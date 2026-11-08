package org.joinmastodon.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.requests.notifications.GetNotificationByID;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Mention;
import org.joinmastodon.android.model.NotificationType;
import org.joinmastodon.android.model.PushNotification;
import org.joinmastodon.android.model.StatusPrivacy;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.ImageLoaderCallback;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class PushNotificationReceiver extends BroadcastReceiver{
	private static final String TAG="PushNotificationReceive";

	public static final int NOTIFICATION_ID=178;

	@Override
	public void onReceive(Context context, Intent intent){
		if(BuildConfig.DEBUG){
			Log.e(TAG, "received: "+intent);
			Bundle extras=intent.getExtras();
			for(String key : extras.keySet()){
				Log.i(TAG, key+" -> "+extras.get(key));
			}
		}
		if("com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())){
			String k=intent.getStringExtra("k");
			String p=intent.getStringExtra("p");
			String s=intent.getStringExtra("s");
			String pushAccountID=intent.getStringExtra("x");
			if(!TextUtils.isEmpty(pushAccountID) && !TextUtils.isEmpty(k) && !TextUtils.isEmpty(p) && !TextUtils.isEmpty(s)){
				MastodonAPIController.runInBackground(()->{
					try{
						List<AccountSession> accounts=AccountSessionManager.getInstance().getLoggedInAccounts();
						AccountSession account=null;
						for(AccountSession acc:accounts){
							if(pushAccountID.equals(acc.pushAccountID)){
								account=acc;
								break;
							}
						}
						if(account==null){
							Log.w(TAG, "onReceive: account for id '"+pushAccountID+"' not found");
							return;
						}
						if(account.getLocalPreferences().getNotificationsPauseEndTime()>System.currentTimeMillis()){
							Log.i(TAG, "onReceive: dropping notification because user has paused notifications for this account");
							return;
						}
						String accountID=account.getID();
						PushNotification pn=AccountSessionManager.getInstance().getAccount(accountID).getPushSubscriptionManager().decryptNotification(k, p, s);
						new GetNotificationByID(pn.notificationId)
								.setCallback(new Callback<>(){
									@Override
									public void onSuccess(org.joinmastodon.android.model.Notification result){
										MastodonAPIController.runInBackground(()->PushNotificationReceiver.this.notify(context, pn, accountID, result));
									}

									@Override
									public void onError(ErrorResponse error){
										MastodonAPIController.runInBackground(()->PushNotificationReceiver.this.notify(context, pn, accountID, null));
									}
								})
								.exec(accountID);
					}catch(Exception x){
						Log.w(TAG, x);
					}
				});
			}else{
				Log.w(TAG, "onReceive: invalid push notification format");
			}
		}
	}

	// MAMMUT: port from upstream master — entry point for UnifiedPush payloads
	public void notifyUnifiedPush(Context context, org.joinmastodon.android.api.session.AccountSession account, org.joinmastodon.android.model.Notification notification){
		notify(context, PushNotification.fromNotification(context, account, notification), account.getID(), notification);
	}

	public void notify(Context context, PushNotification pn, String accountID, org.joinmastodon.android.model.Notification notification){
		// MAMMUT: keep the in-app unread indicator live while the app is in memory
		org.joinmastodon.android.ui.utils.UnreadNotificationStore.onPushReceived();
		if(TextUtils.isEmpty(pn.icon)){
			doNotify(context, pn, accountID, notification, null);
		}else{
			ImageCache.getInstance(context).get(new UrlImageLoaderRequest(pn.icon, V.dp(50), V.dp(50)), null, new ImageLoaderCallback(){
				@Override
				public void onImageLoaded(ImageLoaderRequest req, Drawable image){
					doNotify(context, pn, accountID, notification, image);
				}

				@Override
				public void onImageLoadingFailed(ImageLoaderRequest req, Throwable error){
					doNotify(context, pn, accountID, notification, null);
				}
			}, true);
		}
	}

	private void doNotify(Context context, PushNotification pn, String accountID, org.joinmastodon.android.model.Notification notification, Drawable avatar){
		NotificationManager nm=context.getSystemService(NotificationManager.class);
		Account self=AccountSessionManager.getInstance().getAccount(accountID).self;
		String accountName="@"+self.username+"@"+AccountSessionManager.getInstance().getAccount(accountID).domain;
		Notification.Builder builder;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
			boolean hasGroup=false;
			int version=AccountSessionManager.get(accountID).getRawLocalPreferences().getInt("notificationChannelsVersion", 1);
			List<NotificationChannelGroup> channelGroups=nm.getNotificationChannelGroups();
			for(NotificationChannelGroup group:channelGroups){
				if(group.getId().equals(accountID)){
					hasGroup=true;
					break;
				}
			}
			if(!hasGroup || version!=2){
				NotificationChannelGroup group=new NotificationChannelGroup(accountID, accountName);
				nm.createNotificationChannelGroup(group);
				List<NotificationChannel> channels=Arrays.stream(PushNotification.Type.values())
						.map(type->{
							NotificationChannel channel=new NotificationChannel(accountID+"_"+type, context.getString(type.localizedName), NotificationManager.IMPORTANCE_DEFAULT);
							channel.setLightColor(context.getColor(R.color.primary_700));
							channel.enableLights(true);
							channel.setGroup(accountID);
							return channel;
						})
						.collect(Collectors.toList());
				nm.createNotificationChannels(channels);
				AccountSessionManager.get(accountID).getRawLocalPreferences().edit().putInt("notificationChannelsVersion", 2).apply();
			}
			builder=new Notification.Builder(context, accountID+"_"+pn.notificationType);
		}else{
			builder=new Notification.Builder(context)
					.setPriority(Notification.PRIORITY_DEFAULT)
					.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
		}
		Intent contentIntent=new Intent(context, MainActivity.class);
		contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		contentIntent.putExtra("fromNotification", true);
		contentIntent.putExtra("accountID", accountID);
		if(notification!=null){
			contentIntent.putExtra("notification", Parcels.wrap(notification));
		}
		builder.setContentTitle(pn.title)
				.setContentText(pn.body)
				.setStyle(new Notification.BigTextStyle().bigText(pn.body))
				.setSmallIcon(R.drawable.ic_ntf_logo)
				.setContentIntent(PendingIntent.getActivity(context, (accountID+pn.notificationId).hashCode() & 0xFFFF, contentIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
				.setWhen(notification==null ? System.currentTimeMillis() : notification.createdAt.toEpochMilli())
				.setShowWhen(true)
				.setCategory(Notification.CATEGORY_SOCIAL)
				.setAutoCancel(true)
				.setOnlyAlertOnce(true)
				.setLights(context.getColor(R.color.primary_700), 500, 1000)
				.setColor(context.getColor(R.color.primary_700))
				.setGroup(accountID);
		// MAMMUT: port from upstream master — per-type small icons unless the user wants one uniform icon
		if(!GlobalUserPreferences.uniformNotificationIcon){
			// A switch on the enum throws when the server sends a type this build does not
			// map (moderation, admin sign-ups, a UnifiedPush payload without a type), and the
			// ordinal() NPE takes the whole process down for the sake of a notification icon.
			builder.setSmallIcon(pn.notificationType==null ? R.drawable.ic_ntf_logo : switch(pn.notificationType){
				case FAVORITE -> GlobalUserPreferences.likeIcon ? R.drawable.ic_fluent_heart_24_filled : R.drawable.ic_fluent_star_24_filled;
				case REBLOG -> R.drawable.ic_fluent_arrow_repeat_all_24_filled;
				case FOLLOW -> R.drawable.ic_fluent_person_add_24_filled;
				case MENTION -> R.drawable.ic_fluent_mention_24_filled;
				case POLL -> R.drawable.ic_fluent_poll_24_filled;
				case STATUS -> R.drawable.ic_fluent_chat_24_filled;
				default -> R.drawable.ic_ntf_logo;
			});
		}
		if(avatar!=null){
			builder.setLargeIcon(UiUtils.getBitmapFromDrawable(avatar));
		}
		if(AccountSessionManager.getInstance().getLoggedInAccounts().size()>1){
			builder.setSubText(accountName);
		}
		String notificationTag=accountID+"_"+(notification==null ? 0 : notification.id);
		if(notification!=null && (notification.type==NotificationType.MENTION)){
			ArrayList<String> mentions=new ArrayList<>();
			String ownID=AccountSessionManager.getInstance().getAccount(accountID).self.id;
			if(!notification.status.account.id.equals(ownID))
				mentions.add('@'+notification.status.account.acct);
			for(Mention mention:notification.status.mentions){
				if(mention.id.equals(ownID))
					continue;
				String m='@'+mention.acct;
				if(!mentions.contains(m))
					mentions.add(m);
			}
			String replyPrefix=mentions.isEmpty() ? "" : TextUtils.join(" ", mentions)+" ";

			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
				Intent replyIntent=new Intent(context, NotificationActionHandlerService.class);
				replyIntent.putExtra("action", "reply");
				replyIntent.putExtra("account", accountID);
				replyIntent.putExtra("post", notification.status.id);
				replyIntent.putExtra("notificationTag", notificationTag);
				replyIntent.putExtra("visibility", notification.status.visibility.toString());
				replyIntent.putExtra("replyPrefix", replyPrefix);

			// MAMMUT: phase 1 port from upstream master — prefix the CW of a push-notification reply with "re: " per preference
			String cwText=notification.status.spoilerText;
			boolean prefixCW=GlobalUserPreferences.prefixReplies==GlobalUserPreferences.PrefixRepliesMode.ALWAYS
					|| (GlobalUserPreferences.prefixReplies==GlobalUserPreferences.PrefixRepliesMode.TO_OTHERS && !ownID.equals(notification.status.account.id));
			if(cwText!=null && prefixCW && !cwText.startsWith("re: "))
				replyIntent.putExtra("replySpoilerText", "re: "+cwText);
				builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_reply_24px),
						context.getString(R.string.button_reply), PendingIntent.getService(context, (accountID+pn.notificationId+"reply").hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE))
								.addRemoteInput(new RemoteInput.Builder("replyText").setLabel(context.getString(R.string.button_reply)).build())
								.build());
			}

			Intent favIntent=new Intent(context, NotificationActionHandlerService.class);
			favIntent.putExtra("action", "favorite");
			favIntent.putExtra("account", accountID);
			favIntent.putExtra("post", notification.status.id);
			favIntent.putExtra("notificationTag", notificationTag);
			builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, GlobalUserPreferences.likeIcon ? R.drawable.ic_fluent_heart_24_filled : R.drawable.ic_star_24px),
					context.getString(R.string.button_favorite), PendingIntent.getService(context, (accountID+pn.notificationId+"favorite").hashCode(), favIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());

			// MAMMUT: port from upstream master — bookmark is the third action by default,
			// optionally swapped for boost (which is unavailable on direct posts)
			boolean useBoost=GlobalUserPreferences.swapBookmarkWithBoostAction && notification.status.visibility!=StatusPrivacy.DIRECT;
			if(useBoost){
				Intent boostIntent=new Intent(context, NotificationActionHandlerService.class);
				boostIntent.putExtra("action", "boost");
				boostIntent.putExtra("account", accountID);
				boostIntent.putExtra("post", notification.status.id);
				boostIntent.putExtra("notificationTag", notificationTag);
				PendingIntent boostActionIntent=PendingIntent.getService(context, (accountID+pn.notificationId+"boost").hashCode(), boostIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
				builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_boost_24px),
						context.getString(R.string.button_reblog), boostActionIntent).build());
			}else{
				Intent bookmarkIntent=new Intent(context, NotificationActionHandlerService.class);
				bookmarkIntent.putExtra("action", "bookmark");
				bookmarkIntent.putExtra("account", accountID);
				bookmarkIntent.putExtra("post", notification.status.id);
				bookmarkIntent.putExtra("notificationTag", notificationTag);
				PendingIntent bookmarkActionIntent=PendingIntent.getService(context, (accountID+pn.notificationId+"bookmark").hashCode(), bookmarkIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
				builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_fluent_bookmark_24_regular),
						context.getString(R.string.add_bookmark), bookmarkActionIntent).build());
			}
		}
		nm.notify(notificationTag, NOTIFICATION_ID, builder.build());

		StatusBarNotification[] activeNotifications=nm.getActiveNotifications();
		ArrayList<String> summaryLines=new ArrayList<>();
		int notificationCount=0;
		for(StatusBarNotification sbn:activeNotifications){
			String tag=sbn.getTag();
			if(tag!=null && tag.startsWith(accountID+"_")){
				if((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY)==0){
					if(summaryLines.size()<5){
						summaryLines.add(sbn.getNotification().extras.getString("android.title"));
					}
					notificationCount++;
				}
			}
		}

		if(summaryLines.size()>1){
			Notification.Builder summaryBuilder;
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
				summaryBuilder=new Notification.Builder(context, accountID+"_"+pn.notificationType);
			}else{
				summaryBuilder=new Notification.Builder(context)
						.setPriority(Notification.PRIORITY_DEFAULT);
			}
			Notification.InboxStyle inboxStyle=new Notification.InboxStyle();
			for(String line:summaryLines){
				inboxStyle.addLine(line);
			}
			summaryBuilder.setContentTitle(context.getString(R.string.app_name))
					.setContentText(context.getResources().getQuantityString(R.plurals.x_new_notifications, notificationCount, notificationCount))
					.setSmallIcon(R.drawable.ic_ntf_logo)
					.setColor(context.getColor(R.color.primary_700))
					.setContentIntent(PendingIntent.getActivity(context, accountID.hashCode() & 0xFFFF, contentIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
					.setWhen(notification==null ? System.currentTimeMillis() : notification.createdAt.toEpochMilli())
					.setShowWhen(true)
					.setCategory(Notification.CATEGORY_SOCIAL)
					.setAutoCancel(true)
					.setGroup(accountID)
					.setGroupSummary(true)
					.setStyle(inboxStyle.setSummaryText(accountName));
			nm.notify(accountID+"_summary", NOTIFICATION_ID, summaryBuilder.build());
		}
	}
}
