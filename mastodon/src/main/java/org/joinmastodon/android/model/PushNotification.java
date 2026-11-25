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
		REPORT(R.string.notification_type_status),
		// MAMMUT: audit R3 F2 — Mastodon 4.x sends these; without them the @RequiredField
		// validation dropped every quote/system push before it could be shown
		@SerializedName("quote")
		QUOTE(R.string.notification_type_quote),
		@SerializedName("quoted_update")
		QUOTED_UPDATE(R.string.sk_notification_type_update),
		@SerializedName("severed_relationships")
		SEVERED_RELATIONSHIPS(R.string.app_name),
		@SerializedName("moderation_warning")
		MODERATION_WARNING(R.string.moderation_warning_action_none);

		@StringRes
		public final int localizedName;

		Type(int localizedName){
			this.localizedName=localizedName;
		}
	}

	// MAMMUT: port from upstream master — build a push payload from a fetched notification so
	// UnifiedPush (non-standard, i.e. undecryptable) messages can reuse the FCM code path.
	public static PushNotification fromNotification(android.content.Context context, org.joinmastodon.android.api.session.AccountSession account, Notification notification){
		return Fallback.build(context::getString, notification, status->status.getStrippedText());
	}

	/**
	 * MAMMUT: audit R3 F1 + F2 — pure mapping from a fetched notification to the synthetic
	 * push payload the UnifiedPush fallback posts. String resolution and status-body
	 * extraction are injected so the full type matrix is unit testable on the JVM.
	 */
	public static class Fallback{
		/** Resolves a string resource without touching Android in tests. */
		public interface Strings{
			String get(@StringRes int res);
		}

		/** Extracts the notification body from a status without touching Android in tests. */
		public interface StatusText{
			String of(Status status);
		}

		public static PushNotification build(Strings strings, Notification notification, StatusText statusText){
			PushNotification pn=new PushNotification();
			// R3 F1: the receiver keys every PendingIntent on notificationId; leaving it null
			// collapsed all fallback notifications of an account onto one mutable intent
			pn.notificationId=notification.id;
			pn.notificationType=pushTypeFor(notification.type);
			pn.title=titleFor(strings, notification);
			if(notification.status!=null){
				pn.icon=notification.status.account!=null && notification.status.account.avatarStatic!=null ? notification.status.account.avatarStatic : "";
				pn.body=statusText.of(notification.status);
			}else if(notification.account!=null){
				pn.icon=notification.account.avatarStatic!=null ? notification.account.avatarStatic : "";
				pn.body=notification.account.getDisplayUsername();
			}else{
				// R3 F2: system notifications (severed relationships, moderation warnings)
				// have no account; the payload still has to be displayable
				pn.icon="";
				pn.body=systemBody(strings, notification);
			}
			return pn;
		}

		/** Null-safe type mapping; unknown/null (future admin classes) degrade to STATUS. */
		public static Type pushTypeFor(NotificationType type){
			if(type==null)
				return Type.STATUS;
			return switch(type){
				case FOLLOW -> Type.FOLLOW;
				case FOLLOW_REQUEST -> Type.FOLLOW;
				case MENTION -> Type.MENTION;
				case REBLOG -> Type.REBLOG;
				case FAVORITE -> Type.FAVORITE;
				case POLL -> Type.POLL;
				case STATUS -> Type.STATUS;
				case UPDATE -> Type.UPDATE;
				case QUOTE -> Type.QUOTE;
				case QUOTED_UPDATE -> Type.QUOTED_UPDATE;
				case SEVERED_RELATIONSHIPS -> Type.SEVERED_RELATIONSHIPS;
				case MODERATION_WARNING -> Type.MODERATION_WARNING;
				case ADMIN_SIGN_UP -> Type.SIGN_UP;
				case ADMIN_REPORT -> Type.REPORT;
			};
		}

		private static String titleFor(Strings strings, Notification notification){
			if(notification.account==null)
				return strings.get(R.string.app_name);
			String title=strings.get(titleResFor(notification.type));
			return String.format(title, notification.account.displayName);
		}

		private static int titleResFor(NotificationType type){
			if(type==null)
				return R.string.sk_posted;
			return switch(type){
				case FOLLOW, FOLLOW_REQUEST -> R.string.user_followed_you;
				case MENTION -> R.string.sk_notification_mention;
				case REBLOG -> R.string.notification_boosted;
				case FAVORITE -> R.string.user_favorited;
				case POLL -> R.string.notification_type_poll;
				case STATUS -> R.string.sk_posted;
				case UPDATE -> R.string.sk_posted;
				case QUOTE, QUOTED_UPDATE -> R.string.user_quoted_post;
				case ADMIN_SIGN_UP -> R.string.mo_admin_signed_up;
				case ADMIN_REPORT -> R.string.mo_admin_reported;
				default -> R.string.sk_posted;
			};
		}

		private static String systemBody(Strings strings, Notification notification){
			if(notification.type==NotificationType.MODERATION_WARNING && notification.moderationWarning!=null){
				if(notification.moderationWarning.action==null)
					return "";
				return strings.get(switch(notification.moderationWarning.action){
					case NONE -> R.string.moderation_warning_action_none;
					case DISABLE -> R.string.moderation_warning_action_disable;
					case MARK_STATUSES_AS_SENSITIVE -> R.string.moderation_warning_action_mark_statuses_as_sensitive;
					case DELETE_STATUSES -> R.string.moderation_warning_action_delete_statuses;
					case SENSITIVE -> R.string.moderation_warning_action_sensitive;
					case SILENCE -> R.string.moderation_warning_action_silence;
					case SUSPEND -> R.string.moderation_warning_action_suspend;
				});
			}
			if(notification.type==NotificationType.SEVERED_RELATIONSHIPS && notification.event!=null && notification.event.targetName!=null)
				return notification.event.targetName;
			return "";
		}

		// R3 F1: the receiver's PendingIntent request codes key on these strings; they live
		// here (pure) so tests can assert distinct, stable keys per notification
		public static String contentIntentKey(String accountID, String notificationId){
			return accountID+String.valueOf(notificationId);
		}

		public static String actionIntentKey(String accountID, String notificationId, String action){
			return accountID+String.valueOf(notificationId)+action;
		}
	}
}
