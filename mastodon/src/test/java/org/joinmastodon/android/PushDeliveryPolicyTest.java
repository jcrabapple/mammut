package org.joinmastodon.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.model.StatusPrivacy;
import org.junit.Test;

/**
 * Regression tests for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4):
 * R3 F12 (push boost-action eligibility matches the in-app footer — private mentions
 * from other users never get a Boost action), R3 F6 (the UnifiedPush pause gate drops
 * delivery while paused and resumes at expiry), and S1.7 (push endpoint capability
 * URLs are redacted to scheme://host in logs).
 */
public class PushDeliveryPolicyTest{

	// R3 F12

	@Test
	public void boostEligibilityMatchesFooter(){
		// public/unlisted/local posts: boostable by anyone
		assertTrue(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.PUBLIC, false));
		assertTrue(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.UNLISTED, false));
		assertTrue(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.LOCAL, false));
		// own private post: boostable (matches the footer ownership exception)
		assertTrue(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.PRIVATE, true));
		// another user's private mention: bookmark, never boost
		assertFalse(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.PRIVATE, false));
		// direct posts: never boost
		assertFalse(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.DIRECT, true));
		assertFalse(PushNotificationReceiver.shouldSwapBookmarkForBoost(true, StatusPrivacy.DIRECT, false));
		// swap preference off: always bookmark
		assertFalse(PushNotificationReceiver.shouldSwapBookmarkForBoost(false, StatusPrivacy.PUBLIC, false));
	}

	// R3 F6

	@Test
	public void pauseGateBlocksUntilExpiry(){
		long now=1_000_000L;
		assertTrue(UnifiedPushNotificationReceiver.DeliveryGate.shouldDrop(now+60_000, now));
		assertFalse(UnifiedPushNotificationReceiver.DeliveryGate.shouldDrop(now-1, now));
		assertFalse("expiry restores delivery", UnifiedPushNotificationReceiver.DeliveryGate.shouldDrop(now, now));
		assertFalse(UnifiedPushNotificationReceiver.DeliveryGate.shouldDrop(0, now));
	}

	// S1.7

	@Test
	public void endpointRedactionKeepsOnlySchemeAndHost(){
		String redacted=org.joinmastodon.android.utils.UnifiedPushHelper.redactEndpoint("https://ntfy.example/upABCDEF123456?token=SECRET");
		assertEquals("https://ntfy.example", redacted);
		assertFalse(redacted.contains("SECRET"));
		assertFalse(redacted.contains("upABCDEF"));
	}

	@Test
	public void endpointRedactionHandlesDegenerateInput(){
		assertEquals("null", org.joinmastodon.android.utils.UnifiedPushHelper.redactEndpoint(null));
		assertEquals("[unparseable endpoint]", org.joinmastodon.android.utils.UnifiedPushHelper.redactEndpoint("not a url"));
	}
}
