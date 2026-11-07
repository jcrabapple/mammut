package org.joinmastodon.android.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.joinmastodon.android.api.PushSubscriptionManager;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.PushSubscription;
import org.unifiedpush.android.connector.UnifiedPush;

// MAMMUT: port from upstream master
public class UnifiedPushHelper{
	private static final String TAG="UnifiedPushHelper";

	public static boolean isUnifiedPushEnabled(@NonNull Context context){
		return UnifiedPush.getAckDistributor(context)!=null;
	}

	public static boolean hasAnyDistributorInstalled(@NonNull Context context){
		return !UnifiedPush.getDistributors(context).isEmpty();
	}

	public static void registerAllAccounts(@NonNull Context context){
		for(AccountSession accountSession:AccountSessionManager.getInstance().getLoggedInAccounts()){
			String vapidKey=accountSession.app==null ? null : accountSession.app.vapidKey;
			if(vapidKey==null){
				// The server never told us its VAPID key (dead or misconfigured instance). Registering
				// without one still works when the server does not use standard web push.
				Log.w(TAG, "registerAllAccounts: no vapid key for "+accountSession.getFullUsername());
				continue;
			}
			PushSubscription sub=accountSession.pushSubscription;
			if(sub==null || sub.standard){
				vapidKey=vapidKey.replaceAll("=", "");
			}else{
				// Server is known not to support standard VAPID, register without one
				vapidKey=null;
			}
			try{
				UnifiedPush.register(context, accountSession.getID(), accountSession.self.fqn, vapidKey);
			}catch(RuntimeException x){
				// 3.3.x throws on malformed VAPID keys
				Log.e(TAG, "registerAllAccounts: registration failed for "+accountSession.getFullUsername(), x);
			}
		}
	}

	public static void unregisterAllAccounts(@NonNull Context context){
		for(AccountSession accountSession:AccountSessionManager.getInstance().getLoggedInAccounts()){
			UnifiedPush.unregister(context, accountSession.getID());
			// use FCM again — but only when this build actually has an FCM token, otherwise
			// there is nothing to fall back to
			if(PushSubscriptionManager.arePushNotificationsAvailable())
				accountSession.getPushSubscriptionManager().registerAccountForPush(null);
		}
	}
}
