package org.joinmastodon.android.ui.text;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The composer overflowed to the right whenever a mention or hashtag shared a
 * line with typed text, and only visibly so with a bundled family narrower
 * than Roboto. The span restyled the typeface in updateDrawState alone, and
 * StaticLayout measures with MetricAffectingSpans only, so the mention was
 * wrapped at one width and drawn at another. These tests pin the contract
 * that makes the two agree.
 */
public class ComposeSpanTest{
	@Test
	public void mentionSpanIsSeenByTheMeasurer(){
		assertTrue("a CharacterStyle-only span is invisible to line breaking",
				MetricAffectingSpan.class.isAssignableFrom(ComposeHashtagOrMentionSpan.class));
	}

	@Test
	public void mentionSpanAppliesItsFaceAtMeasureTime() throws Exception{
		assertEquals(ComposeHashtagOrMentionSpan.class,
				ComposeHashtagOrMentionSpan.class.getDeclaredMethod("updateMeasureState", TextPaint.class).getDeclaringClass());
	}

	@Test
	public void mentionSpanAppliesItsFaceAtDrawTime() throws Exception{
		assertEquals(ComposeHashtagOrMentionSpan.class,
				ComposeHashtagOrMentionSpan.class.getDeclaredMethod("updateDrawState", TextPaint.class).getDeclaringClass());
	}

	@Test
	public void plainAutocompleteMarkerStaysNeutral() throws Exception{
		TextPaint tp=new TextPaint();
		new ComposeAutocompleteSpan().updateMeasureState(tp);
		new ComposeAutocompleteSpan().updateDrawState(tp);
	}
}
