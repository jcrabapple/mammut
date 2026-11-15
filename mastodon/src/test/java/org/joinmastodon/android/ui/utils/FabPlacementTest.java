package org.joinmastodon.android.ui.utils;

import android.view.Gravity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Regression tests for the optional left-side navigation FAB. The placement decisions
 * are pure so they can be pinned on the JVM: the default stays bottom|end (the original
 * anchoring), the opt-in flips to bottom|start, and the open animation pivots from the
 * FAB's corner rather than always the right edge.
 */
public class FabPlacementTest{

	@Test
	public void rightAnchorsToEnd(){
		assertEquals(Gravity.BOTTOM|Gravity.END, FabPlacement.cornerGravity(false));
	}

	@Test
	public void leftAnchorsToStart(){
		assertEquals(Gravity.BOTTOM|Gravity.START, FabPlacement.cornerGravity(true));
	}

	@Test
	public void sidesDiffer(){
		assertNotEquals(FabPlacement.cornerGravity(true), FabPlacement.cornerGravity(false));
	}

	@Test
	public void bothStayBottom(){
		assertEquals(Gravity.BOTTOM, FabPlacement.cornerGravity(true)&Gravity.BOTTOM);
		assertEquals(Gravity.BOTTOM, FabPlacement.cornerGravity(false)&Gravity.BOTTOM);
	}

	@Test
	public void pivotRightIsFarEdge(){
		assertEquals(1f, FabPlacement.openPivotFraction(false), 0f);
	}

	@Test
	public void pivotLeftIsNearEdge(){
		assertEquals(0f, FabPlacement.openPivotFraction(true), 0f);
	}
}