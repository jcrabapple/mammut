package org.joinmastodon.android.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.drafts.ComposeDraft;
import org.joinmastodon.android.fragments.ComposeFragment.PublishKeys;
import org.joinmastodon.android.fragments.ComposeFragment.PublishKeys.KeyState;
import org.joinmastodon.android.fragments.ComposeFragment.PublishKeys.SegmentPayload;
import org.junit.Test;

import java.util.Arrays;

/**
 * Regression tests for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3). These pin the pure publish-identity seams behind R1 F1/F2/F5; the
 * fragment delegates to exactly these seams because a plain JVM cannot
 * construct a retained Fragment.
 *
 * <p>F1: reply/quote/thread-anchor ids survive process death — a resumed
 * reply chains below its anchor, a partial tail keeps its key.
 * F2: an idempotency key is reused only for an identical retry; any
 * text/CW/poll/media/anchor/visibility/language/quote/order change mints a
 * fresh key.
 * F5: a resumed draft publishes with its stored quote id when the in-memory
 * Status is gone.</p>
 */
public class ComposePublishKeysTest{

	private static SegmentPayload payload(String text){
		SegmentPayload p=new SegmentPayload();
		p.text=text;
		p.visibility="PUBLIC";
		p.mediaIds.addAll(Arrays.asList("m1", "m2"));
		p.pollOptions.addAll(Arrays.asList("yes", "no"));
		return p;
	}

	// --- R1 F1: anchor priority across process death ---

	@Test
	public void threadAnchorWinsWhileChainIsPartiallyOut(){
		assertEquals("anchor9", PublishKeys.anchorId("anchor9", "reply1", "restored7"));
	}

	@Test
	public void liveReplyTargetWinsOverRestoredId(){
		assertEquals("reply1", PublishKeys.anchorId(null, "reply1", "restored7"));
	}

	@Test
	public void restoredIdCarriesTheAnchorWhenObjectsAreGone(){
		// Process death: no threadAnchor, no in-memory replyTo — the id
		// restored from the draft/saved state is the anchor.
		assertEquals("restored7", PublishKeys.anchorId(null, null, "restored7"));
	}

	@Test
	public void noAnchorWithoutAnySource(){
		assertNull(PublishKeys.anchorId(null, null, null));
	}

	// --- R1 F5: resumed quote id rides the request ---

	@Test
	public void visibleQuoteWinsOverRestoredId(){
		assertEquals("quoteLive", PublishKeys.quoteIdForRequest("quoteLive", "quoteRestored"));
	}

	@Test
	public void restoredQuoteIdIsRetainedForPublish(){
		assertEquals("quoteRestored", PublishKeys.quoteIdForRequest(null, "quoteRestored"));
	}

	@Test
	public void noQuoteIdWithoutAnySource(){
		assertNull(PublishKeys.quoteIdForRequest(null, null));
	}

	// --- R1 F2: payload fingerprinting ---

	@Test
	public void identicalPayloadsFingerprintEqually(){
		assertEquals(PublishKeys.fingerprint(payload("hello")), PublishKeys.fingerprint(payload("hello")));
	}

	@Test
	public void everyPayloadDimensionChangesTheFingerprint(){
		String base=PublishKeys.fingerprint(payload("hello"));
		SegmentPayload p;
		p=payload("edited"); assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.spoilerText="cw"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.sensitive=true; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.visibility="PRIVATE"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.language="de"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.quotePolicy="FOLLOWERS"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.replyAnchorId="anchor1"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.quotedStatusId="quote1"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.scheduledAt="2030-01-01T00:00:00Z"; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.pollExpiresIn=3600; assertNotEquals(base, PublishKeys.fingerprint(p));
		p=payload("hello"); p.pollMultiple=true; assertNotEquals(base, PublishKeys.fingerprint(p));
	}

	@Test
	public void mediaOrderChangeMintsADifferentFingerprint(){
		SegmentPayload a=payload("hello");
		SegmentPayload b=payload("hello");
		b.mediaIds.clear();
		b.mediaIds.addAll(Arrays.asList("m2", "m1"));
		assertNotEquals(PublishKeys.fingerprint(a), PublishKeys.fingerprint(b));
	}

