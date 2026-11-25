package org.joinmastodon.android.fragments.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;

import org.joinmastodon.android.fragments.settings.SettingsAboutAppFragment.SettingsImportException;
import org.joinmastodon.android.fragments.settings.SettingsAboutAppFragment.SettingsImportValidator;
import org.joinmastodon.android.fragments.settings.SettingsAboutAppFragment.SettingsSchema;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression tests for Fix 6 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 6)
 * against the pure import validator and export-document builder:
 * MUST R4 F2/S1.3 — long prefs above Integer.MAX_VALUE round-trip exactly (no
 * ClassCastException, no truncation), malformed/array/wrong-type/unknown-enum/
 * out-of-range documents are rejected before any write, and the export document is
 * complete. HARDENING S1#3 — account blocks are keyed by domain_id, with legacy
 * numeric-key fallback for older exports.
 */
public class SettingsImportValidatorTest{
	private static final long OVER_INT_MAX=(long) Integer.MAX_VALUE+1L;

	private static Map<String, Object> mapOf(Object... kv){
		LinkedHashMap<String, Object> m=new LinkedHashMap<>();
		for(int i=0;i<kv.length;i+=2)
			m.put((String) kv[i], kv[i+1]);
		return m;
	}

	private static Map<String, ?> reparse(JsonObject doc, String member){
		Gson gson=new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
		return gson.fromJson(doc.getAsJsonObject(member), Map.class);
	}

	@Test
	public void longPrefAboveIntMaxRoundTripsExactly() throws Exception{
		Map<String, Object> in=mapOf("catchUpLastAt", OVER_INT_MAX);
		LinkedHashMap<String, Object> out=SettingsImportValidator.validate(in, SettingsSchema.ACCOUNT);
		assertEquals(OVER_INT_MAX, out.get("catchUpLastAt"));
		assertTrue(out.get("catchUpLastAt") instanceof Long);
	}

	@Test
	public void longPrefSurvivesSerializeParseValidateRoundTrip() throws Exception{
		LinkedHashMap<String, Map<String, ?>> accounts=new LinkedHashMap<>();
		Map<String, Object> accountPrefs=mapOf("catchUpLastAt", OVER_INT_MAX, "showReplies", false);
		accounts.put(SettingsSchema.accountBlockKey("example.social", "42"), accountPrefs);

		JsonObject doc=SettingsSchema.buildExportDocument("1.3.5", 135,
				mapOf("playGifs", false, "theme", 1), accounts);

		Map<String, ?> global=reparse(doc, "GlobalUserPreferences");
		LinkedHashMap<String, Object> validatedGlobal=SettingsImportValidator.validate(global, SettingsSchema.GLOBAL);
		assertEquals(Boolean.FALSE, validatedGlobal.get("playGifs"));
		assertEquals(1, validatedGlobal.get("theme"));

		JsonObject block=SettingsSchema.findAccountBlock(doc, "example.social", "42");
		assertTrue(block!=null);
		LinkedHashMap<String, Object> validatedAccount=SettingsImportValidator.validate(reparse(doc, SettingsSchema.accountBlockKey("example.social", "42")), SettingsSchema.ACCOUNT);
		assertEquals(OVER_INT_MAX, validatedAccount.get("catchUpLastAt"));
		assertEquals(Boolean.FALSE, validatedAccount.get("showReplies"));
	}

	@Test
	public void intPrefAcceptsIntegralLongWithinRange() throws Exception{
		LinkedHashMap<String, Object> out=SettingsImportValidator.validate(mapOf("theme", 2L), SettingsSchema.GLOBAL);
		assertEquals(2, out.get("theme"));
		assertTrue(out.get("theme") instanceof Integer);
	}

