package org.joinmastodon.android.ui.text;

import android.graphics.Typeface;
import android.text.TextPaint;

import org.joinmastodon.android.GlobalUserPreferences;

public class ComposeHashtagOrMentionSpan extends ComposeAutocompleteSpan{
	// Lazy: a static Typeface.create would run at class load and make the span
	// unloadable in JVM unit tests, which is exactly where the wrapping contract
	// below is pinned.
	private static Typeface platformMedium;

	@Override
	public void updateDrawState(TextPaint tp){
		tp.setColor(tp.linkColor);
		tp.setTypeface(mediumFor(tp));
	}

	@Override
	public void updateMeasureState(TextPaint tp){
		tp.setTypeface(mediumFor(tp));
	}

	/**
	 * The same face for measurement and for drawing, always. This used to set
	 * sans-serif-medium in updateDrawState only, so the mention was wrapped as
	 * though it were set in the field's own face and then drawn wider: with a
	 * bundled family narrower than Roboto (Atkinson especially) the line ran
	 * past the right edge and the last glyph was clipped off the screen.
	 * Medium of the active family, falling back to the platform medium.
	 */
	private static Typeface mediumFor(TextPaint tp){
		Typeface bundled=MammutTypeface.get(GlobalUserPreferences.fontFamily, 500, false);
		if(bundled!=null)
			return bundled;
		if(platformMedium==null)
			platformMedium=Typeface.create("sans-serif-medium", 0);
		return platformMedium;
	}
}
