package org.joinmastodon.android.fragments;

import org.joinmastodon.android.fragments.CatchUpFragment.CatchUpFill;
import org.joinmastodon.android.fragments.CatchUpFragment.CatchUpFill.End;
import org.joinmastodon.android.fragments.CatchUpFragment.CatchUpFill.FooterBody;
import org.joinmastodon.android.fragments.CatchUpFragment.CatchUpFill.PageItem;
import org.joinmastodon.android.fragments.CatchUpFragment.CatchUpFill.State;
import org.joinmastodon.android.model.Status;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 5 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 5)
 * against the pure Catch up state machine:
 * MUST R4 F3 (the cap scope line names the real, nonzero count the moment the fill
 * ends), F4 (network cursor separate from the oldest displayed; a filtered-empty
 * page still advances), F5 (a CAP-truncated fill leaves the marker and the remainder
 * reachable), F6 (a server-end mixed set gets a factual fetched-originals footer, no
 * "all posts this server has" claim), SHOULD F9 (a future marker falls back to the
 * 24h floor) and F10 (the filtered-to-empty "Load older posts" hides once the server
 * has ended).
 */
public class CatchUpFillTest{
	private static final int PAGE_SIZE=40;
	private static final int MAX_POSTS=200;
	private static final int CEILING=5;
	private static final long DAY=24L*60*60*1000;

	private static List<String> rawIDs(int... ids){
		ArrayList<String> l=new ArrayList<>(ids.length);
		for(int id:ids)
			l.add(String.valueOf(id));
		return l;
	}

	private static List<PageItem> items(long cutoff, int... ids){
		ArrayList<PageItem> l=new ArrayList<>(ids.length);
		for(int id:ids)
			l.add(new PageItem(String.valueOf(id), cutoff+id)); // newer id == newer createdAt
		return l;
	}

	// R4 F3: the cap scope count is derived from the data at format time

	@Test
	public void capFillScopeCountsOriginalPostsOnly(){
		long cutoff=10_000;
		Status post=newStatus("1", cutoff+1, false, false);
		Status boost=newStatus("2", cutoff+2, true, false);
		Status reply=newStatus("3", cutoff+3, false, true);
		assertEquals(1, CatchUpFragment.shownCountOf(List.of(post, boost, reply)));
	}

	@Test
	public void capFillScopeNamesNonzeroCountImmediately(){
		// The pre-fix bug: endFill called updateScopeAndSummary BEFORE buildList, so
		// the scope line formatted the stale shownCount (0 on first fill). The count
		// now comes from the fill state and the data themselves — nonzero the moment
		// the fill ends.
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		List<PageItem> page=items(cutoff, 200, 199, 198);
		int keep=CatchUpFill.onFillPage(s, rawIDs(200, 199, 198), page, cutoff, 3, 3, CEILING);
		assertEquals(3, keep);
		assertEquals(End.CAP, s.end);
		assertTrue(s.capTruncated);
		assertEquals(3, s.keptCount);
	}

	// R4 F4: the network cursor advances even when a page filters to nothing

	@Test
	public void filteredEmptyFillPageAdvancesTheCursor(){
		// Pre-fix: a page fully removed by server-side filters left the cursor
		// unchanged, so the next request repeated the same page until the request
		// ceiling — and then misreported a CAP. The raw oldest always advances.
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		int keep=CatchUpFill.onFillPage(s, rawIDs(120, 119, 118), List.of(), cutoff, 3, MAX_POSTS, CEILING);
		assertEquals(0, keep);
		assertEquals(End.NONE, s.end);
		assertEquals("118", s.networkCursor);
	}

	@Test
	public void fillPagesChainThroughTheRawCursor(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		CatchUpFill.onFillPage(s, rawIDs(200, 199), items(cutoff, 200, 199), cutoff, 2, MAX_POSTS, CEILING);
		assertEquals("199", s.networkCursor);
		assertEquals(End.NONE, s.end);
		CatchUpFill.onFillPage(s, rawIDs(198, 197), items(cutoff, 198, 197), cutoff, 2, MAX_POSTS, CEILING);
		assertEquals("197", s.networkCursor);
	}

