package org.joinmastodon.android.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;

import org.joinmastodon.android.api.ObjectValidationException;
import org.joinmastodon.android.model.PushNotification.Fallback;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Regression tests for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4):
 * R3 F1 (distinct stable PendingIntent keys per fallback notification) and R3 F2
 * (quote/quoted_update/severed_relationships/moderation_warning push types; null-safe
 * type/account handling in the UnifiedPush fallback mapping; account-less system
 * title/body; admin + unknown types through both the FCM validation path and the
 * fallback mapping without throwing).
 */
public class PushNotificationFallbackTest{
	private static final Fallback.Strings STUB_STRINGS=res->"s"+res;
	private static final Fallback.StatusText STUB_TEXT=status->"body";

	private static Notification notification(NotificationType type, String id){
		Notification n=new Notification();
		n.id=id;
		n.type=type;
		n.createdAt=Instant.now();
		if(type!=NotificationType.SEVERED_RELATIONSHIPS && type!=NotificationType.MODERATION_WARNING){
			Account account=new Account();
			account.id="acc-"+id;
			account.acct="user"+id;
			account.displayName="User "+id;
			account.avatarStatic="https://img.example/"+id;
			n.account=account;
		}
		return n;
	}

	// R3 F1: distinct stable PendingIntent keys per fallback notification

	@Test
	public void distinctNotificationsGetDistinctContentKeys(){
		PushNotification first=Fallback.build(STUB_STRINGS, notification(NotificationType.MENTION, "100"), STUB_TEXT);
		PushNotification second=Fallback.build(STUB_STRINGS, notification(NotificationType.MENTION, "200"), STUB_TEXT);
		assertEquals("100", first.notificationId);
		assertEquals("200", second.notificationId);
		assertNotEquals(Fallback.contentIntentKey("acct", first.notificationId), Fallback.contentIntentKey("acct", second.notificationId));
	}

	@Test
	public void actionKeysAreDistinctPerNotificationAndAction(){
		PushNotification first=Fallback.build(STUB_STRINGS, notification(NotificationType.MENTION, "100"), STUB_TEXT);
		PushNotification second=Fallback.build(STUB_STRINGS, notification(NotificationType.MENTION, "200"), STUB_TEXT);
		for(String action:new String[]{"reply", "favorite", "boost", "bookmark"}){
			String k1=Fallback.actionIntentKey("acct", first.notificationId, action);
			String k2=Fallback.actionIntentKey("acct", second.notificationId, action);
			assertNotEquals(action+" keys collide", k1, k2);
			assertEquals(k1, Fallback.actionIntentKey("acct", "100", action));
		}
		// distinct actions on the same notification must not collide either
		assertNotEquals(Fallback.actionIntentKey("acct", "100", "favorite"), Fallback.actionIntentKey("acct", "100", "bookmark"));
	}

	@Test
	public void keysAreScopedByAccount(){
		assertNotEquals(Fallback.contentIntentKey("acctA", "100"), Fallback.contentIntentKey("acctB", "100"));
	}

	// R3 F2: full NotificationType matrix through the fallback mapping without throwing

	@Test
	public void everyNotificationTypeMapsThroughFallback(){
		for(NotificationType type:NotificationType.values()){
			Notification n=notification(type, "1");
			PushNotification pn=Fallback.build(STUB_STRINGS, n, STUB_TEXT);
			assertNotNull(type.name()+" produced no push type", pn.notificationType);
			assertNotNull(type.name()+" produced no title", pn.title);
			assertNotNull(type.name()+" produced no body", pn.body);
			assertEquals("1", pn.notificationId);
		}
	}

	@Test
	public void unknownNullTypeDegradesToStatus(){
		Notification n=notification(null, "7");
		PushNotification pn=Fallback.build(STUB_STRINGS, n, STUB_TEXT);
		assertEquals(PushNotification.Type.STATUS, pn.notificationType);
	}

	@Test
	public void newTypesMapToTheirPushCounterparts(){
		assertEquals(PushNotification.Type.QUOTE, Fallback.pushTypeFor(NotificationType.QUOTE));
		assertEquals(PushNotification.Type.QUOTED_UPDATE, Fallback.pushTypeFor(NotificationType.QUOTED_UPDATE));
		assertEquals(PushNotification.Type.SEVERED_RELATIONSHIPS, Fallback.pushTypeFor(NotificationType.SEVERED_RELATIONSHIPS));
		assertEquals(PushNotification.Type.MODERATION_WARNING, Fallback.pushTypeFor(NotificationType.MODERATION_WARNING));
		assertEquals(PushNotification.Type.SIGN_UP, Fallback.pushTypeFor(NotificationType.ADMIN_SIGN_UP));
		assertEquals(PushNotification.Type.REPORT, Fallback.pushTypeFor(NotificationType.ADMIN_REPORT));
	}

