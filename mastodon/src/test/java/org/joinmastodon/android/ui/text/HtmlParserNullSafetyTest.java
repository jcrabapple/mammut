package org.joinmastodon.android.ui.text;

import org.joinmastodon.android.model.Mention;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for HtmlParser null safety — specifically CustomLocalTimelineFragment
 * setting mention.id = null on remote posts (which previously threw NPE in Collectors.toMap).
 */
public class HtmlParserNullSafetyTest{

	@Test
	public void remoteMentionsWithNullIdDoNotThrow(){
		Mention remote=new Mention();
		remote.id=null;
		remote.url="https://remote.example/@alice";
		remote.username="alice";

		Mention local=new Mention();
		local.id="42";
		local.url="https://local.example/@bob";
		local.username="bob";

		Map<String, String> byUrl=HtmlParser.buildMentionIdsByUrl(Arrays.asList(remote, local, null));
		assertEquals(1, byUrl.size());
		assertEquals("42", byUrl.get("https://local.example/@bob"));

		Map<String, Mention> byId=HtmlParser.buildMentionsById(Arrays.asList(remote, local, null));
		assertEquals(1, byId.size());
		assertEquals(local, byId.get("42"));
	}

	@Test
	public void nullOrEmptyMentionsReturnEmptyMaps(){
		assertTrue(HtmlParser.buildMentionIdsByUrl(null).isEmpty());
		assertTrue(HtmlParser.buildMentionIdsByUrl(Collections.emptyList()).isEmpty());
		assertTrue(HtmlParser.buildMentionsById(null).isEmpty());
	}

	@Test
	public void stripHandlesNullSafely(){
		assertEquals("", HtmlParser.strip(null));
		assertEquals("", HtmlParser.stripAndRemoveInvisibleSpans(null));
		assertEquals("Hello world", HtmlParser.strip("<p>Hello <b>world</b></p>"));
	}
}
