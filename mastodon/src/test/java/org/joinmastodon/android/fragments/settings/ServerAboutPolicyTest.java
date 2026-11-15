package org.joinmastodon.android.fragments.settings;

import org.joinmastodon.android.fragments.settings.SettingsServerAboutFragment.ServerAboutPolicy;
import org.joinmastodon.android.fragments.settings.SettingsServerAboutFragment.ServerAboutPolicy.NavAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 6 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 6),
 * LOW S1.5: the server-about WebView disables JS, only https/http/mailto schemes are
 * ever dispatched, and instance-controlled HTML is sanitized before rendering.
 */
public class ServerAboutPolicyTest{
	@Test
	public void httpsOpensExternalBrowser(){
		assertEquals(NavAction.OPEN_BROWSER, ServerAboutPolicy.actionFor("https"));
	}

	@Test
	public void httpOpensExternalBrowser(){
		assertEquals(NavAction.OPEN_BROWSER, ServerAboutPolicy.actionFor("http"));
	}

	@Test
	public void mailtoMayBeDispatched(){
		assertEquals(NavAction.DISPATCH, ServerAboutPolicy.actionFor("mailto"));
	}

	@Test
	public void javascriptSchemeIsNeverDispatched(){
		assertEquals(NavAction.IGNORE, ServerAboutPolicy.actionFor("javascript"));
	}

	@Test
	public void telSchemeIsNeverDispatched(){
		assertEquals(NavAction.IGNORE, ServerAboutPolicy.actionFor("tel"));
	}

	@Test
	public void fileSchemeIsNeverDispatched(){
		assertEquals(NavAction.IGNORE, ServerAboutPolicy.actionFor("file"));
	}

	@Test
	public void intentSchemeIsNeverDispatched(){
		assertEquals(NavAction.IGNORE, ServerAboutPolicy.actionFor("intent"));
	}

	@Test
	public void nullSchemeIsNeverDispatched(){
		assertEquals(NavAction.IGNORE, ServerAboutPolicy.actionFor(null));
	}

	@Test
	public void schemeMatchIsCaseInsensitive(){
		assertEquals(NavAction.OPEN_BROWSER, ServerAboutPolicy.actionFor("HTTPS"));
	}

	@Test
	public void sanitizeStripsScriptBlocks(){
		String out=ServerAboutPolicy.sanitizeHtml("<p>hi</p><script>alert(1)</script>");
		assertFalse(out.contains("<script"));
		assertFalse(out.contains("alert"));
		assertTrue(out.contains("<p>hi</p>"));
	}

	@Test
	public void sanitizeStripsScriptTagsWithoutClose(){
		String out=ServerAboutPolicy.sanitizeHtml("<script src=\"https://evil.example/x.js\">");
		assertFalse(out.toLowerCase().contains("script"));
	}

	@Test
	public void sanitizeStripsInlineEventHandlers(){
		String out=ServerAboutPolicy.sanitizeHtml("<img src=\"https://x/y.png\" onerror=\"alert(1)\">");
		assertFalse(out.contains("onerror"));
	}

	@Test
	public void sanitizeNeutralizesJavascriptUrls(){
		String out=ServerAboutPolicy.sanitizeHtml("<a href=\"javascript:alert(1)\">click</a>");
		assertFalse(out.contains("javascript:"));
	}

	@Test
	public void sanitizeStripsIframesAndObjects(){
		String out=ServerAboutPolicy.sanitizeHtml("<iframe src=\"https://evil\"></iframe><object data=\"x\"></object><p>ok</p>");
		assertFalse(out.contains("<iframe"));
		assertFalse(out.contains("<object"));
		assertTrue(out.contains("<p>ok</p>"));
	}

	@Test
	public void sanitizeHandlesNull(){
		assertEquals("", ServerAboutPolicy.sanitizeHtml(null));
	}

	@Test
	public void sanitizeKeepsBenignMarkup(){
		String html="<h2>Rules</h2><p>No <b>spam</b>, <a href=\"https://example.social/about\">details</a></p><ul><li>one</li></ul>";
		assertEquals(html, ServerAboutPolicy.sanitizeHtml(html));
	}
}
