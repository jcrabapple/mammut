package org.joinmastodon.android.ui.share;

/**
 * Pure rules for the poll block on a share card (spec §11). No Android types,
 * so the percentage-to-bar geometry and the footer line are unit-testable on
 * the JVM.
 *
 * Percentages follow the timeline's own poll math ({@code PollOptionStatusDisplayItem}):
 * the base is {@code votersCount>0 ? votersCount : votesCount} and a leader is any
 * option whose vote count equals the maximum, so a tie has more than one leader.
 * The bar length is always derived from the same rounded percentage the label
 * prints — both come from {@link #percentage(int, int)} via the renderer.
 */
public class PollBlock{
	/** Bar height in card pixels (spec §11). */
	public static final int BAR_HEIGHT=12;

	/** Rounded percentage of one option; 0 when there is nothing to divide by. */
	public static int percentage(int optionVotes, int totalVotes){
		if(totalVotes<=0)
			return 0;
		return Math.round(optionVotes*100f/totalVotes);
	}

	public static int[] percentages(int[] optionVotes, int totalVotes){
		int[] pcts=new int[optionVotes.length];
		for(int i=0;i<optionVotes.length;i++)
			pcts[i]=percentage(optionVotes[i], totalVotes);
		return pcts;
	}

	/**
	 * Leader colouring: every option tied at the maximum is a leader (timeline
	 * precedent). An option is never a leader in an empty poll.
	 */
	public static boolean isLeader(int index, int[] optionVotes){
		if(index<0 || index>=optionVotes.length)
			return false;
		int max=0;
		for(int v:optionVotes)
			max=Math.max(max, v);
		return max>0 && optionVotes[index]==max;
	}

	/**
	 * Bar fill width for a rounded percentage on the given track. The same value
	 * the label prints; 0% draws no bar at all.
	 */
	public static int barWidth(int percentage, int trackWidth){
		if(percentage<=0 || trackWidth<=0)
			return 0;
		return Math.round(trackWidth*Math.min(100, percentage)/100f);
	}

	/**
	 * The drawing model for one option's bar: the rounded percentage, the fill
	 * width it maps to, whether the option is a leader, and whether a full-width
	 * empty track is drawn behind the fill. The renderer turns these into
	 * colours; keeping the geometry here makes the M1 invariant — a non-leader
	 * fill is never described as full-width — unit-testable.
	 */
	public static final class Bar{
		public final int percentage;
		public final int fillWidth;
		public final boolean leader;
		/** A full-width rounded track is drawn behind every option's fill. */
		public final boolean track;

		private Bar(int percentage, int fillWidth, boolean leader, boolean track){
			this.percentage=percentage;
			this.fillWidth=fillWidth;
			this.leader=leader;
			this.track=track;
		}
	}

	/**
	 * Per-option bar model for the poll block. The fill width is always driven
	 * by the same rounded percentage the label prints, so a 0% option draws no
	 * fill at all. A non-leader is additionally clamped to never reach the full
	 * track width: with real vote counts that is unreachable (a non-leader is
	 * strictly below the maximum, so it can never round to 100% while the
	 * leader exists), but the clamp keeps the "no full-width non-leader fill"
	 * rule total regardless of the inputs.
	 */
	public static Bar[] bars(int[] percentages, boolean[] leaders, int trackWidth){
		Bar[] bars=new Bar[percentages.length];
		for(int i=0;i<percentages.length;i++){
			int pct=Math.max(0, Math.min(100, percentages[i]));
			int width=barWidth(pct, trackWidth);
			boolean leader=leaders!=null && i<leaders.length && leaders[i];
			if(!leader && trackWidth>0 && width>=trackWidth)
				width=trackWidth-1;
			bars[i]=new Bar(pct, width, leader, true);
		}
		return bars;
	}

	/** Base of the percentage, per the timeline: voters when known, else votes. */
	public static int totalVotes(int votersCount, int votesCount){
		return votersCount>0 ? votersCount : votesCount;
	}

	/**
	 * Footer line: "N votes · closed" / "N votes · ends in 3 days" / "N votes".
	 * The card bakes fixed English chrome (like slice A's "Mammut" / "CW: "), so
	 * this mirrors {@code PollFooterStatusDisplayItem} + {@code UiUtils.formatTimeLeft}
	 * without touching resources.
	 */
	public static String footer(int votes, boolean expired, Long expiresAtMillis, long nowMillis){
		String text=votes+" votes";
		if(expired)
			return text+" · closed";
		if(expiresAtMillis!=null)
			return text+" · ends in "+timeLeft(expiresAtMillis-nowMillis);
		return text;
	}

	/**
	 * "less than a minute" / "N minutes" / "N hours" / "N days" (floor), the same
	 * thresholds {@code UiUtils.formatTimeLeft} uses.
	 */
	public static String timeLeft(long diffMillis){
		if(diffMillis<60_000L)
			return "less than a minute";
		if(diffMillis<3_600_000L)
			return (diffMillis/60_000L)+" minutes";
		if(diffMillis<86_400_000L)
			return (diffMillis/3_600_000L)+" hours";
		return (diffMillis/86_400_000L)+" days";
	}
}
