package org.joinmastodon.android.ui.share;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TruncationChooserTest{

	@Test
	public void allParagraphsFit(){
		List<Integer> heights=Arrays.asList(100, 200, 300);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 8000);
		assertEquals(3, r.paragraphsIncluded);
		assertFalse(r.truncated);
	}

	@Test
	public void exactBudgetFits(){
		List<Integer> heights=Arrays.asList(4000, 4000);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 8000);
		assertEquals(2, r.paragraphsIncluded);
		assertFalse(r.truncated);
	}

	@Test
	public void oneOverBudgetTruncates(){
		List<Integer> heights=Arrays.asList(4000, 4000, 10);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 8000);
		assertEquals(2, r.paragraphsIncluded);
		assertTrue(r.truncated);
	}

	@Test
	public void largestPrefixThatFits(){
		List<Integer> heights=Arrays.asList(1000, 5000, 1000);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 3000);
		assertEquals(1, r.paragraphsIncluded);
		assertTrue(r.truncated);
	}

	@Test
	public void firstParagraphAloneTooBig(){
		List<Integer> heights=Arrays.asList(9000, 100);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 8000);
		assertEquals(0, r.paragraphsIncluded);
		assertTrue(r.truncated);
	}

	@Test
	public void emptyParagraphList(){
		TruncationChooser.Result r=TruncationChooser.choose(Collections.emptyList(), 8000);
		assertEquals(0, r.paragraphsIncluded);
		assertFalse(r.truncated);
	}

	@Test
	public void gapCountsTowardBudget(){
		List<Integer> heights=Arrays.asList(3000, 3000);
		// Without the 500px gap both fit (6000 ≤ 8000); with it they don't (6500... still fit).
		TruncationChooser.Result fits=TruncationChooser.choose(heights, 2000, 8000);
		assertEquals(2, fits.paragraphsIncluded);
		// 3000 + 4000 gap + 3000 = 10000 > 8000 → only the first fits.
		TruncationChooser.Result tight=TruncationChooser.choose(heights, 4000, 8000);
		assertEquals(1, tight.paragraphsIncluded);
		assertTrue(tight.truncated);
		// Zero-height paragraphs only pay the gap after the first.
		TruncationChooser.Result zeroes=TruncationChooser.choose(Arrays.asList(0, 0, 0), 10, 25);
		assertEquals(3, zeroes.paragraphsIncluded);
	}

	@Test
	public void prefixSumIsMonotonic(){
		List<Integer> heights=Arrays.asList(700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700);
		TruncationChooser.Result r=TruncationChooser.choose(heights, 8000);
		assertEquals(11, r.paragraphsIncluded);
		assertTrue(r.truncated);
	}
}
