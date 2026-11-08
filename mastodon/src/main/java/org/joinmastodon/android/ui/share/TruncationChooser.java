package org.joinmastodon.android.ui.share;

import java.util.List;

/**
 * Pure chooser for the 8000 px card-height cap. Given the measured heights of the
 * post's paragraphs and the vertical budget left for the body, it returns the
 * largest prefix of paragraphs that fits. No Android types, so it is unit-testable
 * on the JVM.
 */
public class TruncationChooser{
	public static final int DEFAULT_BUDGET=8000;

	public static class Result{
		/** Number of paragraphs from the start of the post that fit the budget. */
		public final int paragraphsIncluded;
		/** True when at least one paragraph was left out. */
		public final boolean truncated;

		public Result(int paragraphsIncluded, boolean truncated){
			this.paragraphsIncluded=paragraphsIncluded;
			this.truncated=truncated;
		}
	}

	/**
	 * @param paragraphHeights measured height of each paragraph, in order
	 * @param paragraphGap vertical space between two consecutive paragraphs (0 when
	 *                     the heights already include it)
	 * @param budget vertical space available for the paragraphs
	 */
	public static Result choose(List<Integer> paragraphHeights, int paragraphGap, int budget){
		int used=0, included=0;
		for(int height:paragraphHeights){
			int extra=(included>0 ? paragraphGap : 0)+height;
			if(used+extra>budget)
				break;
			used+=extra;
			included++;
		}
		return new Result(included, included<paragraphHeights.size());
	}

	/**
	 * Convenience overload with no inter-paragraph gap.
	 */
	public static Result choose(List<Integer> paragraphHeights, int budget){
		return choose(paragraphHeights, 0, budget);
	}
}
