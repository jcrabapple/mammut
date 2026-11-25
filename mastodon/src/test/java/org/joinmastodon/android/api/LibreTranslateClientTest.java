package org.joinmastodon.android.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.model.Translation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Mammut's universal-translation fallback (LibreTranslate).
 * The endpoint normalization, request building and response parsing are pure seams;
 * these pin the contract the network path and the settings UI rely on.
 */
public class LibreTranslateClientTest{

	// --- normalizeEndpoint / isConfigured ---

	@Test
	public void nullUrlIsNotConfigured(){
		assertNull(LibreTranslateClient.normalizeEndpoint(null));
		assertFalse(LibreTranslateClient.isConfigured(null));
	}

	@Test
	public void emptyOrBlankUrlIsNotConfigured(){
		assertNull(LibreTranslateClient.normalizeEndpoint(""));
		assertNull(LibreTranslateClient.normalizeEndpoint("   "));
		assertFalse(LibreTranslateClient.isConfigured(""));
	}

	@Test
	public void nonHttpSchemeRejected(){
		assertNull(LibreTranslateClient.normalizeEndpoint("ftp://example.com"));
		assertNull(LibreTranslateClient.normalizeEndpoint("example.com"));
		assertNull(LibreTranslateClient.normalizeEndpoint("libretranslate.example.com"));
	}

	@Test
	public void schemeOnlyRejected(){
		assertNull(LibreTranslateClient.normalizeEndpoint("https://"));
		assertNull(LibreTranslateClient.normalizeEndpoint("http://"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https:///"));
	}

	@Test
	public void baseUrlGetsTranslatePath(){
		assertEquals("https://lt.example.com/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com"));
	}

	@Test
	public void trailingSlashesStripped(){
		assertEquals("https://lt.example.com/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com///"));
	}

