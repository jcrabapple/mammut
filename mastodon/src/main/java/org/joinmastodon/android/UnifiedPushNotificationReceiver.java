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
		Log.d(TAG, "onNewEndpoint: "+endpoint.getUrl()+" for "+instance);
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
		Log.d(TAG, "onRegistrationFailed: "+instance+" ("+reason+")");
		android.widget.Toast.makeText(context, R.string.mo_up_failed, android.widget.Toast.LENGTH_LONG).show();
		fallBackToFcm(instance);
	}

	@Override
	public void onUnregistered(@NonNull Context context, @NonNull String instance){
		Log.d(TAG, "onUnregistered: "+instance);
		fallBackToFcm(instance);
	}

	private void fallBackToFcm(String instance){
		AccountSession account=AccountSessionManager.getInstance().tryGetAccount(instance);
		if(account!=null)
			account.getPushSubscriptionManager().registerAccountForPush(null);
	}

	@Override
	public void onMessage(@NonNull Context context, @NonNull PushMessage message, @NonNull String instance){
		Log.d(TAG, "onMessage for "+instance);
		AccountSession account=AccountSessionManager.getInstance().tryGetAccount(instance);
		if(account==null)
			return;
		if(message.getDecrypted()){
			PushNotification pn=MastodonAPIController.gson.fromJson(new String(message.getContent(), StandardCharsets.UTF_8), PushNotification.class);
			new GetNotificationByID(pn.notificationId)
					.setCallback(new Callback<>(){
						@Override
						public void onSuccess(Notification result){
							MastodonAPIController.runInBackground(()->new PushNotificationReceiver().notify(context, pn, instance, result));
						}

						@Override
						public void onError(ErrorResponse error){
							MastodonAPIController.runInBackground(()->new PushNotificationReceiver().notify(context, pn, instance, null));
						}
					})
					.exec(instance);
		}else{
			// The app cannot decrypt this payload, so pull the newest notification instead.
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
							Log.w(TAG, "onMessage: could not fetch the notification for "+instance);
						}
					})
					.exec(instance);
		}
	}
}