	@Test
	public void systemNotificationsGetAccountLessTitleAndBody(){
		Notification severed=notification(NotificationType.SEVERED_RELATIONSHIPS, "9");
		RelationshipSeveranceEvent event=new RelationshipSeveranceEvent();
		event.type=RelationshipSeveranceEvent.Type.ACCOUNT_SUSPENSION;
		event.targetName="spam.example";
		severed.event=event;
		PushNotification pnSevered=Fallback.build(STUB_STRINGS, severed, STUB_TEXT);
		assertEquals("s"+org.joinmastodon.android.R.string.app_name, pnSevered.title);
		assertEquals("spam.example", pnSevered.body);
		assertEquals("", pnSevered.icon);

		Notification warned=notification(NotificationType.MODERATION_WARNING, "10");
		AccountWarning warning=new AccountWarning();
		warning.action=AccountWarning.Action.SUSPEND;
		warned.moderationWarning=warning;
		PushNotification pnWarned=Fallback.build(STUB_STRINGS, warned, STUB_TEXT);
		assertNotNull(pnWarned.title);
		assertEquals("s"+org.joinmastodon.android.R.string.moderation_warning_action_suspend, pnWarned.body);
	}

	@Test
	public void mentionWithStatusUsesStatusBody(){
		Notification n=notification(NotificationType.MENTION, "3");
		n.status=Status.ofFake("st-3", "hello there", Instant.now());
		n.status.account=n.account;
		PushNotification pn=Fallback.build(STUB_STRINGS, n, STUB_TEXT);
		assertEquals("body", pn.body);
		assertEquals("https://img.example/3", pn.icon);
	}

	// R3 F2: the FCM validation path (decryptNotification's gson + postprocess tail)

	private static Gson fcmGson(){
		return new GsonBuilder()
				.disableHtmlEscaping()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.registerTypeAdapter(Instant.class, new org.joinmastodon.android.api.gson.IsoInstantTypeAdapter())
				.registerTypeAdapter(java.time.LocalDate.class, new org.joinmastodon.android.api.gson.IsoLocalDateTypeAdapter())
				.create();
	}

	private static String fcmPayload(String serializedType){
		Map<String, Object> payload=new HashMap<>();
		payload.put("notification_id", "42");
		payload.put("notification_type", serializedType);
		payload.put("icon", "https://img.example/a.png");
		payload.put("title", "Title");
		payload.put("body", "Body");
		return fcmGson().toJson(payload);
	}

	@Test
	public void everySerializedPushTypePassesFcmValidation() throws Exception{
		// the serialized names Mastodon 4.x emits, incl. the four types this fix adds
		Map<String, PushNotification.Type> names=new HashMap<>();
		names.put("favourite", PushNotification.Type.FAVORITE);
		names.put("mention", PushNotification.Type.MENTION);
		names.put("reblog", PushNotification.Type.REBLOG);
		names.put("follow", PushNotification.Type.FOLLOW);
		names.put("poll", PushNotification.Type.POLL);
		names.put("status", PushNotification.Type.STATUS);
		names.put("update", PushNotification.Type.UPDATE);
		names.put("admin.sign_up", PushNotification.Type.SIGN_UP);
		names.put("admin.report", PushNotification.Type.REPORT);
		names.put("quote", PushNotification.Type.QUOTE);
		names.put("quoted_update", PushNotification.Type.QUOTED_UPDATE);
		names.put("severed_relationships", PushNotification.Type.SEVERED_RELATIONSHIPS);
		names.put("moderation_warning", PushNotification.Type.MODERATION_WARNING);
		for(Map.Entry<String, PushNotification.Type> e:names.entrySet()){
			PushNotification pn=fcmGson().fromJson(fcmPayload(e.getKey()), PushNotification.class);
			pn.postprocess();
			assertEquals(e.getKey(), e.getValue(), pn.notificationType);
			assertEquals("42", pn.notificationId);
		}
	}

	@Test
	public void unknownFutureTypeFailsValidationCleanly(){
		PushNotification pn=fcmGson().fromJson(fcmPayload("turbo_like_3000"), PushNotification.class);
		// gson maps the unknown enum to null; @RequiredField rejects it — no NPE, the
		// receiver drops the null and moves on
		assertThrows(ObjectValidationException.class, pn::postprocess);
	}

	@Test
	public void adminTypesDeserializeInsteadOfNullingOut(){
		Gson gson=fcmGson();
		Notification signUp=gson.fromJson("{\"id\":\"1\",\"type\":\"admin.sign_up\",\"created_at\":\"2026-01-01T00:00:00Z\",\"account\":{\"id\":\"a\",\"acct\":\"u\"}}", Notification.class);
		assertEquals(NotificationType.ADMIN_SIGN_UP, signUp.type);
		Notification report=gson.fromJson("{\"id\":\"2\",\"type\":\"admin.report\",\"created_at\":\"2026-01-01T00:00:00Z\",\"account\":{\"id\":\"a\",\"acct\":\"u\"}}", Notification.class);
		assertEquals(NotificationType.ADMIN_REPORT, report.type);
	}

	@Test
	public void adminNotificationsMapThroughFallbackWithoutThrowing(){
		Map<NotificationType, Integer> titleRes=new HashMap<>();
		titleRes.put(NotificationType.ADMIN_SIGN_UP, org.joinmastodon.android.R.string.mo_admin_signed_up);
		titleRes.put(NotificationType.ADMIN_REPORT, org.joinmastodon.android.R.string.mo_admin_reported);
		for(Map.Entry<NotificationType, Integer> e:titleRes.entrySet()){
			Notification n=notification(e.getKey(), "5");
			PushNotification pn=Fallback.build(STUB_STRINGS, n, STUB_TEXT);
			assertNotNull(pn.notificationType);
			// the stub resolver returns "s<res>"; the title is the mapped resource and the
			// body is the actor's @acct
			assertEquals("s"+e.getValue().intValue(), pn.title);
			assertEquals("@user5", pn.body);
		}
	}
}
