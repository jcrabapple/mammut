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

	/**
	 * MAMMUT: reflects the user's choice, not the round trip. The connector only reports an
	 * acknowledged distributor after the chosen one answers over IPC, so keying the switch off
	 * that made it spring back to off before registration had a chance to complete.
	 */
	public static boolean isUnifiedPushEnabled(@NonNull Context context){
		return UnifiedPush.getSavedDistributor(context)!=null;
	}

	public static boolean isRegistrationAcknowledged(@NonNull Context context){
		return UnifiedPush.getAckDistributor(context)!=null;
	}

	public static String getDistributorLabel(@NonNull Context context, String packageName){
		try{
			return context.getPackageManager().getApplicationLabel(
					context.getPackageManager().getApplicationInfo(packageName, 0)).toString();
		}catch(Exception x){
			return packageName;
		}
	}

	public static boolean hasAnyDistributorInstalled(@NonNull Context context){
		return !UnifiedPush.getDistributors(context).isEmpty();
	}

	public static void registerAllAccounts(@NonNull Context context){
		boolean registeredAny=false, missingVapid=false;
		for(AccountSession accountSession:AccountSessionManager.getInstance().getLoggedInAccounts()){
			String vapidKey=accountSession.app==null ? null : accountSession.app.vapidKey;
			if(vapidKey==null){
				// The server never told us its VAPID key (dead or misconfigured instance). Registering
				// without one still works when the server does not use standard web push.
				Log.w(TAG, "registerAllAccounts: no vapid key for "+accountSession.getFullUsername());
				missingVapid=true;
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
				registeredAny=true;
			}catch(RuntimeException x){
				// 3.3.x throws on malformed VAPID keys
				Log.e(TAG, "registerAllAccounts: registration failed for "+accountSession.getFullUsername(), x);
			}
		}
		if(!registeredAny){
			android.widget.Toast.makeText(context, missingVapid ? org.joinmastodon.android.R.string.mo_up_no_vapid : org.joinmastodon.android.R.string.mo_up_failed, android.widget.Toast.LENGTH_LONG).show();
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
		// Drop the stored choice too, otherwise isUnifiedPushEnabled() keeps reporting true and
		// the switch comes back on by itself the next time the screen opens.
		try{
			UnifiedPush.removeDistributor(context);
		}catch(RuntimeException x){
			Log.w(TAG, "unregisterAllAccounts: could not clear the saved distributor", x);
		}
	}
}