	@Test
	public void pollOptionOrderChangeMintsADifferentFingerprint(){
		SegmentPayload a=payload("hello");
		SegmentPayload b=payload("hello");
		b.pollOptions.clear();
		b.pollOptions.addAll(Arrays.asList("no", "yes"));
		assertNotEquals(PublishKeys.fingerprint(a), PublishKeys.fingerprint(b));
	}

	// --- R1 F2: the key policy ---

	@Test
	public void freshComposerMintsOnceAndReusesForIdenticalRetry(){
		KeyState state=new KeyState();
		assertEquals("k1", state.resolve(PublishKeys.fingerprint(payload("hello")), "k1"));
		assertEquals("k1", state.resolve(PublishKeys.fingerprint(payload("hello")), "k2"));
	}

	@Test
	public void editedPayloadUsesAFreshKey(){
		KeyState state=new KeyState();
		state.resolve(PublishKeys.fingerprint(payload("hello")), "k1");
		assertEquals("k2", state.resolve(PublishKeys.fingerprint(payload("edited")), "k2"));
		// And a third distinct payload mints a third key.
		assertEquals("k3", state.resolve(PublishKeys.fingerprint(payload("edited again")), "k3"));
	}

	@Test
	public void reorderedMediaUsesAFreshKey(){
		KeyState state=new KeyState();
		state.resolve(PublishKeys.fingerprint(payload("hello")), "k1");
		SegmentPayload reordered=payload("hello");
		reordered.mediaIds.clear();
		reordered.mediaIds.addAll(Arrays.asList("m2", "m1"));
		assertEquals("k2", state.resolve(PublishKeys.fingerprint(reordered), "k2"));
	}

	@Test
	public void resumedDraftKeepsPersistedKeyForIdenticalPayload(){
		// Resume from the drafts store: key persisted, payload unknown.
		KeyState state=KeyState.restored("persisted", null);
		assertEquals("persisted", state.resolve(PublishKeys.fingerprint(payload("hello")), "mint"));
		assertEquals("persisted", state.resolve(PublishKeys.fingerprint(payload("hello")), "mint2"));
	}

	@Test
	public void resumedDraftMintsFreshKeyOncePayloadDiffersFromFirstPublish(){
		KeyState state=KeyState.restored("persisted", null);
		state.resolve(PublishKeys.fingerprint(payload("hello")), "mint");
		assertEquals("mint2", state.resolve(PublishKeys.fingerprint(payload("edited")), "mint2"));
	}

	@Test
	public void processDeathRetryKeepsKeyForIdenticalPayload(){
		// A publish was attempted (key bound to the payload), the process
		// died mid-request, the activity restored key+fingerprint: the
		// retry must replay the SAME key.
		KeyState first=new KeyState();
		first.resolve(PublishKeys.fingerprint(payload("hello")), "k1");
		KeyState restored=KeyState.restored(first.key, first.fingerprint);
		assertEquals("k1", restored.resolve(PublishKeys.fingerprint(payload("hello")), "mint"));
	}

	@Test
	public void processDeathRetryWithEditedPayloadMintsFreshKey(){
		KeyState first=new KeyState();
		first.resolve(PublishKeys.fingerprint(payload("hello")), "k1");
		KeyState restored=KeyState.restored(first.key, first.fingerprint);
		assertEquals("mint", restored.resolve(PublishKeys.fingerprint(payload("edited")), "mint"));
	}

	@Test
	public void autosaveEnsureKeyNeverBindsAPayload(){
		KeyState state=new KeyState();
		assertEquals("k1", state.ensureKey("k1"));
		// The first publish still binds: same key, fingerprint recorded.
		assertEquals("k1", state.resolve(PublishKeys.fingerprint(payload("hello")), "k2"));
		assertEquals("k1", state.resolve(PublishKeys.fingerprint(payload("hello")), "k3"));
	}

