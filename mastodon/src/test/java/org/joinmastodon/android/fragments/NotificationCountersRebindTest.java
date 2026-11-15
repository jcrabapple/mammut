package org.joinmastodon.android.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.events.StatusCountersUpdatedEvent;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.viewmodel.NotificationViewModel;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression test for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4),
 * R3 F4: failed favorite/boost/bookmark requests post StatusCountersUpdatedEvent after
 * restoring the model; the notifications list must apply the counters and rebind the
 * matching footers (previously the optimistic tint/count survived until an unrelated
 * rebind). Pins both the pure apply logic and the @Subscribe wiring.
 */
public class NotificationCountersRebindTest{

	private static NotificationViewModel row(String groupKey, String statusId){
		NotificationViewModel nvm=new NotificationViewModel();
		nvm.notification=new org.joinmastodon.android.model.NotificationGroup();
		nvm.notification.groupKey=groupKey;
		nvm.status=Status.ofFake(statusId, "text of "+statusId, Instant.now());
		nvm.accounts=List.of();
		return nvm;
	}

	@Test
	public void failedFavoriteRollsBackEveryMatchingRow(){
		List<NotificationViewModel> data=new ArrayList<>();
		data.add(row("n1", "100"));
		data.add(row("n2", "200"));
		data.add(row("n3", "100")); // the same status boosted by someone else
		List<NotificationViewModel> preloaded=new ArrayList<>();
		preloaded.add(row("p1", "100"));

		Status restored=Status.ofFake("100", "text of 100", Instant.now());
		restored.favouritesCount=7;
		restored.favourited=false;
		List<NotificationViewModel> affected=BaseNotificationsListFragment.CounterRebind.apply(data, preloaded, new StatusCountersUpdatedEvent(restored, StatusCountersUpdatedEvent.CounterType.FAVORITES));

		assertEquals(2, affected.size());
		assertTrue(affected.contains(data.get(0)));
		assertTrue(affected.contains(data.get(2)));
		assertFalse(affected.contains(data.get(1)));
		for(NotificationViewModel nvm:affected){
			assertEquals(7, nvm.status.favouritesCount);
			assertFalse(nvm.status.favourited);
		}
		// preloaded rows are updated but not rebound
		assertEquals(7, preloaded.get(0).status.favouritesCount);
		// non-matching rows are untouched
		assertEquals(0, data.get(1).status.favouritesCount);
	}

	@Test
	public void counterEventHandlerIsSubscribed() throws Exception{
		boolean found=false;
		for(Class<?> cls:BaseNotificationsListFragment.class.getDeclaredClasses()){
			if(!cls.getSimpleName().equals("EventListener"))
				continue;
			for(Method m:cls.getMethods()){
				if(m.getName().equals("onStatusCountersUpdated") && m.getParameterTypes().length==1
						&& m.getParameterTypes()[0]==StatusCountersUpdatedEvent.class){
					assertNotNull("handler lost its @Subscribe annotation", m.getAnnotation(Subscribe.class));
					found=true;
				}
			}
		}
		assertTrue("BaseNotificationsListFragment does not handle StatusCountersUpdatedEvent", found);
	}
}
