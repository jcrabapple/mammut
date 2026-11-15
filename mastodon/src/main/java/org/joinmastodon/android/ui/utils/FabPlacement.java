package org.joinmastodon.android.ui.utils;

import android.view.Gravity;

/**
 * MAMMUT: pure placement decisions for the navigation FAB's optional left side.
 * Kept free of any View superclass so the gravity/pivot logic is unit-testable on the JVM.
 * START/END are used (not LEFT/RIGHT) so the default "end" side keeps matching the original
 * bottom|end anchoring and mirrors correctly under RTL layouts.
 */
public final class FabPlacement{
	private FabPlacement(){}

	/** Bottom-corner gravity for the FAB anchor (in the overlay) and the FAB (in the anchor). */
	public static int cornerGravity(boolean left){
		return Gravity.BOTTOM | (left ? Gravity.START : Gravity.END);
	}

	/**
	 * Fraction of the menu panel's width the open animation scales from: the FAB corner.
	 * Right-anchored menus grow from the right edge (1.0); left-anchored from the left (0.0).
	 */
	public static float openPivotFraction(boolean left){
		return left ? 0f : 1f;
	}
}