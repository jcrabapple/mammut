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
