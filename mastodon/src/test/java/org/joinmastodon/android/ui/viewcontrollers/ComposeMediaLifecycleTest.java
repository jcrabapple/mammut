package org.joinmastodon.android.ui.viewcontrollers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState;
import org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.ResumePlan;
import org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.UploadErrorDecision;
import org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.UploadSuccessDecision;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState.DONE;
import static org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState.ERROR;
import static org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState.PROCESSING;
import static org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState.QUEUED;
import static org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController.AttachmentUploadState.UPLOADING;

/**
 * Regression tests for Fix 2 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 2). These pin the pure decision seams behind R2 F4/F5/F6/F7; the
 * Android-bound callbacks in ComposeMediaViewController delegate to exactly
 * these seams, because a plain JVM cannot construct a retained Fragment or
 * fake a ContentResolver.
 *
 * <p>F4: while the retained composer is detached, upload callbacks only touch
 * model state — no view access (the audit NPE was getActivity() at the old
 * :360), no queue advancement, no polling — and resumePendingUploadWork()
 * drives exactly one queued upload from the reattachment hook.
 * F5: the MIME stored when the attachment was added decides the resize cap; a
 * file:// draft URI returns null from a fresh resolver query.
 * F6: parcel restore maps in-flight states to requeued/retryable ones.
 * F7: HEIC/HEIF preflight is evaluated by the effective output MIME.</p>
 */
public class ComposeMediaLifecycleTest{

	private static List<AttachmentUploadState> states(AttachmentUploadState... states){
		return new ArrayList<>(Arrays.asList(states));
	}

	// --- R2 F4: detach between response and callback ---

	@Test
	public void detachedSuccessWithTwoAttachmentsDefersThenResumesExactlyOneUpload(){
		// Two attachments: the first is UPLOADING, the second QUEUED. The upload
		// response arrives while the retained composer is detached.
		List<AttachmentUploadState> states=states(UPLOADING, QUEUED);

		UploadSuccessDecision decision=ComposeMediaViewController.planUploadSuccess(true, false, true);
		assertEquals(UploadSuccessDecision.FINISH_DEFERRED, decision);
		// Deferred = model state only: DONE without touching views or advancing.
		states.set(0, DONE);

		// Nothing may run while still detached — no advance, no polling.
		ResumePlan detached=ComposeMediaViewController.planResume(states, false);
		assertFalse(detached.startNextUpload);
		assertEquals(0, detached.pollsToRestart);

		// Reattach: the resume hook starts exactly one queued upload...
		ResumePlan resumed=ComposeMediaViewController.planResume(states, true);
		assertTrue(resumed.startNextUpload);
		assertEquals(0, resumed.pollsToRestart);
		states.set(1, UPLOADING);

		// ...and exactly one: a second walk with an UPLOADING present starts none.
		assertFalse(ComposeMediaViewController.planResume(states, true).startNextUpload);
	}

	@Test
	public void attachedSuccessUsesTheImmediatePaths(){
		assertEquals(UploadSuccessDecision.FINISH, ComposeMediaViewController.planUploadSuccess(true, true, true));
		assertEquals(UploadSuccessDecision.BEGIN_PROCESSING, ComposeMediaViewController.planUploadSuccess(true, true, false));
	}

	@Test
	public void detachedProcessingCallbackDefersPollingUntilReattach(){
		// Server accepted the bytes but the media is still transcoding: url empty.
		assertEquals(UploadSuccessDecision.BEGIN_PROCESSING_DEFERRED,
				ComposeMediaViewController.planUploadSuccess(true, false, false));

		// While detached nothing polls; on reattach the hook restarts polling for
		// the PROCESSING attachment and still advances the queue behind it.
		List<AttachmentUploadState> states=states(PROCESSING, QUEUED);
		ResumePlan detached=ComposeMediaViewController.planResume(states, false);
		assertEquals(0, detached.pollsToRestart);
		assertFalse(detached.startNextUpload);

		ResumePlan resumed=ComposeMediaViewController.planResume(states, true);
		assertEquals(1, resumed.pollsToRestart);
		assertTrue(resumed.startNextUpload);
	}

	@Test
	public void staleCallbacksOnRemovedAttachmentsAreIgnored(){
		// The attachment was removed while the request was in flight: the
		// callback must not touch views, state or the queue.
		assertEquals(UploadSuccessDecision.IGNORE, ComposeMediaViewController.planUploadSuccess(false, true, true));
		assertEquals(UploadSuccessDecision.IGNORE, ComposeMediaViewController.planUploadSuccess(false, false, false));
		assertEquals(UploadErrorDecision.IGNORE, ComposeMediaViewController.planUploadError(false, true));
		assertEquals(UploadErrorDecision.IGNORE, ComposeMediaViewController.planUploadError(false, false));
	}

	@Test
	public void detachedErrorCallbackDefersTheErrorTile(){
		assertEquals(UploadErrorDecision.APPLY_DEFERRED, ComposeMediaViewController.planUploadError(true, false));
		assertEquals(UploadErrorDecision.APPLY, ComposeMediaViewController.planUploadError(true, true));
		// A deferred error is retryable on reattach: ERROR feeds the retry path,
		// and the resume plan never starts an upload from it by itself.
		List<AttachmentUploadState> states=states(ERROR, DONE);
		assertFalse(ComposeMediaViewController.planResume(states, true).startNextUpload);
	}

	// --- R2 F5: stored MIME decides the resize cap ---

