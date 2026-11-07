package org.joinmastodon.android.model;

import com.google.gson.annotations.SerializedName;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.RequiredField;

import androidx.annotation.StringRes;

public class PushNotification extends BaseModel{
	public String accessToken;
	public String preferredLocale;
	public String notificationId;
	@RequiredField
	public Type notificationType;
	@RequiredField
	public String icon;
	@RequiredField
	public String title;
	@RequiredField
	public String body;

	@Override
	public String toString(){
		return "PushNotification{"+
				"accessToken='"+accessToken+'\''+
				", preferredLocale='"+preferredLocale+'\''+
				", notificationId="+notificationId+
				", notificationType="+notificationType+
				", icon='"+icon+'\''+
				", title='"+title+'\''+
				", body='"+body+'\''+
				'}';
	}

	public enum Type{
		@SerializedName("favourite")
		FAVORITE(R.string.notification_type_favorite),
		@SerializedName("mention")
		MENTION(R.string.notification_type_mention),
		@SerializedName("reblog")
		REBLOG(R.string.notification_type_reblog),
		@SerializedName("follow")
		FOLLOW(R.string.notification_type_follow),
		@SerializedName("poll")
		POLL(R.string.notification_type_poll),
		@SerializedName("status")
		STATUS(R.string.notification_type_status),
		// MAMMUT: port from upstream master
		@SerializedName("update")
		UPDATE(R.string.notification_type_status),
		@SerializedName("admin.sign_up")
		SIGN_UP(R.string.notification_type_follow),
		@SerializedName("admin.report")
		REPORT(R.string.notification_type_status);

		@StringRes
		public final int localizedName;

		Type(int localizedName){
			this.localizedName=localizedName;
		}
	}

	// MAMMUT: port from upstream master — build a push payload from a fetched notification so
	// UnifiedPush (non-standard, i.e. undecryptable) messages can reuse the FCM code path.
	public static PushNotification fromNotification(android.content.Context context, org.joinmastodon.android.api.session.AccountSession account, Notification notification){
		PushNotification pn=new PushNotification();
		pn.notificationType=switch(notification.type){
			case FOLLOW -> Type.FOLLOW;
			case MENTION -> Type.MENTION;
			case REBLOG -> Type.REBLOG;
			case FAVORITE -> Type.FAVORITE;
			case POLL -> Type.POLL;
			case STATUS -> Type.STATUS;
			case UPDATE -> Type.UPDATE;
			default -> Type.STATUS;
		};
		String title=context.getString(switch(notification.type){
			case FOLLOW -> R.string.user_followed_you;
			case MENTION -> R.string.sk_notification_mention;
			case REBLOG -> R.string.notification_boosted;
			case FAVORITE -> R.string.user_favorited;
			case POLL -> R.string.notification_type_poll;
			case STATUS -> R.string.sk_posted;
			default -> R.string.sk_posted;
		});
		pn.title=String.format(title, notification.account.displayName);
		if(notification.status!=null){
			pn.icon=notification.status.account.avatarStatic;
			pn.body=notification.status.getStrippedText();
		}else{
			pn.icon=notification.account.avatarStatic;
			pn.body=notification.account.getDisplayUsername();
		}
		return pn;
	}
}
