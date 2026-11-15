package org.joinmastodon.android.ui.viewcontrollers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.ui.viewcontrollers.ComposePollViewController.PollValidator;
import org.joinmastodon.android.ui.viewcontrollers.ComposePollViewController.PollValidator.Issue;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Regression tests for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3, R1 F9): one poll-validity rule — trimmed, unique, grapheme max —
 * gates publish, schedule, and the request body.
 */
public class ComposePollValidatorTest{
	private static final int MAX_OPTIONS=4;
	private static final int MAX_CHARS=50;

	private static PollValidator validate(List<String> options){
		return PollValidator.validate(options, MAX_OPTIONS, MAX_CHARS);
	}

	@Test
	public void twoPlainOptionsAreValid(){
		assertTrue(validate(Arrays.asList("Yes", "No")).isValid());
	}

	@Test
	public void duplicateOptionsAreRejected(){
		PollValidator v=validate(Arrays.asList("Same", "Same"));
		assertFalse(v.isValid());
		assertEquals(Issue.DUPLICATE_OPTION, v.issue);
		assertEquals(1, v.optionIndex);
	}

	@Test
	public void duplicatesAreDetectedAfterTrimming(){
		PollValidator v=validate(Arrays.asList("Yes", "  Yes  "));
		assertFalse(v.isValid());
		assertEquals(Issue.DUPLICATE_OPTION, v.issue);
		assertEquals(1, v.optionIndex);
	}

	@Test
	public void blankOnlyPollIsRejected(){
		PollValidator v=validate(Arrays.asList("", "   "));
		assertFalse(v.isValid());
		assertEquals(Issue.TOO_FEW_OPTIONS, v.issue);
	}

	@Test
	public void singleFilledOptionIsRejected(){
		PollValidator v=validate(Arrays.asList("Only", ""));
		assertFalse(v.isValid());
		assertEquals(Issue.TOO_FEW_OPTIONS, v.issue);
	}

	@Test
	public void blankOptionBetweenFilledOnesIsIgnored(){
		assertTrue(validate(Arrays.asList("One", "", "Two")).isValid());
	}

	@Test
	public void fiftyOneGraphemeOptionIsRejected(){
		// 51 astral emoji are 102 UTF-16 units but 51 graphemes — the old
		// length() rule let this through at exactly the boundary.
		String longOption="\uD83D\uDE00".repeat(51);
		PollValidator v=validate(Arrays.asList(longOption, "No"));
		assertFalse(v.isValid());
		assertEquals(Issue.OPTION_TOO_LONG, v.issue);
		assertEquals(0, v.optionIndex);
	}

	@Test
	public void exactlyMaxGraphemesIsAccepted(){
		String edge="\uD83D\uDE00".repeat(50);
		assertTrue(validate(Arrays.asList(edge, "No")).isValid());
	}

	@Test
	public void moreThanMaxOptionsIsRejected(){
		PollValidator v=validate(Arrays.asList("1", "2", "3", "4", "5"));
		assertFalse(v.isValid());
		assertEquals(Issue.TOO_MANY_OPTIONS, v.issue);
	}

	@Test
	public void emptyPollIsNotAnError(){
		assertTrue(validate(Collections.emptyList()).isValid());
	}
}