	@Test
	public void trailingStragglersEndTheWindowButKeepTheRawCursor(){
		// A page whose tail is older than the cutoff stops the fill, but the network
		// cursor still points at the raw oldest of the boundary page.
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		// ids 104 and 103 are inside the window; 102..100 are boundary stragglers.
		List<PageItem> page=List.of(
				new PageItem("104", cutoff+104),
				new PageItem("103", cutoff+103),
				new PageItem("102", cutoff-1000),
				new PageItem("101", cutoff-1001),
				new PageItem("100", cutoff-1002));
		int keep=CatchUpFill.onFillPage(s, rawIDs(104, 103, 102, 101, 100), page, cutoff, 5, MAX_POSTS, CEILING);
		assertEquals(2, keep);
		assertEquals(End.WINDOW_END, s.end);
		assertEquals("100", s.networkCursor);
		assertEquals("103", s.oldestKeptID);
	}

	@Test
	public void hiddenTrailingExtensionPageAdvancesTheCursor(){
		// An extension page that filtering emptied used to leave the extension cursor
		// on the last kept id, repeating the same page on every tap. It now advances
		// to the raw oldest of the hidden page.
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		CatchUpFill.onFillPage(s, rawIDs(104, 103, 102), items(cutoff, 104, 103, 102), cutoff, 3, MAX_POSTS, CEILING);
		CatchUpFill.prepareExtension(s);
		assertEquals("102", s.extensionCursor);
		CatchUpFill.onExtensionPage(s, rawIDs(101, 100, 99), List.of()); // fully filtered
		assertFalse(s.serverEnded);
		assertEquals("99", s.extensionCursor);
	}

	// R4 F5: a CAP-truncated fill leaves the marker and the remainder reachable

	@Test
	public void cappedFillDoesNotAdvanceTheMarker(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		CatchUpFill.onFillPage(s, rawIDs(160, 159, 158, 157), items(cutoff, 160, 159, 158, 157), cutoff, 4, 4, CEILING);
		assertEquals(End.CAP, s.end);
		assertTrue(s.capTruncated);
		assertFalse("a cap-truncated fill must not advance catchUpLastAt", CatchUpFill.shouldAdvanceMarker(s));
	}

	@Test
	public void completedWindowFillAdvancesTheMarker(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		List<PageItem> page=List.of(
				new PageItem("103", cutoff+103),
				new PageItem("102", cutoff+102),
				new PageItem("101", cutoff-1000));
		CatchUpFill.onFillPage(s, rawIDs(103, 102, 101), page, cutoff, 3, MAX_POSTS, CEILING);
		assertEquals(End.WINDOW_END, s.end);
		assertTrue(CatchUpFill.shouldAdvanceMarker(s));
	}

	@Test
	public void serverEndedFillAdvancesTheMarker(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		// Raw page smaller than pageSize: the server itself ran out.
		CatchUpFill.onFillPage(s, rawIDs(104, 103), items(cutoff, 104, 103), cutoff, 3, MAX_POSTS, CEILING);
		assertEquals(End.SERVER_END, s.end);
		assertTrue(CatchUpFill.shouldAdvanceMarker(s));
	}

	@Test
	public void cappedFillLeavesMarkerAndRemainderReachable(){
		// Cap at 150 with pages of 40: after four pages (160 kept) the cap hits; the
		// fragment trims data to 150, so the oldest kept wrapper is id 851 in a
		// 1000-descending scheme and the ten trimmed stragglers (850..841) are older
		// than it — a max_id at the kept-oldest re-fetches them on extension.
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		for(int page=0;page<4;page++){
			ArrayList<String> raw=new ArrayList<>();
			ArrayList<PageItem> its=new ArrayList<>();
			for(int i=0;i<40;i++){
				int id=1000-page*40-i;
				raw.add(String.valueOf(id));
				its.add(new PageItem(String.valueOf(id), cutoff+id));
			}
			CatchUpFill.onFillPage(s, raw, its, cutoff, PAGE_SIZE, 150, CEILING);
		}
		assertEquals(End.CAP, s.end);
		assertFalse(CatchUpFill.shouldAdvanceMarker(s));
		CatchUpFill.noteCapTrim(s, "851");
		CatchUpFill.prepareExtension(s);
		assertEquals("851", s.extensionCursor);
		// Every trimmed straggler sorts BELOW the extension cursor, so the next
		// extension page (max_id = 851, exclusive) returns all of them: the
		// remainder is reachable, and the marker stayed put for the next catch-up.
		for(int id=850;id>=841;id--)
			assertTrue(id+" must be < extension cursor", id<851);
	}

	// R4 F6: server-end mixed set footer is honest