	@Test
	public void tailDraftResumeAfterPartialPublishKeepsKeys(){
		// The tail draft carries the failed segment's key; resuming it and
		// republishing unchanged must replay that key (review M3 chain).
		KeyState tail=KeyState.restored("tailKey", null);
		assertEquals("tailKey", tail.resolve(PublishKeys.fingerprint(payload("tail segment")), "mint"));
		assertEquals("tailKey", tail.resolve(PublishKeys.fingerprint(payload("tail segment")), "mint2"));
	}

	// --- fingerprint persistence in the draft (R1 F2 round trip) ---

	@Test
	public void fingerprintAtToleratesOldDrafts(){
		ComposeDraft draft=new ComposeDraft();
		assertNull(PublishKeys.fingerprintAt(draft, 0));
		draft.segmentFingerprints.add("fp0");
		assertEquals("fp0", PublishKeys.fingerprintAt(draft, 0));
		assertNull(PublishKeys.fingerprintAt(draft, 1));
		draft.segmentFingerprints=null;
		assertNull(PublishKeys.fingerprintAt(draft, 0));
	}

	@Test
	public void draftRoundTripKeepsBindingAcrossResume(){
		// saveDraft wrote key + bound fingerprint; the resumed draft seeds
		// from both, so an edited payload mints a fresh key even though the
		// composer was rebuilt from the store.
		ComposeDraft draft=new ComposeDraft();
		draft.segmentFingerprints.add("fpA");
		KeyState resumed=KeyState.restored("persisted", PublishKeys.fingerprintAt(draft, 0));
		assertEquals("fresh", resumed.resolve("fpB", "fresh"));
	}

	// --- R1 F6: language + quote policy persist in the draft ---

	@Test
	public void draftFieldsSurviveJsonRoundTrip(){
		// The DraftStore serializes with Gson; the new fields must ride
		// along so a resumed draft republishes with the author's choices.
		ComposeDraft d=new ComposeDraft();
		d.language="de";
		d.quotePolicy="FOLLOWERS";
		d.segmentFingerprints.add("fp0");
		ComposeDraft back=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(d), ComposeDraft.class);
		assertEquals("de", back.language);
		assertEquals("FOLLOWERS", back.quotePolicy);
		assertEquals(1, back.segmentFingerprints.size());
		assertEquals("fp0", back.segmentFingerprints.get(0));
	}

	@Test
	public void olderDraftsWithoutTheNewFieldsLoadCleanly(){
		// Forward compatibility: a draft written before F6/F2 must not
		// break resume — everything falls back to "unset".
		ComposeDraft back=new com.google.gson.Gson().fromJson("{\"id\":\"x\",\"segments\":[]}", ComposeDraft.class);
		assertNull(back.language);
		assertNull(back.quotePolicy);
		assertTrue(back.segmentFingerprints.isEmpty());
	}

	// --- Wave 3 Q-C10: thread segment key bound to actual inReplyToId ---

	@Test
	public void threadSegmentKeyBoundToActualReplyAnchorReplaysOnTailRetry(){
		// Segment 1's key is resolved in publishNextSegment() with the real
		// posted segment 0 ID as replyAnchorId, not null before segment 0 posts.
		SegmentPayload seg1=payload("second post in thread");
		seg1.replyAnchorId="postedSeg0";
		KeyState seg1State=new KeyState();
		String firstKey=seg1State.resolve(PublishKeys.fingerprint(seg1), "k-seg1");
		assertEquals("k-seg1", firstKey);

		// When segment 1 fails and is promoted to segment 0 with threadAnchor="postedSeg0",
		// retrying with the same payload replays the exact same idempotency key.
		KeyState promoted=KeyState.restored(seg1State.key, seg1State.fingerprint);
		SegmentPayload retrySeg0=payload("second post in thread");
		retrySeg0.replyAnchorId=PublishKeys.anchorId("postedSeg0", null, null);
		assertEquals("k-seg1", promoted.resolve(PublishKeys.fingerprint(retrySeg0), "k-new"));

		// Whereas binding with null anchor before segment 0 existed would have mismatched.
		SegmentPayload staleUnbound=payload("second post in thread");
		staleUnbound.replyAnchorId=null;
		assertNotEquals(PublishKeys.fingerprint(staleUnbound), PublishKeys.fingerprint(retrySeg0));
	}
}
