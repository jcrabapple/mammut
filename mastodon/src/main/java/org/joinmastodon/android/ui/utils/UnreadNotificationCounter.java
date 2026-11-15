package org.joinmastodon.android.ui.utils;

import org.joinmastodon.android.api.requests.notifications.GetNotificationsV1;
import org.joinmastodon.android.api.requests.notifications.GetUnreadNotificationsCount;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.NotificationType;
import org.joinmastodon.android.utils.ObjectIdComparator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;

/**
 * MAMMUT: loads the unread-notifications count through the same source the old
 * notification badge used (Mastodon 4.4 marker API when available, v1 fallback).
 * Shared by HomeFragment and ThreadFragment so the navigation FAB badge is
 * consistent on both surfaces.
 */
public class UnreadNotificationCounter{
	public interface CountCallback{
		void onUnreadCount(int count, boolean more);
	}

	private UnreadNotificationCounter(){}

	public static void load(String accountID, CountCallback callback){
		Instance instance=AccountSessionManager.get(accountID).getInstanceInfo();
		if(instance==null)
			return;
		if(instance.getApiVersion()>=2){
			new GetUnreadNotificationsCount(EnumSet.allOf(NotificationType.class), NotificationType.getGroupableTypes())
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(GetUnreadNotificationsCount.Response result){
							callback.onUnreadCount(result.count, false);
						}

						@Override
						public void onError(ErrorResponse error){}
					})
					.exec(accountID);
		}else{
			List<Notification>[] notifications=new List[]{null};
			String[] marker={null};
			boolean[] markerReady={false};
			AccountSessionManager.get(accountID).reloadNotificationsMarker(m->{
				marker[0]=m;
				markerReady[0]=true;
				if(notifications[0]!=null)
					updateUnreadCountV1(accountID, notifications[0], marker[0], callback);
			});

			new GetNotificationsV1(null, 40, EnumSet.allOf(NotificationType.class))
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(List<Notification> result){
							notifications[0]=result;
							if(markerReady[0])
								updateUnreadCountV1(accountID, notifications[0], marker[0], callback);
						}

						@Override
						public void onError(ErrorResponse error){}
					}).exec(accountID);
		}
	}

	private static void updateUnreadCountV1(String accountID, List<Notification> notifications, String marker, CountCallback callback){
		List<String> ids=new ArrayList<>(notifications.size());
		for(Notification n:notifications)
			ids.add(n.id);
		int[] result=V1Count.compute(ids, marker);
		callback.onUnreadCount(result[0], result[1]==1);
	}

	/**
	 * MAMMUT: audit R3 F8 — pure V1 unread counting. Counts every id newer than the
	 * marker ({@code compare(id, marker) > 0}); the marker object itself does not have
	 * to be in the page, so a deleted or filtered marker notification no longer makes
	 * the whole page count as read. The list must be in reverse-chronological order.
	 */
	public static class V1Count{
		/** Returns {count, more-as-1}. */
		public static int[] compute(List<String> idsDescending, String marker){
			if(idsDescending.isEmpty() || ObjectIdComparator.INSTANCE.compare(idsDescending.get(0), marker)<=0)
				return new int[]{0, 0};
			if(ObjectIdComparator.INSTANCE.compare(idsDescending.get(idsDescending.size()-1), marker)>0)
				return new int[]{idsDescending.size(), 1};
			int count=0;
			for(String id:idsDescending){
				if(ObjectIdComparator.INSTANCE.compare(id, marker)<=0)
					break;
				count++;
			}
			return new int[]{count, 0};
		}
	}
}
