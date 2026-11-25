package org.joinmastodon.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.ExternalShareActivity.ShareBoundary;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Regression tests for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3, R1 F4 + S1.1): EXTRA_STREAM is the primary multi-share channel and
 * ClipData the fallback, and only content: URIs cross the external boundary —
 * a file:// URI would crash the app's own content-URI media pipeline.
 *
 * <p>The scheme/precedence decisions are pure; the Uri-touching sanitize
 * wrapper is a thin filter over them.</p>
 */
public class ExternalShareBoundaryTest{

	@Test
	public void onlyContentSchemeCrossesTheBoundary(){
		assertTrue(ShareBoundary.acceptsScheme("content"));
		assertFalse(ShareBoundary.acceptsScheme("file"));
		assertFalse(ShareBoundary.acceptsScheme("http"));
		assertFalse(ShareBoundary.acceptsScheme("https"));
		assertFalse(ShareBoundary.acceptsScheme(null));
		assertFalse(ShareBoundary.acceptsScheme(""));
	}

	@Test
	public void extraStreamOnlyMultiShareKeepsItsAttachments(){
		// S1.1: a sender that populates ONLY EXTRA_STREAM (no ClipData)
		// must still deliver every attachment.
		List<String> stream=Arrays.asList("a", "b", "c");
		List<String> result=ShareBoundary.preferExtraStream(stream, null);
		assertSame(stream, result);
		assertEquals(3, result.size());
	}

	@Test
	public void extraStreamWinsOverClipDataWhenBothPresent(){
		List<String> stream=Arrays.asList("s1");
		List<String> clip=Arrays.asList("c1", "c2");
		assertSame(stream, ShareBoundary.preferExtraStream(stream, clip));
	}

	@Test
	public void clipDataIsTheFallbackNotAMerge(){
		// An empty EXTRA_STREAM (present but no items) still falls back.
		List<String> clip=Arrays.asList("c1", "c2");
		assertSame(clip, ShareBoundary.preferExtraStream(null, clip));
		assertSame(clip, ShareBoundary.preferExtraStream(Collections.emptyList(), clip));
	}

	@Test
	public void noChannelsMeansNoAttachments(){
		assertNull(ShareBoundary.preferExtraStream(null, null));
		List<String> empty=Collections.emptyList();
		assertSame(empty, ShareBoundary.preferExtraStream(null, empty));
	}

	@Test
	public void ownAuthoritiesAreRejectedAcrossBoundary(){
		assertFalse(ShareBoundary.acceptsAuthority("app.mammut.android.fileprovider", "app.mammut.android"));
		assertFalse(ShareBoundary.acceptsAuthority("app.mammut.android.self_update_provider", "app.mammut.android"));
		assertFalse(ShareBoundary.acceptsAuthority("app.mammut.android", "app.mammut.android"));
		assertFalse(ShareBoundary.acceptsAuthority(null, "app.mammut.android"));
		assertFalse(ShareBoundary.acceptsAuthority("", "app.mammut.android"));
		assertTrue(ShareBoundary.acceptsAuthority("com.android.providers.media.documents", "app.mammut.android"));
		assertTrue(ShareBoundary.acceptsAuthority("app.mammut.android.debug.other", "app.mammut.android.release"));
	}
}