	@Test
	public void fullTranslateUrlPreserved(){
		assertEquals("https://lt.example.com/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com/translate"));
		assertEquals("https://lt.example.com/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com/translate/"));
	}

	@Test
	public void whitespaceTrimmedAndConfigured(){
		assertEquals("https://lt.example.com/translate", LibreTranslateClient.normalizeEndpoint("  https://lt.example.com  "));
		assertTrue(LibreTranslateClient.isConfigured("https://lt.example.com"));
	}

	@Test
	public void plainHttpEndpointsRejected(){
		assertNull(LibreTranslateClient.normalizeEndpoint("http://localhost:5000"));
		assertNull(LibreTranslateClient.normalizeEndpoint("http://lt.example.com"));
		assertFalse(LibreTranslateClient.isConfigured("http://lt.example.com"));
	}

	@Test
	public void malformedOrUnsafeHttpsUrlsRejected(){
		assertNull(LibreTranslateClient.normalizeEndpoint("https://my server.com"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https://host:abc"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https://[bad"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https://user:pass@lt.example.com"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https://lt.example.com?foo=bar"));
		assertNull(LibreTranslateClient.normalizeEndpoint("https://lt.example.com#frag"));
	}

	@Test
	public void subpathPreservedWhenAppendingTranslate(){
		assertEquals("https://lt.example.com/api/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com/api"));
		assertEquals("https://lt.example.com/api/translate", LibreTranslateClient.normalizeEndpoint("https://lt.example.com/api/translate/"));
	}

	@Test
	public void readCappedAllowsWithinLimitAndRejectsOversize() throws IOException{
		String ok=LibreTranslateClient.readCapped(new ByteArrayInputStream("{\"translatedText\":\"ok\"}".getBytes(StandardCharsets.UTF_8)), 64);
		assertEquals("{\"translatedText\":\"ok\"}", ok);
		boolean threw=false;
		try{
			LibreTranslateClient.readCapped(new ByteArrayInputStream(new byte[65]), 64);
		}catch(IOException expected){
			threw=true;
		}
		assertTrue(threw);
	}

	// --- buildPayload ---

	@Test
	public void payloadHasHtmlFormatAndAllFields(){
		JsonObject o=JsonParser.parseString(LibreTranslateClient.buildPayload("<p>Hello</p>", "en", "fr", "secret")).getAsJsonObject();
		assertEquals("<p>Hello</p>", o.get("q").getAsString());
		assertEquals("en", o.get("source").getAsString());
		assertEquals("fr", o.get("target").getAsString());
		assertEquals("html", o.get("format").getAsString());
		assertEquals("secret", o.get("api_key").getAsString());
	}

	@Test
	public void emptySourceBecomesAutoAndNoKeyOmitted(){
		JsonObject o=JsonParser.parseString(LibreTranslateClient.buildPayload("hi", "", "de", null)).getAsJsonObject();
		assertEquals("auto", o.get("source").getAsString());
		assertFalse(o.has("api_key"));
	}

	@Test
	public void nullSourceBecomesAutoAndNullContentEmpty(){
		JsonObject o=JsonParser.parseString(LibreTranslateClient.buildPayload(null, null, "es", "")).getAsJsonObject();
		assertEquals("auto", o.get("source").getAsString());
		assertEquals("", o.get("q").getAsString());
		assertFalse(o.has("api_key"));
	}

	// --- parseTranslation ---

	@Test
	public void parsesTranslatedTextAndDetectedLanguage(){
		Translation t=LibreTranslateClient.parseTranslation(
				"{\"translatedText\":\"<p>Bonjour</p>\",\"detectedLanguage\":{\"language\":\"en\",\"confidence\":0.9}}", "fr");
		assertEquals("<p>Bonjour</p>", t.content);
		assertEquals("LibreTranslate", t.provider);
		assertEquals("en", t.detectedSourceLanguage);
	}

	@Test
	public void missingDetectedLanguageFallsBackToSource(){
		Translation t=LibreTranslateClient.parseTranslation("{\"translatedText\":\"Hola\"}", "en");
		assertEquals("en", t.detectedSourceLanguage);
	}

	@Test
	public void missingDetectedLanguageAndNullSourceFallsBackToUnd(){
		// detectedSourceLanguage must never be null: the footer runs it through Locale.forLanguageTag
		Translation t=LibreTranslateClient.parseTranslation("{\"translatedText\":\"Hola\"}", null);
		assertNotNull(t.detectedSourceLanguage);
		assertEquals("und", t.detectedSourceLanguage);
	}

	@Test(expected=IllegalArgumentException.class)
	public void missingTranslatedTextThrows(){
		LibreTranslateClient.parseTranslation("{\"error\":\"bad\"}", "en");
	}

	@Test(expected=IllegalArgumentException.class)
	public void nullTranslatedTextThrows(){
		LibreTranslateClient.parseTranslation("{\"translatedText\":null}", "en");
	}

	// --- translation service mode resolution (Mammut Translate / Custom / Off) ---

	@Test
	public void mammutModeUsesBuiltInEndpoint(){
		assertEquals(LibreTranslateClient.MAMMUT_TRANSLATE_URL,
				LibreTranslateClient.effectiveUrl(GlobalUserPreferences.TranslationService.MAMMUT, ""));
		// the built-in default works even with no custom URL configured
		assertTrue(LibreTranslateClient.isFallbackEnabled(GlobalUserPreferences.TranslationService.MAMMUT, ""));
		// Mammut Translate needs no API key, even if a custom one is stored
		assertNull(LibreTranslateClient.effectiveApiKey(GlobalUserPreferences.TranslationService.MAMMUT, "stored-key"));
	}

	@Test
	public void builtInEndpointIsAValidUrl(){
		assertTrue(LibreTranslateClient.isConfigured(LibreTranslateClient.MAMMUT_TRANSLATE_URL));
	}

	@Test
	public void customModeUsesNormalizedUrlAndKey(){
		assertEquals("https://lt.example.com/translate",
				LibreTranslateClient.effectiveUrl(GlobalUserPreferences.TranslationService.CUSTOM, "https://lt.example.com"));
		assertEquals("stored-key",
				LibreTranslateClient.effectiveApiKey(GlobalUserPreferences.TranslationService.CUSTOM, "stored-key"));
		assertTrue(LibreTranslateClient.isFallbackEnabled(GlobalUserPreferences.TranslationService.CUSTOM, "https://lt.example.com"));
	}

	@Test
	public void customModeWithInvalidUrlDisablesFallback(){
		assertNull(LibreTranslateClient.effectiveUrl(GlobalUserPreferences.TranslationService.CUSTOM, ""));
		assertNull(LibreTranslateClient.effectiveUrl(GlobalUserPreferences.TranslationService.CUSTOM, "not a url"));
		assertFalse(LibreTranslateClient.isFallbackEnabled(GlobalUserPreferences.TranslationService.CUSTOM, ""));
	}

	@Test
	public void offModeDisablesFallbackEvenWithAUrl(){
		assertNull(LibreTranslateClient.effectiveUrl(GlobalUserPreferences.TranslationService.OFF, "https://lt.example.com"));
		assertFalse(LibreTranslateClient.isFallbackEnabled(GlobalUserPreferences.TranslationService.OFF, "https://lt.example.com"));
		assertNull(LibreTranslateClient.effectiveApiKey(GlobalUserPreferences.TranslationService.OFF, "stored-key"));
	}
}