	@Test
	public void intPrefRejectsLongAboveIntMax(){
		try{
			SettingsImportValidator.validate(mapOf("theme", OVER_INT_MAX), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void themeOrdinalOutOfRangeRejected(){
		try{
			SettingsImportValidator.validate(mapOf("theme", 7L), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void wrongTypeRejected(){
		try{
			SettingsImportValidator.validate(mapOf("playGifs", "yes"), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void fractionalNumberRejectedForInt(){
		try{
			SettingsImportValidator.validate(mapOf("theme", 1.5d), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void arrayValueRejected(){
		try{
			SettingsImportValidator.validate(mapOf("playGifs", new ArrayList<>(List.of(true))), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void nestedObjectRejected(){
		try{
			SettingsImportValidator.validate(mapOf("playGifs", new HashMap<>(Map.of("a", 1))), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void unknownEnumValueRejected(){
		try{
			SettingsImportValidator.validate(mapOf("color", "NOT_A_PALETTE"), SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void knownEnumValueAccepted() throws Exception{
		LinkedHashMap<String, Object> out=SettingsImportValidator.validate(mapOf("color", "PURPLE"), SettingsSchema.GLOBAL);
		assertEquals("PURPLE", out.get("color"));
	}

	@Test
	public void unknownKeysAreSkippedNotFatal() throws Exception{
		LinkedHashMap<String, Object> out=SettingsImportValidator.validate(
				mapOf("someFutureKey", true, "playGifs", false), SettingsSchema.GLOBAL);
		assertFalse(out.containsKey("someFutureKey"));
		assertEquals(Boolean.FALSE, out.get("playGifs"));
	}

	@Test
	public void nullValuesAreSkipped() throws Exception{
		LinkedHashMap<String, Object> out=SettingsImportValidator.validate(mapOf("playGifs", null), SettingsSchema.GLOBAL);
		assertTrue(out.isEmpty());
	}

	@Test
	public void missingObjectRejected(){
		try{
			SettingsImportValidator.validate(null, SettingsSchema.GLOBAL);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}

	@Test
	public void accountBlockKeyEmbedsDomain(){
		assertEquals("example.social_42", SettingsSchema.accountBlockKey("example.social", "42"));
	}

	@Test
	public void findAccountBlockPrefersCompositeKey(){
		JsonObject doc=JsonParser.parseString("{\"example.social_42\":{\"showReplies\":false},\"42\":{\"showReplies\":true}}").getAsJsonObject();
		JsonObject block=SettingsSchema.findAccountBlock(doc, "example.social", "42");
		assertTrue(block!=null);
		assertFalse(block.get("showReplies").getAsBoolean());
	}

	@Test
	public void findAccountBlockFallsBackToLegacyNumericKey(){
		JsonObject doc=JsonParser.parseString("{\"42\":{\"showReplies\":true}}").getAsJsonObject();
		JsonObject block=SettingsSchema.findAccountBlock(doc, "example.social", "42");
		assertTrue(block!=null);
		assertTrue(block.get("showReplies").getAsBoolean());
	}

	@Test
	public void findAccountBlockReturnsNullWhenAbsent(){
		JsonObject doc=JsonParser.parseString("{\"other.social_42\":{}}").getAsJsonObject();
		assertNull(SettingsSchema.findAccountBlock(doc, "example.social", "42"));
	}

	@Test
	public void exportDocumentCarriesSchemaVersionAndIdentity(){
		JsonObject doc=SettingsSchema.buildExportDocument("1.3.5", 135, mapOf(), new LinkedHashMap<>());
		assertEquals(SettingsSchema.VERSION, doc.get("schemaVersion").getAsInt());
		assertEquals("1.3.5", doc.get("versionName").getAsString());
		assertEquals(135, doc.get("versionCode").getAsInt());
		assertTrue(doc.has("GlobalUserPreferences"));
	}

	@Test
	public void translationSecretsAndEndpointsExcludedFromExportAndImport() throws Exception{
		Map<String, Object> globalIn=mapOf(
				"playGifs", true,
				"libreTranslateApiKey", "super-secret-key",
				"libreTranslateUrl", "https://evil.example/translate",
				"translationService", "CUSTOM"
		);
		JsonObject doc=SettingsSchema.buildExportDocument("1.3.15", 10315, globalIn, new LinkedHashMap<>());
		JsonObject exportedGlobal=doc.getAsJsonObject("GlobalUserPreferences");
		assertTrue(exportedGlobal.has("playGifs"));
		assertFalse(exportedGlobal.has("libreTranslateApiKey"));
		assertFalse(exportedGlobal.has("libreTranslateUrl"));
		assertFalse(exportedGlobal.has("translationService"));

		LinkedHashMap<String, Object> imported=SettingsImportValidator.validate(globalIn, SettingsSchema.GLOBAL);
		assertEquals(Boolean.TRUE, imported.get("playGifs"));
		assertFalse(imported.containsKey("libreTranslateApiKey"));
		assertFalse(imported.containsKey("libreTranslateUrl"));
		assertFalse(imported.containsKey("translationService"));
	}

	@Test
	public void accountColorRejectsUnknownEnumValue(){
		try{
			SettingsImportValidator.validate(mapOf("color", "INVALID_COLOR"), SettingsSchema.ACCOUNT);
			fail("expected rejection");
		}catch(SettingsImportException expected){
		}
	}
}
