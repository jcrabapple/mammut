package org.joinmastodon.android.ui.text;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/**
 * Marker for the composer's autocomplete ranges. MetricAffectingSpan rather
 * than CharacterStyle so subclasses that restyle text are seen by the
 * measurer too: StaticLayout only applies MetricAffectingSpans when it works
 * out line widths, and a CharacterStyle that changes the typeface changes the
 * drawing without changing the wrapping.
 */
public class ComposeAutocompleteSpan extends MetricAffectingSpan{
	@Override
	public void updateDrawState(TextPaint tp){
	}

	@Override
	public void updateMeasureState(TextPaint tp){
	}
}
