package org.joinmastodon.android.ui.text;

import org.joinmastodon.android.GlobalUserPreferences.FontFamily;
import org.joinmastodon.android.R;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/**
 * FONT-SPEC §7 — the resource table is the one place a rename in
 * {@code res/font/} could silently ship a family that renders as the
 * system face, so these tests pin {@link MammutTypeface#resourceFor} to
 * the exact bundled files. Runs on the JVM: resourceFor is a pure switch
 * over the generated {@link R} constants, and MammutTypeface's class
 * initializer stays pure-Java (the face cache is created lazily in get())
 * precisely so this mapping is unit-testable.
 *
 * Slot order is the table in resourceFor: 0 = regular, 1 = medium,
 * 2 = bold, 3 = regular-italic.
 */
public class MammutTypefaceTest{
	private static final int SLOT_REGULAR=0, SLOT_MEDIUM=1, SLOT_BOLD=2, SLOT_ITALIC=3;

	@Test
	public void interMapsToBundledFiles(){
		assertEquals(R.font.inter_regular, MammutTypeface.resourceFor(FontFamily.INTER, SLOT_REGULAR));
		assertEquals(R.font.inter_medium, MammutTypeface.resourceFor(FontFamily.INTER, SLOT_MEDIUM));
		assertEquals(R.font.inter_bold, MammutTypeface.resourceFor(FontFamily.INTER, SLOT_BOLD));
		assertEquals(R.font.inter_italic, MammutTypeface.resourceFor(FontFamily.INTER, SLOT_ITALIC));
	}

	@Test
	public void sourceSansMapsToBundledFiles(){
		assertEquals(R.font.source_sans_regular, MammutTypeface.resourceFor(FontFamily.SOURCE_SANS, SLOT_REGULAR));
		assertEquals(R.font.source_sans_medium, MammutTypeface.resourceFor(FontFamily.SOURCE_SANS, SLOT_MEDIUM));
		assertEquals(R.font.source_sans_bold, MammutTypeface.resourceFor(FontFamily.SOURCE_SANS, SLOT_BOLD));
		assertEquals(R.font.source_sans_italic, MammutTypeface.resourceFor(FontFamily.SOURCE_SANS, SLOT_ITALIC));
	}

	@Test
	public void literataMapsToBundledFiles(){
		assertEquals(R.font.literata_regular, MammutTypeface.resourceFor(FontFamily.LITERATA, SLOT_REGULAR));
		assertEquals(R.font.literata_medium, MammutTypeface.resourceFor(FontFamily.LITERATA, SLOT_MEDIUM));
		assertEquals(R.font.literata_bold, MammutTypeface.resourceFor(FontFamily.LITERATA, SLOT_BOLD));
		assertEquals(R.font.literata_italic, MammutTypeface.resourceFor(FontFamily.LITERATA, SLOT_ITALIC));
	}

	@Test
	public void atkinsonMapsToBundledFiles(){
		assertEquals(R.font.atkinson_regular, MammutTypeface.resourceFor(FontFamily.ATKINSON, SLOT_REGULAR));
		assertEquals(R.font.atkinson_bold, MammutTypeface.resourceFor(FontFamily.ATKINSON, SLOT_BOLD));
		assertEquals(R.font.atkinson_italic, MammutTypeface.resourceFor(FontFamily.ATKINSON, SLOT_ITALIC));
	}

	@Test
	public void atkinsonMediumFallsBackToBoldFile(){
		// §2: Atkinson Hyperlegible ships no 500 — the medium slot serves
		// the 700 file rather than inventing a weight the family lacks.
		assertEquals(R.font.atkinson_bold, MammutTypeface.resourceFor(FontFamily.ATKINSON, SLOT_MEDIUM));
	}

	@Test
	public void systemMapsToNoResource(){
		for(int slot=SLOT_REGULAR; slot<=SLOT_ITALIC; slot++)
			assertEquals(0, MammutTypeface.resourceFor(FontFamily.SYSTEM, slot));
	}

	@Test
	public void everyFamilySlotHasAFile(){
		// §7 no-null guard: a rename in res/font/ must fail a test, not
		// silently render the system face. Iterates the enum so a fifth
		// family is covered the day it lands.
		for(FontFamily family : FontFamily.values()){
			if(family==FontFamily.SYSTEM)
				continue;
			for(int slot=SLOT_REGULAR; slot<=SLOT_ITALIC; slot++)
				assertNotEquals(family+" slot "+slot+" resolves to no resource", 0, MammutTypeface.resourceFor(family, slot));
		}
	}

	@Test
	public void unknownSlotMapsToZero(){
		assertEquals(0, MammutTypeface.resourceFor(FontFamily.INTER, 4));
		assertEquals(0, MammutTypeface.resourceFor(FontFamily.INTER, -1));
	}

	@Test
	public void nothingIsBundledBeforeAnyFaceLoads(){
		// M2-a: apply()'s idempotency guard scans the lazily created face
		// cache. On the JVM no face is ever loaded, so the cache stays
		// null and nothing reads as bundled — this pins both the guard's
		// null-base path and the cache-stays-lazy class-init property the
		// resourceFor tests above rely on.
		assertFalse(MammutTypeface.isBundled(null));
	}
}