	@Test
	public void largeJpegDraftFromFileUriReachesTheMatrixLimit(){
		// The 1.3.5 F5 failure: a fresh resolver query on a file:// draft URI
		// returns null, maxSize collapsed to 0, and the oversized original was
		// sent. The stored MIME must decide instead.
		String effective=ComposeMediaViewController.effectiveUploadMimeType("image/jpeg", null);
		assertEquals("image/jpeg", effective);
		assertEquals(8_294_400, ComposeMediaViewController.maxImagePixelsForUpload(effective, 8_294_400));
	}

	@Test
	public void resolverQueryIsOnlyAFallbackAndVideoIsNotCapped(){
		assertEquals("image/png", ComposeMediaViewController.effectiveUploadMimeType(null, "image/png"));
		assertNull(ComposeMediaViewController.effectiveUploadMimeType(null, null));
		// No instance matrix limit: the historical default still applies.
		assertEquals(2_073_600, ComposeMediaViewController.maxImagePixelsForUpload("image/png", 0));
		// Video/audio are not image-capped.
		assertEquals(0, ComposeMediaViewController.maxImagePixelsForUpload("video/mp4", 8_294_400));
		assertEquals(0, ComposeMediaViewController.maxImagePixelsForUpload(null, 8_294_400));
	}

	// --- R2 F6: parcel round-trip of in-flight states ---

	@Test
	public void parcelRestoreResumesOrMakesRetryable(){
		// UPLOADING rejoins the queue; PROCESSING becomes a retryable ERROR. No
		// restored state can block publish on a request that died with the process.
		assertEquals(QUEUED, ComposeMediaViewController.restoredUploadState(UPLOADING));
		assertEquals(ERROR, ComposeMediaViewController.restoredUploadState(PROCESSING));
		assertEquals(QUEUED, ComposeMediaViewController.restoredUploadState(QUEUED));
		assertEquals(ERROR, ComposeMediaViewController.restoredUploadState(ERROR));
		assertEquals(DONE, ComposeMediaViewController.restoredUploadState(DONE));

		// Round-trip invariant: after restore, every state is either driven by the
		// resume hook (QUEUED) or retryable by the author (ERROR/DONE).
		for(AttachmentUploadState saved:AttachmentUploadState.values()){
			AttachmentUploadState restored=ComposeMediaViewController.restoredUploadState(saved);
			assertTrue(restored+" must not stay in-flight", restored!=UPLOADING && restored!=PROCESSING);
		}
		// A requeued upload resumes through exactly the same one-at-a-time plan.
		ResumePlan plan=ComposeMediaViewController.planResume(states(QUEUED, QUEUED), true);
		assertTrue(plan.startNextUpload);
	}

	// --- R2 F7: HEIC/HEIF preflight by effective output MIME ---

	@Test
	public void heicAcceptedOnJpegOnlyAllowlist(){
		List<String> allowlist=Arrays.asList("image/jpeg", "image/png");
		assertTrue(allowlist.contains(ComposeMediaViewController.preflightMimeType("image/heic")));
		assertTrue(allowlist.contains(ComposeMediaViewController.preflightMimeType("image/heif")));
		// Untouched types pass through verbatim.
		assertEquals("image/png", ComposeMediaViewController.preflightMimeType("image/png"));
		assertEquals("image/gif", ComposeMediaViewController.preflightMimeType("image/gif"));
		assertNull(ComposeMediaViewController.preflightMimeType(null));
	}

	@Test
	public void heicPreflightIsCaseAndLocaleSafe(){
		// Turkish default-locale lowercasing turns "IMAGE/HEIC" into "ıMAGE/HEIC"
		// and would fail the preflight map, rejecting a file the transcoder
		// accepts. Locale.ROOT must be used.
		Locale previous=Locale.getDefault();
		Locale.setDefault(new Locale("tr", "TR"));
		try{
			assertEquals("image/jpeg", ComposeMediaViewController.preflightMimeType("IMAGE/HEIC"));
			assertEquals("image/jpeg", ComposeMediaViewController.preflightMimeType("Image/Heif"));
		}finally{
			Locale.setDefault(previous);
		}
	}

	// --- Wave 3 regressions: image predicate & draftFilePath parceling ---

	@Test
	public void isImageAttachmentDistinguishesImagesFromVideoAndAudio(){
		assertTrue(ComposeMediaViewController.isImageAttachment("image/jpeg", null));
		assertTrue(ComposeMediaViewController.isImageAttachment("IMAGE/PNG", null));
		assertFalse(ComposeMediaViewController.isImageAttachment("video/mp4", null));
		assertFalse(ComposeMediaViewController.isImageAttachment("audio/mpeg", null));
		assertTrue(ComposeMediaViewController.isImageAttachment(null, org.joinmastodon.android.model.Attachment.Type.IMAGE));
		assertFalse(ComposeMediaViewController.isImageAttachment(null, org.joinmastodon.android.model.Attachment.Type.VIDEO));
		assertFalse(ComposeMediaViewController.isImageAttachment(null, org.joinmastodon.android.model.Attachment.Type.GIFV));
		assertFalse(ComposeMediaViewController.isImageAttachment(null, org.joinmastodon.android.model.Attachment.Type.AUDIO));
		assertFalse(ComposeMediaViewController.isImageAttachment(null, null));
	}

	@Test
	public void draftFilePathIsNotTransientSoParcelerPreservesIt() throws Exception{
		java.lang.reflect.Field f=ComposeMediaViewController.DraftMediaAttachment.class.getDeclaredField("draftFilePath");
		assertFalse("draftFilePath must survive Parceler save/restore across process death",
				java.lang.reflect.Modifier.isTransient(f.getModifiers()));
	}
}
