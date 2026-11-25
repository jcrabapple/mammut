package org.joinmastodon.android;

import org.joinmastodon.android.PanicResponderActivity.PanicTriggerPolicy;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 6 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 6),
 * MUST S1.2: a panic trigger from an untrusted package is ignored; the legit
 * Guardian Project panic apps keep working.
 */
public class PanicTriggerPolicyTest{
	@Test
	public void untrustedPackageIsIgnored(){
		assertFalse(PanicTriggerPolicy.shouldRespond("com.evil.attacker"));
	}

	@Test
	public void arbitraryThirdPartyAppStoreInstallerIsIgnored(){
		assertFalse(PanicTriggerPolicy.shouldRespond("com.android.vending"));
	}

	@Test
	public void nullCallerIsIgnored(){
		assertFalse(PanicTriggerPolicy.shouldRespond(null));
	}

	@Test
	public void rippleIsTrusted(){
		assertTrue(PanicTriggerPolicy.shouldRespond("info.guardianproject.ripple"));
	}

	@Test
	public void guardianPanicIsTrusted(){
		assertTrue(PanicTriggerPolicy.shouldRespond("info.guardianproject.panic"));
	}

	@Test
	public void ownPackageIsNeverTrusted(){
		assertFalse(PanicTriggerPolicy.shouldRespond("app.mammut.android", "app.mammut.android"));
		assertFalse(PanicTriggerPolicy.shouldRespond("info.guardianproject.ripple", "info.guardianproject.ripple"));
	}
}
