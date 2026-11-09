package org.joinmastodon.android.ui.text;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import org.joinmastodon.android.GlobalUserPreferences.FontFamily;
import org.joinmastodon.android.R;
import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

	// --- M5 minor-1: the identity scan itself, decoupled from the cache ---

	@Test
	public void identityScanFindsContainedInstance(){
		Object[] faces={new Object(), new Object(), new Object()};
		assertTrue(MammutTypeface.containsIdentity(faces[1], faces));
	}

	@Test
	public void identityScanRejectsAbsentAndNull(){
		Object[] faces={new Object(), new Object()};
		assertFalse(MammutTypeface.containsIdentity(new Object(), faces));
		assertFalse(MammutTypeface.containsIdentity(null, faces));
		assertFalse(MammutTypeface.containsIdentity(faces[0], null));
		assertFalse(MammutTypeface.containsIdentity(faces[0], new Object[0]));
	}

	@Test
	public void identityScanUsesReferenceNotEquality(){
		// The contract is == (get() returns the cached instance itself),
		// not equals(): two Strings with equal content are distinct
		// identities. An equals-based scan would wrongly report a
		// system face built from the same name as bundled.
		String base=new String("sans-serif");
		String[] faces={new String("sans-serif")};
		assertFalse(MammutTypeface.containsIdentity(base, faces));
	}

	// --- M5: constructor-cache resolution (the pre-Q null-context fix) ---

	@Test
	public void resolvesTwoArgConstructorForViewSubclass() throws Exception{
		// Statically pins the path InflaterFactory constructs views
		// through: the framework-passed context goes straight into the
		// class's own (Context, AttributeSet) constructor, so no
		// inflater's mConstructorArgs — null on pre-Q ViewStub clones and
		// android:theme wrappers — can poison construction. Reflection
		// only: the mockable android.jar loads View and its constructors
		// but throws Stub! if one actually ran.
		String name="org.joinmastodon.android.ui.text.MammutTypefaceTest$FixtureView";
		Constructor<? extends View> ctor=MammutTypeface.InflaterFactory.resolveConstructor(name, getClass().getClassLoader());
		assertNotNull(ctor);
		assertSame(FixtureView.class, ctor.getDeclaringClass());
		// The cached Constructor is reused, not re-resolved.
		assertSame(ctor, MammutTypeface.InflaterFactory.resolveConstructor(name, getClass().getClassLoader()));
	}

	@Test
	public void resolveReturnsNullForNonViewClass(){
		assertNull(MammutTypeface.InflaterFactory.resolveConstructor(String.class.getName(), getClass().getClassLoader()));
	}

	@Test
	public void resolveReturnsNullForUnknownClass(){
		assertNull(MammutTypeface.InflaterFactory.resolveConstructor("does.not.Exist", getClass().getClassLoader()));
	}

	@Test
	public void resolveReturnsNullWithoutTwoArgConstructor(){
		// A View subclass lacking the (Context, AttributeSet) XML
		// constructor cannot come from a layout — resolution must fail
		// quietly so the framework's own fallback decides.
		String name="org.joinmastodon.android.ui.text.MammutTypefaceTest$OneArgView";
		assertNull(MammutTypeface.InflaterFactory.resolveConstructor(name, getClass().getClassLoader()));
	}

	/** Fixture for resolveConstructor: a plain View subclass with the XML constructor. */
	public static class FixtureView extends View{
		public FixtureView(Context context, AttributeSet attrs){
			super(context, attrs);
		}
	}

	/** Fixture for resolveConstructor: View subclass without the two-arg constructor. */
	public static class OneArgView extends View{
		public OneArgView(Context context){
			super(context);
		}
	}
}
