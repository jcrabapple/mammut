package org.joinmastodon.android.api.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.joinmastodon.android.model.AccountWarning;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Filter;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.QuoteApproval;
import org.joinmastodon.android.model.StatusQuotePolicy;
import org.junit.Test;

public class SafeEnumSetAndModelFallbackTest{

	private static Gson createGson(){
		return new GsonBuilder()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.registerTypeAdapter(java.time.Instant.class, new IsoInstantTypeAdapter())
				.registerTypeAdapterFactory(new SafeEnumSetTypeAdapterFactory())
				.create();
	}

	@Test
	public void quoteApprovalIgnoresUnknownPoliciesAndDefaultsCurrentUser() throws Exception{
		Gson gson=createGson();
		String json="{\"automatic\":[\"public\",\"future_policy_2030\"],\"manual\":[\"unknown_only\"],\"current_user\":\"future_state\"}";
		QuoteApproval qa=gson.fromJson(json, QuoteApproval.class);
		qa.postprocess();
		assertNotNull(qa.automatic);
		assertTrue(qa.automatic.contains(QuoteApproval.Policy.PUBLIC));
		assertEquals(1, qa.automatic.size());
		assertNotNull(qa.manual);
		assertTrue(qa.manual.isEmpty());
		assertEquals(QuoteApproval.CurrentUserPolicy.UNKNOWN, qa.currentUser);
		assertEquals(StatusQuotePolicy.PUBLIC, qa.toQuotePolicy());
	}

	@Test
	public void filterContextIgnoresUnknownContextNames() throws Exception{
		Gson gson=createGson();
		String json="{\"id\":\"f1\",\"title\":\"mute\",\"context\":[\"home\",\"future_surface\"]}";
		Filter filter=gson.fromJson(json, Filter.class);
		filter.postprocess();
		assertNotNull(filter.context);
		assertTrue(filter.context.contains(FilterContext.HOME));
		assertEquals(1, filter.context.size());
	}

	@Test
	public void attachmentUnknownTypeDefaultsToUnknownInsteadOfThrowing() throws Exception{
		Gson gson=createGson();
		String json="{\"id\":\"a1\",\"type\":\"3d_model\",\"url\":\"https://example.social/m.glb\"}";
		Attachment att=gson.fromJson(json, Attachment.class);
		att.postprocess();
		assertEquals(Attachment.Type.UNKNOWN, att.type);
	}

	@Test
	public void accountWarningUnknownActionDefaultsToNone() throws Exception{
		Gson gson=createGson();
		String json="{\"id\":\"w1\",\"action\":\"future_moderation_action\",\"text\":\"notice\"}";
		AccountWarning warning=gson.fromJson(json, AccountWarning.class);
		warning.postprocess();
		assertEquals(AccountWarning.Action.NONE, warning.action);
	}

	@Test
	public void enumSetSerializationRoundTripsKnownValues(){
		Gson gson=createGson();
		String json="{\"id\":\"f2\",\"title\":\"t\",\"context\":[\"home\",\"public\"]}";
		Filter filter=gson.fromJson(json, Filter.class);
		String reserialized=gson.toJson(filter);
		Filter again=gson.fromJson(reserialized, Filter.class);
		assertTrue(again.context.contains(FilterContext.HOME));
		assertTrue(again.context.contains(FilterContext.PUBLIC));
		assertFalse(again.context.contains(FilterContext.ACCOUNT));
	}
}
