package org.joinmastodon.android.ui.share;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PollBlockTest{

	//region Percentage rounding

	@Test
	public void zeroVotesIsZeroPercent(){
		assertEquals(0, PollBlock.percentage(0, 10));
		assertEquals(0, PollBlock.barWidth(0, 968));
	}

	@Test
	public void zeroTotalIsZeroPercent(){
		// No votes yet: no division, no NaN, no leader.
		assertEquals(0, PollBlock.percentage(5, 0));
		assertEquals(0, PollBlock.percentage(0, 0));
	}

	@Test
	public void thirdRoundsDown(){
		assertEquals(33, PollBlock.percentage(1, 3));
	}

	@Test
	public void twoThirdsRoundsUp(){
		assertEquals(67, PollBlock.percentage(2, 3));
	}

	@Test
	public void halfRoundsUp(){
		// 1/8 = 12.5% → 13 (Math.round rounds .5 toward the higher integer).
		assertEquals(13, PollBlock.percentage(1, 8));
		// 1/40 = 2.5% → 3.
		assertEquals(3, PollBlock.percentage(1, 40));
	}

	@Test
	public void exactHalfIsExact(){
		// 149/298 = exactly 50%.
		assertEquals(50, PollBlock.percentage(149, 298));
	}

	//endregion

	//region Bar geometry (driven by the printed rounded percentage)

	@Test
	public void zeroPercentBarHasZeroWidth(){
		assertEquals(0, PollBlock.barWidth(0, 968));
	}

	@Test
	public void fullPercentFillsTrack(){
		assertEquals(968, PollBlock.barWidth(100, 968));
	}

	@Test
	public void barWidthRoundsLikeThePercentage(){
		// 67% of 968 = 648.56 → 649; the label prints "67%" and the bar matches it.
		assertEquals(649, PollBlock.barWidth(67, 968));
		// 50% of 968 lands exactly.
		assertEquals(484, PollBlock.barWidth(50, 968));
		// 33% of 968 = 319.44 → 319.
		assertEquals(319, PollBlock.barWidth(33, 968));
	}

	@Test
	public void barNeverExceedsTrack(){
		assertEquals(968, PollBlock.barWidth(150, 968));
		assertEquals(0, PollBlock.barWidth(-5, 968));
		assertEquals(0, PollBlock.barWidth(50, 0));
	}

	//endregion

	//region Leaders and ties

	@Test
	public void singleOptionIsLeaderAndFullBar(){
		int[] votes={7};
		int[] pcts=PollBlock.percentages(votes, PollBlock.totalVotes(0, 7));
		assertEquals(100, pcts[0]);
		assertTrue(PollBlock.isLeader(0, votes));
		assertEquals(968, PollBlock.barWidth(pcts[0], 968));
	}

	@Test
	public void tieHasTwoLeadersWithEqualBars(){
		// Timeline precedent: every option at the maximum is highlighted.
		int[] votes={5, 5, 1};
		int total=PollBlock.totalVotes(0, 11);
		int[] pcts=PollBlock.percentages(votes, total);
		assertEquals(45, pcts[0]);
		assertEquals(45, pcts[1]);
		assertEquals(9, pcts[2]);
		assertTrue(PollBlock.isLeader(0, votes));
		assertTrue(PollBlock.isLeader(1, votes));
		assertFalse(PollBlock.isLeader(2, votes));
		assertEquals(PollBlock.barWidth(pcts[0], 968), PollBlock.barWidth(pcts[1], 968));
	}

	@Test
	public void noLeaderWhenNobodyVoted(){
		int[] votes={0, 0};
		assertFalse(PollBlock.isLeader(0, votes));
		assertFalse(PollBlock.isLeader(1, votes));
	}

	@Test
	public void totalPrefersVoters(){
		assertEquals(4, PollBlock.totalVotes(4, 9));
		assertEquals(9, PollBlock.totalVotes(0, 9));
	}

	//endregion

	//region Footer (open, closed, expiry text)

	@Test
	public void closedPollFooter(){
		assertEquals("5 votes · closed", PollBlock.footer(5, true, null, 0));
		// An expiry in the past is still just "closed".
		assertEquals("5 votes · closed", PollBlock.footer(5, true, 1000L, 2000L));
	}

	@Test
	public void openPollFooterWithExpiry(){
		long now=1_000_000L;
		assertEquals("3 votes · ends in 3 days", PollBlock.footer(3, false, now+3*86_400_000L, now));
	}

	@Test
	public void openPollFooterWithoutExpiry(){
		assertEquals("7 votes", PollBlock.footer(7, false, null, 0));
	}

	@Test
	public void timeLeftBoundaries(){
		assertEquals("less than a minute", PollBlock.timeLeft(30_000));
		assertEquals("5 minutes", PollBlock.timeLeft(5*60_000));
		assertEquals("59 minutes", PollBlock.timeLeft(59*60_000+30_000));
		assertEquals("23 hours", PollBlock.timeLeft(23*3_600_000L));
		assertEquals("1 days", PollBlock.timeLeft(25*3_600_000L));
		assertEquals("3 days", PollBlock.timeLeft(3*86_400_000L));
	}

	//endregion

	//region Bar drawing model (review M1)

	@Test
	public void zeroPercentOptionHasZeroFillWidth(){
		// The M1 regression: a 0% option must not read as a full bar.
		PollBlock.Bar[] bars=PollBlock.bars(new int[]{0, 50, 50}, new boolean[]{false, true, true}, 968);
		assertEquals(0, bars[0].fillWidth);
		assertTrue(bars[0].track);
		assertFalse(bars[0].leader);
	}

	@Test
	public void nonLeaderIsStrictlyNarrowerThanFullWidth(){
		// 60/20/20 split: the non-leaders are 194 px of 968, never the full track.
		int[] votes={6, 2, 2};
		int total=PollBlock.totalVotes(0, 10);
		int[] pcts=PollBlock.percentages(votes, total);
		boolean[] leaders={PollBlock.isLeader(0, votes), PollBlock.isLeader(1, votes), PollBlock.isLeader(2, votes)};
		PollBlock.Bar[] bars=PollBlock.bars(pcts, leaders, 968);
		// 60% of 968 = 580.8 → 581: the leader bar scales to its percentage.
		assertEquals(581, bars[0].fillWidth);
		assertEquals(PollBlock.barWidth(60, 968), bars[0].fillWidth);
		assertTrue(bars[1].fillWidth<968);
		assertTrue(bars[2].fillWidth<968);
		assertEquals(194, bars[1].fillWidth);
		assertEquals(194, bars[2].fillWidth);
	}

	@Test
	public void leaderWidthEqualsItsRoundedPercentage(){
		int[] votes={2, 1};
		int total=PollBlock.totalVotes(0, 3);
		int[] pcts=PollBlock.percentages(votes, total);
		boolean[] leaders={PollBlock.isLeader(0, votes), PollBlock.isLeader(1, votes)};
		PollBlock.Bar[] bars=PollBlock.bars(pcts, leaders, 968);
		// 67% of 968 = 648.56 → 649; same value the label prints.
		assertEquals(67, bars[0].percentage);
		assertEquals(649, bars[0].fillWidth);
		assertEquals(Math.round(968*67/100f), bars[0].fillWidth);
	}

	@Test
	public void noEntryDescribesAFullWidthNonLeaderFill(){
		int[] votes={5, 5, 1, 0};
		int total=PollBlock.totalVotes(0, 11);
		int[] pcts=PollBlock.percentages(votes, total);
		boolean[] leaders={PollBlock.isLeader(0, votes), PollBlock.isLeader(1, votes), PollBlock.isLeader(2, votes), PollBlock.isLeader(3, votes)};
		PollBlock.Bar[] bars=PollBlock.bars(pcts, leaders, 968);
		for(PollBlock.Bar bar:bars){
			assertTrue(bar.track);
			if(!bar.leader)
				assertTrue("non-leader fill "+bar.fillWidth+" must be < track 968", bar.fillWidth<968);
		}
	}

	@Test
	public void pathologicalNonLeaderIsClampedBelowFullWidth(){
		// Unreachable with real votes (a non-leader is below the maximum and so
		// cannot round to 100% while a leader exists), but the model must keep
		// the invariant total regardless of the inputs.
		PollBlock.Bar[] bars=PollBlock.bars(new int[]{100, 100}, new boolean[]{true, false}, 968);
		assertEquals(968, bars[0].fillWidth);
		assertEquals(967, bars[1].fillWidth);
		assertFalse(bars[1].leader);
	}

	@Test
	public void everyOptionDrawsAnEmptyTrack(){
		PollBlock.Bar[] bars=PollBlock.bars(new int[]{45, 45, 9, 0}, new boolean[]{true, true, false, false}, 968);
		for(PollBlock.Bar bar:bars)
			assertTrue(bar.track);
	}

	//endregion
}
