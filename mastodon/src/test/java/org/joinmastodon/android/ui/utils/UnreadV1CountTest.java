package org.joinmastodon.android.ui.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.joinmastodon.android.api.session.AccountSession.MarkerResolution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Regression tests for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4):
 * R3 F8 (V1 unread counting by compare(id, marker) > 0, so a deleted marker between
 * two ids counts only the newer items) and R3 F7 (marker resolution always completes:
 * empty markers, failed requests, and missing local state fall back to the local
 * marker or the "0" sentinel).
 */
public class UnreadV1CountTest{

	private static List<String> ids(String... ids){
		return new ArrayList<>(Arrays.asList(ids));
	}

	// R3 F8: deleted-marker counting

	@Test
	public void deletedMarkerBetweenTwoIdsCountsOnlyNewer(){
		// marker 25 is gone from the page; only 30 is unread — the old exact-equality
		// loop counted all 3 because no id equaled the marker
		assertArrayEquals(new int[]{1, 0}, UnreadNotificationCounter.V1Count.compute(ids("30", "20", "10"), "25"));
	}

	@Test
	public void markerOlderThanTheWholePageSetsMore(){
		assertArrayEquals(new int[]{3, 1}, UnreadNotificationCounter.V1Count.compute(ids("30", "20", "10"), "5"));
	}

	@Test
	public void markerAtOrAboveTheNewestIdMeansNothingUnread(){
		assertArrayEquals(new int[]{0, 0}, UnreadNotificationCounter.V1Count.compute(ids("30", "20", "10"), "30"));
		assertArrayEquals(new int[]{0, 0}, UnreadNotificationCounter.V1Count.compute(ids("30", "20", "10"), "35"));
	}

	@Test
	public void markerPresentInPageCountsDownToIt(){
		assertArrayEquals(new int[]{1, 0}, UnreadNotificationCounter.V1Count.compute(ids("30", "20", "10"), "20"));
	}

	@Test
	public void fullPageBoundaryReportsMore(){
		List<String> page=ids();
		for(int i=40; i>=1; i--)
			page.add(String.valueOf(i*10));
		assertArrayEquals(new int[]{40, 1}, UnreadNotificationCounter.V1Count.compute(page, "5"));
		// and when the marker sits inside the 40-item page, more is not set
		assertArrayEquals(new int[]{24, 0}, UnreadNotificationCounter.V1Count.compute(page, "160"));
	}

	@Test
	public void emptyPagePublishesZero(){
		assertArrayEquals(new int[]{0, 0}, UnreadNotificationCounter.V1Count.compute(Collections.emptyList(), "25"));
	}

	@Test
	public void differentLengthIdsCompareNumerically(){
		// snowflake ids as strings: "1100" (19 chars eventually) vs shorter older ids
		assertArrayEquals(new int[]{1, 0}, UnreadNotificationCounter.V1Count.compute(ids("1100", "950"), "1000"));
	}

	// R3 F7: marker fallback resolution

	@Test
	public void serverMarkerWinsWhenNewest(){
		assertEquals("120", MarkerResolution.resolve("120", "100"));
	}

	@Test
	public void movedBackServerMarkerClampsToLocal(){
		assertEquals("100", MarkerResolution.resolve("90", "100"));
	}

	@Test
	public void emptyServerMarkerFallsBackToLocal(){
		assertEquals("100", MarkerResolution.resolve(null, "100"));
		assertEquals("100", MarkerResolution.resolve("", "100"));
	}

	@Test
	public void noServerAndNoLocalMarkerPublishesZeroSentinel(){
		assertEquals("0", MarkerResolution.resolve(null, null));
		assertEquals("0", MarkerResolution.resolve("", null));
	}
}