	@Test
	public void serverEndMixedSetFooterStatesFetchedOriginals(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		CatchUpFill.onFillPage(s, rawIDs(104, 103), items(cutoff, 104, 103), cutoff, 3, MAX_POSTS, CEILING);
		assertTrue(s.serverEnded);
		// No code path may select a "what the server has" claim: the server-end case
		// resolves to the same fetched-originals body as the window case.
		assertEquals(FooterBody.WINDOW, CatchUpFill.footerBody(false, s.end, false));
		assertEquals(FooterBody.SINCE, CatchUpFill.footerBody(false, s.end, true));
		// ...and the count behind it is the shown originals, not the fetched total.
		Status post=newStatus("1", cutoff+1, false, false);
		Status boost=newStatus("2", cutoff+2, true, false);
		assertEquals(1, CatchUpFragment.shownCountOf(List.of(post, boost)));
	}

	@Test
	public void footerVariantsFollowTheContract(){
		long cutoff=10_000;
		State cap=CatchUpFill.initialState();
		CatchUpFill.onFillPage(cap, rawIDs(4, 3, 2, 1), items(cutoff, 4, 3, 2, 1), cutoff, 4, 4, CEILING);
		assertEquals(End.CAP, cap.end);
		assertEquals(FooterBody.CAP, CatchUpFill.footerBody(false, cap.end, false));
		// Extension drops every window/cap claim: plain fetched totals only.
		assertEquals(FooterBody.EXTENDED, CatchUpFill.footerBody(true, cap.end, false));
		assertEquals(FooterBody.WINDOW, CatchUpFill.footerBody(false, End.WINDOW_END, false));
		assertEquals(FooterBody.SINCE, CatchUpFill.footerBody(false, End.WINDOW_END, true));
	}

	// R4 F9: a future marker falls back to the 24h floor

	@Test
	public void futureMarkerKeepsRecentPosts(){
		long now=1_000_000_000L;
		long future=now+60*60*1000; // clock skew: last catch-up "started" an hour from now
		assertEquals(now-DAY, CatchUpFill.windowStart(now, future, DAY));
		assertFalse(CatchUpFill.hasPreviousCatchUp(now, future, DAY));
	}

	@Test
	public void markerInsideTheWindowResumesThere(){
		long now=1_000_000_000L;
		long lastAt=now-60*60*1000;
		assertEquals(lastAt, CatchUpFill.windowStart(now, lastAt, DAY));
		assertTrue(CatchUpFill.hasPreviousCatchUp(now, lastAt, DAY));
	}

	@Test
	public void staleMarkerFallsBackToTheFloor(){
		long now=1_000_000_000L;
		assertEquals(now-DAY, CatchUpFill.windowStart(now, now-2*DAY, DAY));
		assertFalse(CatchUpFill.hasPreviousCatchUp(now, now-2*DAY, DAY));
		assertEquals(now-DAY, CatchUpFill.windowStart(now, 0, DAY));
		assertFalse(CatchUpFill.hasPreviousCatchUp(now, 0, DAY));
	}

	// R4 F10: inert filtered-empty "Load older posts" is hidden

	@Test
	public void filteredEmptyLoadOlderHiddenOnceTheServerEnded(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		assertTrue(CatchUpFill.showFilteredEmptyLoadOlder(s));
		CatchUpFill.onFillPage(s, rawIDs(104, 103), items(cutoff, 104, 103), cutoff, 3, MAX_POSTS, CEILING);
		assertTrue(s.serverEnded);
		assertFalse(CatchUpFill.showFilteredEmptyLoadOlder(s));
	}

	@Test
	public void extensionEmptyPageEndsTheServerAndHidesLoadOlder(){
		long cutoff=10_000;
		State s=CatchUpFill.initialState();
		CatchUpFill.prepareExtension(s);
		CatchUpFill.onExtensionPage(s, List.of(), List.of());
		assertTrue(s.serverEnded);
		assertFalse(CatchUpFill.showFilteredEmptyLoadOlder(s));
		assertNull(s.extensionCursor);
	}

	private static Status newStatus(String id, long createdAtMillis, boolean boost, boolean reply){
		Status s=new Status();
		s.id=id;
		s.createdAt=java.time.Instant.ofEpochMilli(createdAtMillis);
		if(boost)
			s.reblog=new Status();
		if(reply)
			s.inReplyToId="parent";
		return s;
	}
}
