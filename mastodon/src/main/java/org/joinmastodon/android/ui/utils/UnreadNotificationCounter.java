package org.joinmastodon.android.ui.utils;

import org.joinmastodon.android.api.requests.notifications.GetNotificationsV1;
import org.joinmastodon.android.api.requests.notifications.GetUnreadNotificationsCount;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.NotificationType;
import org.joinmastodon.android.utils.ObjectIdComparator;

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
			AccountSessionManager.get(accountID).reloadNotificationsMarker(m->{
				marker[0]=m;
				if(notifications[0]!=null)
					updateUnreadCountV1(accountID, notifications[0], marker[0], callback);
			});

			new GetNotificationsV1(null, 40, EnumSet.allOf(NotificationType.class))
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(List<Notification> result){
							notifications[0]=result;
							if(marker[0]!=null)
								updateUnreadCountV1(accountID, notifications[0], marker[0], callback);
						}

						@Override
						public void onError(ErrorResponse error){}
					}).exec(accountID);
		}
	}

	private static void updateUnreadCountV1(String accountID, List<Notification> notifications, String marker, CountCallback callback){
		if(notifications.isEmpty() || ObjectIdComparator.INSTANCE.compare(notifications.get(0).id, marker)<=0){
			callback.onUnreadCount(0, false);
		}else{
			if(ObjectIdComparator.INSTANCE.compare(notifications.get(notifications.size()-1).id, marker)>0){
				callback.onUnreadCount(notifications.size(), true);
			}else{
				int count=0;
				for(Notification n:notifications){
					if(n.id.equals(marker))
						break;
					count++;
				}
				callback.onUnreadCount(count, false);
			}
		}
	}
}
