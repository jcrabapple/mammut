package org.joinmastodon.android.fragments.settings;

import org.joinmastodon.android.R;
import org.joinmastodon.android.model.AccountWarning;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.NotificationType;
import org.joinmastodon.android.model.PushNotification;
import org.joinmastodon.android.ui.displayitems.NotificationHeaderStatusDisplayItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Wave 1 notification fixes:
 * - null NotificationType (unknown V1 server types) never throws NPE in header switches
 * - moderationWarning with null action never throws NPE in PushNotification.Fallback
 * - SettingsNotificationsFragment syncs push settings on UnifiedPush-only setups as well as FCM
 */
public class NotificationWaveOneTest{

	@Test
	public void nullNotificationTypeFallsBackSafelyInHeaderHelpers(){
		assertEquals(R.string.mo_notification_fallback, NotificationHeaderStatusDisplayItem.singleHeaderTextRes(null));
		assertEquals(R.drawable.ic_notifications_wght700fill1_20px, NotificationHeaderStatusDisplayItem.iconRes(null, false));
		assertEquals(R.drawable.ic_notifications_wght700fill1_20px, NotificationHeaderStatusDisplayItem.iconRes(null, true));
		assertEquals(R.attr.colorM3Outline, NotificationHeaderStatusDisplayItem.iconTintAttr(null));
	}

	@Test
	public void everyKnownNotificationTypeMapsWithoutThrowing(){
		for(NotificationType type:NotificationType.values()){
			assertNotEquals(0, NotificationHeaderStatusDisplayItem.singleHeaderTextRes(type));
			assertNotEquals(0, NotificationHeaderStatusDisplayItem.iconRes(type, false));
			assertNotEquals(0, NotificationHeaderStatusDisplayItem.iconRes(type, true));
			assertNotEquals(0, NotificationHeaderStatusDisplayItem.iconTintAttr(type));
		}
	}

	@Test
	public void moderationWarningWithNullActionDoesNotCrashFallback(){
		Notification n=new Notification();
		n.id="999";
		n.type=NotificationType.MODERATION_WARNING;
		n.moderationWarning=new AccountWarning();
		n.moderationWarning.action=null;

		PushNotification pn=PushNotification.Fallback.build(res->"str_"+res, n, s->"");
		assertEquals("999", pn.notificationId);
		assertEquals("", pn.body);
	}

	@Test
	public void pushSyncRunsForFcmOrUnifiedPushOnly(){
		assertFalse(SettingsNotificationsFragment.shouldSyncPushSettings(false, false));
		assertTrue(SettingsNotificationsFragment.shouldSyncPushSettings(true, false));
		assertTrue(SettingsNotificationsFragment.shouldSyncPushSettings(false, true));
		assertTrue(SettingsNotificationsFragment.shouldSyncPushSettings(true, true));
	}
}
