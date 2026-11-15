package org.joinmastodon.android;

import org.joinmastodon.android.GlobalUserPreferences.AutoRevealMode;
import org.joinmastodon.android.GlobalUserPreferences.PrefixRepliesMode;
import org.joinmastodon.android.api.session.AccountLocalPreferences.ColorPreference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Regression tests for Fix 6 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 6),
 * MUST R4 F2/S1.3: a corrupt stored preference value must resolve to its documented
 * default instead of crashing load() (the persistent startup crash loop).
 */
public class GlobalUserPreferencesFallbackTest{
	@Test
	public void enumOrReturnsParsedValueForValidName(){
		assertEquals(PrefixRepliesMode.ALWAYS, GlobalUserPreferences.enumOr(PrefixRepliesMode.class, "ALWAYS", PrefixRepliesMode.NEVER));
	}

	@Test
	public void enumOrFallsBackForUnknownName(){
		// pre-fix valueOf threw IllegalArgumentException here, killing load()
		assertEquals(AutoRevealMode.THREADS, GlobalUserPreferences.enumOr(AutoRevealMode.class, "CORRUPTED", AutoRevealMode.THREADS));
	}

	@Test
	public void enumOrFallsBackForNullName(){
		assertEquals(ColorPreference.MAMMUT, GlobalUserPreferences.enumOr(ColorPreference.class, null, ColorPreference.MAMMUT));
	}

	@Test
	public void enumValueReturnsNullInsteadOfThrowing(){
		assertNull(GlobalUserPreferences.enumValue(ColorPreference.class, "NOPE"));
	}

	@Test
	public void ordinalOrPassesThroughInRangeValues(){
		assertEquals(1, GlobalUserPreferences.ordinalOr(1, 3));
	}

	@Test
	public void ordinalOrClampsNegativeOrdinal(){
		assertEquals(0, GlobalUserPreferences.ordinalOr(-3, 3));
	}

	@Test
	public void ordinalOrClampsAboveRangeOrdinal(){
		// pre-fix ThemePreference.values()[99] threw ArrayIndexOutOfBoundsException on startup
		assertEquals(0, GlobalUserPreferences.ordinalOr(99, 3));
	}

	@Test
	public void ordinalOrGuardsDegenerateEnumSize(){
		assertEquals(0, GlobalUserPreferences.ordinalOr(1, 0));
	}
}
