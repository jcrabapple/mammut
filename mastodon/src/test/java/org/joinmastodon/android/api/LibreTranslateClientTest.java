package org.joinmastodon.android.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.model.Translation;
import org.junit.Test;

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
	public void plainHttpLocalEndpointAllowed(){
		assertEquals("http://localhost:5000/translate", LibreTranslateClient.normalizeEndpoint("http://localhost:5000"));
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
}