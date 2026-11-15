package org.joinmastodon.android.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.joinmastodon.android.api.gson.IsoInstantTypeAdapter;
import org.joinmastodon.android.api.gson.IsoLocalDateTypeAdapter;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.NotificationType;
import org.joinmastodon.android.model.viewmodel.NotificationViewModel;
import org.junit.Test;

import java.time.Instant;

/**
 * Regression test for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4),
 * R3 F5: account-less system notifications (severed_relationships, moderation_warning)
 * must parse, postprocess, and convert to a displayable V1 view model instead of NPE-ing
 * the whole refresh; account-requiring types still fail validation without an account.
 */
public class CacheControllerV1ConversionTest{
	private static final Gson GSON=new GsonBuilder()
			.disableHtmlEscaping()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.registerTypeAdapter(Instant.class, new IsoInstantTypeAdapter())
			.registerTypeAdapter(java.time.LocalDate.class, new IsoLocalDateTypeAdapter())
			.create();

	private static final String SEVERED_JSON="{\"id\":\"1001\",\"type\":\"severed_relationships\",\"created_at\":\"2026-01-02T03:04:05Z\","
			+"\"event\":{\"id\":\"e1\",\"type\":\"account_suspension\",\"target_name\":\"spam.example\",\"purged\":false}}";
	private static final String MODERATION_JSON="{\"id\":\"1002\",\"type\":\"moderation_warning\",\"created_at\":\"2026-01-02T03:04:05Z\","
			+"\"moderation_warning\":{\"id\":\"w1\",\"action\":\"suspend\"}}";

	@Test
	public void severedRelationshipFixtureWithoutAccountParsesAndConverts() throws Exception{
		Notification n=GSON.fromJson(SEVERED_JSON, Notification.class);
		n.postprocess();
		assertEquals(NotificationType.SEVERED_RELATIONSHIPS, n.type);
		assertNotNull(n.event);

		NotificationViewModel nvm=CacheController.V1Conversion.convert(n);
		assertEquals("converted-1001", nvm.getID());
		assertTrue(nvm.notification.sampleAccountIds.isEmpty());
		assertTrue(nvm.accounts.isEmpty());
		assertEquals("e1", nvm.notification.event.id);
	}

	@Test
	public void moderationWarningFixtureWithoutAccountParsesAndConverts() throws Exception{
		Notification n=GSON.fromJson(MODERATION_JSON, Notification.class);
		n.postprocess();
		assertEquals(NotificationType.MODERATION_WARNING, n.type);

		NotificationViewModel nvm=CacheController.V1Conversion.convert(n);
		assertEquals("converted-1002", nvm.getID());
		assertTrue(nvm.notification.sampleAccountIds.isEmpty());
		assertTrue(nvm.accounts.isEmpty());
		assertEquals("w1", nvm.notification.moderationWarning.id);
	}

	@Test
	public void accountNotificationsStillMapTheirAccount(){
		// gson parse only: Account.postprocess() touches android.text.TextUtils, which is
		// not mocked on the JVM; account mapping is what this test pins
		Notification n=GSON.fromJson("{\"id\":\"1003\",\"type\":\"favourite\",\"created_at\":\"2026-01-02T03:04:05Z\","
				+"\"account\":{\"id\":\"a1\",\"acct\":\"fan@example.social\",\"display_name\":\"Fan\"}}", Notification.class);
		assertEquals(NotificationType.FAVORITE, n.type);
		NotificationViewModel nvm=CacheController.V1Conversion.convert(n);
		assertEquals(1, nvm.notification.sampleAccountIds.size());
		assertEquals("a1", nvm.notification.sampleAccountIds.get(0));
		assertEquals(1, nvm.accounts.size());
	}

	@Test
	public void mentionWithoutAccountStillFailsValidation(){
		Notification n=GSON.fromJson("{\"id\":\"1004\",\"type\":\"mention\",\"created_at\":\"2026-01-02T03:04:05Z\"}", Notification.class);
		assertThrows(ObjectValidationException.class, n::postprocess);
	}
}
