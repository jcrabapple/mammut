package org.joinmastodon.android.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.fragments.ComposeFragment.DraftPolicy;
import org.junit.Test;

/**
 * Regression test for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3, R1 F7): the main surface's CW state makes the draft real — a
 * CW-only composer autosaves and is offered on discard.
 */
public class ComposeDraftPolicyTest{

	@Test
	public void cwOnlyComposerHoldsADraft(){
		assertTrue(DraftPolicy.mainSurfaceHasDraft("", "", false, false, true));
	}

	@Test
	public void emptyUntouchedComposerHoldsNothing(){
		assertFalse(DraftPolicy.mainSurfaceHasDraft("", "", false, false, false));
	}

	@Test
	public void prefilledTextLeftAloneHoldsNothing(){
		// Share-intent prefilled text the author never touched is not a
		// draft (it would resurrect itself forever otherwise).
		assertFalse(DraftPolicy.mainSurfaceHasDraft("hello", "hello", false, false, false));
	}

	@Test
	public void editedTextHoldsADraft(){
		assertTrue(DraftPolicy.mainSurfaceHasDraft("hello!", "hello", false, false, false));
	}

	@Test
	public void mediaOrPollContentHoldsADraft(){
		assertTrue(DraftPolicy.mainSurfaceHasDraft("", "", true, false, false));
		assertTrue(DraftPolicy.mainSurfaceHasDraft("", "", false, true, false));
	}
}
