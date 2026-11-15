package org.joinmastodon.android.fragments.drafts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.fragments.drafts.DraftsListFragment.ScheduledRefresh;
import org.junit.Test;

/**
 * Regression test for Fix 3 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 3, R1 F10): the Scheduled tab refreshes when shown after loading
 * once, and when a schedule is created while it sits hidden — hide/show
 * never touches the fragment lifecycle, so onResume alone cannot cover
 * either case.
 */
public class ScheduledTabRefreshTest{

	@Test
	public void showingAnAlreadyLoadedPageReloadsIt(){
		assertTrue(ScheduledRefresh.shouldReloadOnShow(true));
	}

	@Test
	public void firstShowLoadsInCreateViewAndMustNotDoubleLoad(){
		assertFalse(ScheduledRefresh.shouldReloadOnShow(false));
	}

	@Test
	public void createEventWhileHiddenForcesReload(){
		assertTrue(ScheduledRefresh.shouldReloadOnCreateEvent(true, true));
	}

	@Test
	public void createEventOnVisiblePageNeedsNoHiddenReload(){
		// The visible page either loaded recently or is about to reload on
		// the tab switch that shows it.
		assertFalse(ScheduledRefresh.shouldReloadOnCreateEvent(false, true));
	}

	@Test
	public void eventBeforeFirstLoadNeedsNoReload(){
		// Nothing is stale before the page ever loaded; onCreateView loads.
		assertFalse(ScheduledRefresh.shouldReloadOnCreateEvent(true, false));
	}
}
