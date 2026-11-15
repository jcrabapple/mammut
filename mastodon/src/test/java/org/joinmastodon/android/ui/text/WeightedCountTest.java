package org.joinmastodon.android.ui.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.twitter.twittertext.TwitterTextEmojiRegex;

import org.joinmastodon.android.ui.text.LengthLimitHighlighter.WeightedCount;
import org.junit.Test;

import java.util.regex.Pattern;

/**
 * Regression tests for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3, R1 F3/F8): the composer's over-limit highlight and counters must
 * map the weighted count back to RAW offsets through grapheme boundaries —
 * never raw length arithmetic, which cut astral emoji in half and let a
 * combined body+CW count index negatively into the body's range.
 *
 * <p>The mention pattern literal is copied from ComposeFragment's private
 * constant so this test exercises the same matching the composer uses.</p>
 */
public class WeightedCountTest{
	private static final Pattern MENTION_PATTERN=Pattern.compile("(^|[^\\/\\w])@(([a-z0-9_]+)@[a-z0-9\\.\\-]+[a-z0-9]+)", Pattern.CASE_INSENSITIVE);

	private static WeightedCount weighted(String raw){
		return WeightedCount.weighted(raw, HtmlParser.URL_PATTERN, MENTION_PATTERN, TwitterTextEmojiRegex.VALID_EMOJI_PATTERN);
	}

	// --- plain grapheme counting (CW fields, R1 F8) ---

	@Test
	public void asciiTextCountsGraphemes(){
		WeightedCount w=WeightedCount.plain("hello world");
		assertEquals(11, w.graphemes);
		assertEquals(11, WeightedCount.plainGraphemes("hello world"));
	}

	@Test
	public void astralEmojiCountsAsOneGrapheme(){
		// Two UTF-16 units, one grapheme — the F8 fix.
		assertEquals(3, WeightedCount.plainGraphemes("a\uD83D\uDE00b"));
		assertEquals(1, WeightedCount.plainGraphemes("\uD83D\uDE00"));
	}

	@Test
	public void emptyTextHasNoOverLimitStart(){
		WeightedCount w=WeightedCount.plain("");
		assertEquals(-1, w.overLimitStart(0));
		assertEquals(-1, w.overLimitStart(500));
	}

	// --- weighted→raw offset mapping (R1 F3) ---

	@Test
	public void plainMappingIsIdentityForAscii(){
		WeightedCount w=WeightedCount.plain("abcdef");
		assertEquals(-1, w.overLimitStart(6));
		assertEquals(4, w.overLimitStart(4));
	}

	@Test
	public void astralEmojiBoundaryNeverSplitsASurrogatePair(){
		WeightedCount w=WeightedCount.plain("a\uD83D\uDE00b");
		// graphemes: a | 😀 | b — raw offsets at their starts: 0, 1, 3.
		assertEquals(3, w.graphemes);
		assertEquals(3, w.overLimitStart(2));
		// Limit 1: the over-limit region begins at the emoji (raw 1) and
		// covers both of its UTF-16 units.
		assertEquals(1, w.overLimitStart(1));
	}

	@Test
	public void astralCwBoundaryAt51Graphemes(){
		String cw="\uD83D\uDE00".repeat(51);
		WeightedCount w=WeightedCount.plain(cw);
		assertEquals(51, w.graphemes);
		// The 51st emoji starts at raw offset 100; a UTF-16-length rule
		// would have answered 50, cutting the emoji in half.
		assertEquals(100, w.overLimitStart(50));
	}

	@Test
	public void emptyBodyWithLimitPlusOneCwNeverYieldsNegativeSpanStart(){
		// The audit crash: empty body, CW at limit+1, old code computed
		// text.length()-(count-limit) = 0-1 = -1 for the BODY range.
		int limit=500;
		WeightedCount body=WeightedCount.plain("");
		String cwText="x".repeat(limit+1);
		WeightedCount cw=WeightedCount.plain(cwText);
		int bodyStart=body.overLimitStart(limit);
		assertEquals(-1, bodyStart);
		int total=body.graphemes+cw.graphemes;
		assertTrue(total>limit);
		int over=total-limit;
		// Exactly the arithmetic highlightCwTail performs:
		int cwStart=over>=cw.graphemes ? 0 : cw.rawOffsetAt(cw.graphemes-over);
		assertTrue("CW span start must never be negative: "+cwStart, cwStart>=0);
		assertTrue(cwStart<cwText.length());
	}

	// --- weighted parity with the old countTextChars pipeline ---

	@Test
	public void urlCountsAs23AndMapsCutsPastItsEnd(){
		WeightedCount w=weighted("check https://example.com now");
		// "check " (6) + URL weight 23 + " now" (4) — the URL's preceding
		// space is consumed as part of the match, so the space after it
		// remains.
		assertEquals(6+23+4, w.graphemes);
		// A limit cutting inside the URL's 23 units must start the span
		// just past the URL, never mid-URL.
		assertEquals("check https://example.com".length(), w.overLimitStart(10));
	}

	@Test
	public void mentionCountsAsLocalPartOnly(){
		WeightedCount w=weighted("hi @alice@example.com!");
		// "hi" (2) + boundary space (1) + "@alice" (6) + "!" (1)
		assertEquals(10, w.graphemes);
		assertEquals(8, w.overLimitStart(8));
	}

	@Test
	public void emojiInBodyCountsAsOneWeightedUnit(){
		WeightedCount w=weighted("hello \uD83D\uDE00 world");
		assertEquals(13, w.graphemes);
		// The grapheme after the emoji begins at raw offset 8 (the space
		// following the two-unit emoji) — identity arithmetic would say 7.
		assertEquals(8, w.overLimitStart(7));
	}

	@Test
	public void plainTextHasNoWeightedReplacements(){
		WeightedCount weighted=weighted("plain old sentence");
		assertEquals(WeightedCount.plain("plain old sentence").graphemes, weighted.graphemes);
	}

	@Test
	public void offsetsAreMonotonicAndInRange(){
		String raw="mix https://a.b \uD83D\uDE00 @x@y.z end";
		WeightedCount w=weighted(raw);
		int prev=0;
		for(int k=0;k<=w.graphemes;k++){
			int off=w.rawOffsetAt(k);
			assertTrue("offset "+off+" at grapheme "+k+" before previous "+prev, off>=prev);
			assertTrue(off>=0 && off<=raw.length());
			prev=off;
		}
	}
}
