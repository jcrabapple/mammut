package org.joinmastodon.android;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.requests.notifications.GetNotificationByID;
import org.joinmastodon.android.api.requests.notifications.GetNotificationsV1;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.PushNotification;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.MessagingReceiver;
import org.unifiedpush.android.connector.data.PublicKeySet;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;

/**
 * MAMMUT: port from upstream master — de-Googled push notifications.
 *
 * Payloads that the distributor could decrypt carry the full PushNotification body and are
 * replayed through the same pipeline as FCM messages. Everything else falls back to fetching the
 * newest notification from the server, because the app cannot decrypt standard web push itself.
 */
public class UnifiedPushNotificationReceiver extends MessagingReceiver{
	private static final String TAG="UnifiedPushNotificationReceiver";

	public UnifiedPushNotificationReceiver(){
		super();
	}

	@Override
	public void onNewEndpoint(@NonNull Context context, @NonNull PushEndpoint endpoint, @NonNull String instance){
		// MAMMUT: audit S1.7 — the endpoint URL is a capability URL; debug-only redacted log
		if(BuildConfig.DEBUG)
			Log.d(TAG, "onNewEndpoint: "+org.joinmastodon.android.utils.UnifiedPushHelper.redactEndpoint(endpoint.getUrl()));
		AccountSession account=AccountSessionManager.getInstance().tryGetAccount(instance);
		if(account==null)
			return;
		PublicKeySet keySet=endpoint.getPubKeySet();
		if(keySet!=null)
			account.getPushSubscriptionManager().registerAccountForPush(account.pushSubscription, true, endpoint.getUrl(), keySet.getPubKey(), keySet.getAuth());
		else
			account.getPushSubscriptionManager().registerAccountForPush(account.pushSubscription, endpoint.getUrl());
	}

	@Override
	public void onRegistrationFailed(@NonNull Context context, @NonNull FailedReason reason, @NonNull String instance){
		if(BuildConfig.DEBUG)
			Log.d(TAG, "onRegistrationFailed ("+reason+")");
		android.widget.Toast.makeText(context, R.string.mo_up_failed, android.widget.Toast.LENGTH_LONG).show();
		fallBackToFcm(instance);
	}

	@Override
	public void onUnregistered(@NonNull Context context, @NonNull String instance){
		if(BuildConfig.DEBUG)
			Log.d(TAG, "onUnregistered");
		fallBackToFcm(instance);
	}

	private void fallBackToFcm(String instance){
		AccountSession account=AccountSessionManager.getInstance().tryGetAccount(instance);
		if(account!=null)
			account.getPushSubscriptionManager().registerAccountForPush(account.pushSubscription);
	}

	@Override
	public void onMessage(@NonNull Context context, @NonNull PushMessage message, @NonNull String instance){
		if(BuildConfig.DEBUG)
			Log.d(TAG, "onMessage received");
		AccountSession account=AccountSessionManager.getInstance().tryGetAccount(instance);
		if(account==null)
			return;
		// MAMMUT: audit R3 F6 — the per-account pause must gate UnifiedPush exactly like FCM:
		// no fetch, no shade notification, no unread increment while it is active
		if(DeliveryGate.shouldDrop(account.getLocalPreferences().getNotificationsPauseEndTime(), System.currentTimeMillis())){
			if(BuildConfig.DEBUG)
				Log.i(TAG, "onMessage: dropping notification because user has paused notifications for this account");
			return;
		}
		if(message.getDecrypted()){
			// MAMMUT: audit R3 F2 — validate like the FCM path; an unknown/future type or a
			// malformed payload must fall back to fetching instead of crashing the receiver
			PushNotification pn=null;
			try{
				pn=MastodonAPIController.gson.fromJson(new String(message.getContent(), StandardCharsets.UTF_8), PushNotification.class);
				if(pn!=null)
					pn.postprocess();
			}catch(Exception x){
				Log.w(TAG, "onMessage: could not parse the decrypted payload", x);
				pn=null;
			}
			if(pn!=null){
				PushNotification validPn=pn;
				try{
					new GetNotificationByID(validPn.notificationId)
							.setCallback(new Callback<>(){
								@Override
								public void onSuccess(Notification result){
									MastodonAPIController.runInBackground(()->new PushNotificationReceiver().notify(context, validPn, instance, result));
								}

								@Override
								public void onError(ErrorResponse error){
									MastodonAPIController.runInBackground(()->new PushNotificationReceiver().notify(context, validPn, instance, null));
								}
							})
							.exec(instance);
					return;
				}catch(IllegalArgumentException x){
					Log.w(TAG, "onMessage: invalid notificationId in decrypted payload, falling back to fetch");
				}
			}else{
				Log.w(TAG, "onMessage: decrypted payload failed validation, falling back to fetch");
			}
		}
		// The app cannot decrypt this payload (or it failed validation), so pull the newest
		// notification instead.
		new GetNotificationsV1(null, 1, null)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(List<Notification> result){
						if(result.isEmpty())
							return;
						Notification notification=result.get(0);
						MastodonAPIController.runInBackground(()->new PushNotificationReceiver().notifyUnifiedPush(context, account, notification));
					}

					@Override
					public void onError(ErrorResponse error){
						if(BuildConfig.DEBUG)
							Log.w(TAG, "onMessage: could not fetch notification");
					}
				})
				.exec(instance);
	}

	/**
	 * MAMMUT: audit R3 F6 — pure pause gate: delivery is dropped while the pause end time
	 * is in the future and resumes the moment it expires.
	 */
	public static class DeliveryGate{
		public static boolean shouldDrop(long pauseEndTimeMillis, long nowMillis){
			return pauseEndTimeMillis>nowMillis;
		}
	}
}
