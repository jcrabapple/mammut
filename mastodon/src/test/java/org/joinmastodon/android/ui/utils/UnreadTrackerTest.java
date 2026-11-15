package org.joinmastodon.android.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.ui.utils.UnreadNotificationStore.Tracker;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression tests for Fix 4 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 4):
 * R3 F3 (unread state keyed by account with a generation guard — interleaved A/B
 * refresh/switch/push isolation, stale responses rejected), R3 F9 (push between the
 * bell long-press fetch and the marker save keeps the badge at 1), and R3 F11 (the
 * shared mark-all helper commits local state only after server success).
 */
public class UnreadTrackerTest{
	private static final String A="acct-a", B="acct-b";

	// R3 F3: account isolation

	@Test
	public void pushForAccountBDoesNotTouchAccountA(){
		Tracker tracker=new Tracker();
		tracker.push(A);
		Tracker.State a=tracker.snapshot(A);
		Tracker.State b=tracker.snapshot(B);
		assertEquals(1, a.count);
		assertTrue(a.known);
		assertFalse(b.known);
		assertEquals(0, b.count);
	}

	@Test
	public void markReadIsScopedToItsAccount(){
		Tracker tracker=new Tracker();
		tracker.push(A);
		tracker.push(B);
		tracker.markRead(A);
		assertEquals(0, tracker.snapshot(A).count);
		assertEquals(1, tracker.snapshot(B).count);
	}

	@Test
	public void switchToBThenLateAResponseCannotOverwriteB(){
		Tracker tracker=new Tracker();
		long aGen=tracker.beginEpoch(A);       // A refresh starts
		tracker.beginEpoch(B);                 // account switch: B refresh starts
		tracker.complete(B, tracker.snapshot(B).generation, 4, false);
		// A's slow response lands after the switch; it still records into A's own slot
		tracker.complete(A, aGen, 9, true);
		assertEquals(9, tracker.snapshot(A).count);
		assertEquals(4, tracker.snapshot(B).count);
	}

	@Test
	public void newerRefreshInvalidatesOlderInFlightRefresh(){
		Tracker tracker=new Tracker();
		long first=tracker.beginEpoch(A);
		long second=tracker.beginEpoch(A);
		assertFalse(tracker.complete(A, first, 5, false));
		assertTrue(tracker.complete(A, second, 2, false));
		assertEquals(2, tracker.snapshot(A).count);
	}

	@Test
	public void pushBetweenFetchAndSaveKeepsBadgeAtOne(){
		Tracker tracker=new Tracker();
		// refresh begins (e.g. Home onShow), then the bell flow fetches id N and saves;
		// while the save is in flight a push for N+1 arrives
		tracker.beginEpoch(A);
		tracker.push(A);
		assertEquals(1, tracker.snapshot(A).count);
		// the save succeeds and the handler refreshes: the server count (which includes
		// N+1) lands; even a stale pre-push server snapshot would be rejected
		long gen=tracker.beginEpoch(A);
		assertTrue(tracker.complete(A, gen, 1, false));
		assertEquals("push between fetch and save must survive the save", 1, tracker.snapshot(A).count);
		// contrast: the old force-zero behavior would have erased it
		tracker.markRead(A);
		assertEquals(0, tracker.snapshot(A).count);
	}

	@Test
	public void pushDuringRefreshIsNotClobberedByTheOlderServerSnapshot(){
		Tracker tracker=new Tracker();
		long gen=tracker.beginEpoch(A);
		tracker.push(A); // invalidates the in-flight refresh
		assertFalse(tracker.complete(A, gen, 0, false));
		assertEquals(1, tracker.snapshot(A).count);
	}

	// R3 F9: the bell's post-save policy

	@Test
	public void bellRefreshesAfterSaveButClearsOnEmptyList(){
		assertEquals(NavToolbarUtils.StoreUpdateAfterSave.REFRESH, NavToolbarUtils.storeUpdateAfterSave(false));
		assertEquals(NavToolbarUtils.StoreUpdateAfterSave.CLEAR, NavToolbarUtils.storeUpdateAfterSave(true));
	}

	// R3 F11: server-success-only marker commit through the shared helper

	@Test
	public void markerFailureLeavesLocalStateUntouched(){
		AtomicBoolean committed=new AtomicBoolean(false);
		AtomicBoolean failed=new AtomicBoolean(false);
		NavToolbarUtils.commitMarkerAfterServerSuccess(A, "100", (id, onSuccess, onError)->onError.run(),
				()->committed.set(true), ()->failed.set(true));
		assertFalse("local state committed despite server failure", committed.get());
		assertTrue(failed.get());
	}

	@Test
	public void markerSuccessCommitsOnce(){
		AtomicReference<String> savedId=new AtomicReference<>();
		AtomicBoolean committed=new AtomicBoolean(false);
		NavToolbarUtils.commitMarkerAfterServerSuccess(A, "100", (id, onSuccess, onError)->{
					savedId.set(id);
					onSuccess.run();
				},
				()->committed.set(true), ()->{});
		assertEquals("100", savedId.get());
		assertTrue(committed.get());
	}
}
