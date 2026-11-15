package org.joinmastodon.android.ui.utils;

import org.joinmastodon.android.api.session.AccountLocalPreferences.ColorPreference;
import org.joinmastodon.android.ui.utils.UiUtils.PaletteFallback;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for Fix 5 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 5),
 * SHOULD R4 F12: compose surfaces that open without a session (external share,
 * account chooser) resolve the palette as account override, then the reader's global
 * palette preference, then the MAMMUT default — the pre-fix null-session branch
 * hard-coded MAMMUT and ignored the global choice.
 */
public class PaletteFallbackTest{

	@Test
	public void accountColorWinsOverGlobal(){
		assertEquals(ColorPreference.BLUE, PaletteFallback.resolve(ColorPreference.BLUE, ColorPreference.PINK));
	}

	@Test
	public void globalPaletteUsedWhenNoAccountOverride(){
		assertEquals(ColorPreference.PINK, PaletteFallback.resolve(null, ColorPreference.PINK));
		assertEquals(ColorPreference.NORD, PaletteFallback.resolve(null, ColorPreference.NORD));
	}

	@Test
	public void mammutDefaultWhenNeitherExists(){
		assertEquals(ColorPreference.MAMMUT, PaletteFallback.resolve(null, null));
	}
}
