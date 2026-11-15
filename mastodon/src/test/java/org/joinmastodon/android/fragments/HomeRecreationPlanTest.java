package org.joinmastodon.android.fragments;

import org.joinmastodon.android.fragments.HomeFragment.ChildFragmentPlan;
import org.joinmastodon.android.fragments.HomeTabFragment.PageChangeRegistry;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 5 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 5),
 * MUST R4 F1: recreating Home on Home/Local/Notifications (process death) must not
 * NPE — the child fragments are restored before any use — and the pager's page-change
 * listener must fire exactly one callback, however many times the view was recreated.
 */
public class HomeRecreationPlanTest{

	// R4 F1: child-fragment lifecycle plan

	@Test
	public void firstLaunchCreatesChildrenAndTheyAreUsable(){
		ChildFragmentPlan plan=ChildFragmentPlan.decide(false, false);
		assertEquals(ChildFragmentPlan.Action.CREATE, plan.action);
		assertTrue(plan.childrenUsable());
	}

	@Test
	public void processDeathRecreatesWithSavedStateAndChildrenAreNotUsableUntilRestored(){
		// The pre-fix NPE: savedInstanceState!=null, children absent, and onCreateView
		// already dereferenced homeTabFragment. The plan forbids using the children
		// until onViewStateRestored has run.
		ChildFragmentPlan plan=ChildFragmentPlan.decide(true, false);
		assertEquals(ChildFragmentPlan.Action.RESTORE, plan.action);
		assertFalse(plan.childrenUsable());
	}

	@Test
	public void retainedInstanceKeepsItsChildrenAcrossRecreation(){
		ChildFragmentPlan plan=ChildFragmentPlan.decide(true, true);
		assertEquals(ChildFragmentPlan.Action.KEEP, plan.action);
		assertTrue(plan.childrenUsable());
	}

	// R4 F1: one callback per page change

	@Test
	public void sameListenerRegisteredTwiceFiresOneCallback(){
		PageChangeRegistry registry=new PageChangeRegistry();
		AtomicInteger calls=new AtomicInteger();
		Runnable listener=calls::incrementAndGet;
		assertTrue(registry.add(listener));
		assertFalse("duplicate registration must be rejected", registry.add(listener));
		assertEquals(1, registry.size());
		registry.fire();
		assertEquals(1, calls.get());
	}

	@Test
	public void distinctListenersAccumulate(){
		PageChangeRegistry registry=new PageChangeRegistry();
		AtomicInteger first=new AtomicInteger(), second=new AtomicInteger();
		assertTrue(registry.add(first::incrementAndGet));
		assertTrue(registry.add(second::incrementAndGet));
		registry.fire();
		assertEquals(1, first.get());
		assertEquals(1, second.get());
	}
}
