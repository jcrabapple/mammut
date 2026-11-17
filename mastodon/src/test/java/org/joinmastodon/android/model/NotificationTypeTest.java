package org.joinmastodon.android.model;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Guards the notification display routing. The grouped-plurals switch in
 * NotificationHeaderStatusDisplayItem only handles the three groupable types and throws on
 * anything else, so canBeGrouped() must agree with getGroupableTypes() for every type. Admin
 * notifications (Mastodon 4.x) must take the single-account header path, not the grouped one.
 */
public class NotificationTypeTest{
	@Test
	public void adminTypesExist(){
		assertEquals(NotificationType.ADMIN_REPORT, NotificationType.valueOf("ADMIN_REPORT"));
		assertEquals(NotificationType.ADMIN_SIGN_UP, NotificationType.valueOf("ADMIN_SIGN_UP"));
	}

	@Test
	public void adminTypesAreNotGroupable(){
		assertFalse(NotificationType.ADMIN_REPORT.canBeGrouped());
		assertFalse(NotificationType.ADMIN_SIGN_UP.canBeGrouped());
	}

	@Test
	public void groupableFlagMatchesGroupableSetForEveryType(){
		assertEquals(EnumSet.of(NotificationType.FAVORITE, NotificationType.REBLOG, NotificationType.FOLLOW),
				NotificationType.getGroupableTypes());
		for(NotificationType t: NotificationType.values()){
			assertEquals("canBeGrouped() disagrees with getGroupableTypes() for "+t,
					t.canBeGrouped(), NotificationType.getGroupableTypes().contains(t));
		}
	}
}