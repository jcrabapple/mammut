package org.joinmastodon.android.updater;

import org.joinmastodon.android.MainActivity;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Wave 2 security unit tests covering:
 * - S-H1: SelfUpdateVerifier download URL allowlist, semantic version parser, SHA-256 parsing & hashing
 * - S-M4: UiUtils.isAllowedBrowserUrl scheme allowlist
 * - S-M6: MainActivity.NotificationHandoff one-shot in-process token
 */
public class SelfUpdateVerifierTest{

	@Test
	public void downloadUrlAllowlistAcceptsOnlyOfficialHttpsGithubHosts(){
		assertTrue(SelfUpdateVerifier.isAllowedDownloadUrl("https://github.com/jcrabapple/mammut/releases/download/v1.3.16/mammut-1.3.16.apk"));
		assertTrue(SelfUpdateVerifier.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-123/file"));
		assertTrue(SelfUpdateVerifier.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/some/path"));

		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl("http://github.com/jcrabapple/mammut/releases/download/v1.3.16/mammut.apk"));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl("https://evil.example.com/mammut.apk"));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl("https://github.com.evil.example/mammut.apk"));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl("https://user:pass@github.com/a.apk"));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl("file:///sdcard/update.apk"));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl(null));
		assertFalse(SelfUpdateVerifier.isAllowedDownloadUrl(""));
	}

	@Test
	public void semanticVersionHandlesTwoAndThreePartVersionsAccurately(){
		long v14=SelfUpdateVerifier.parseSemanticVersion("v1.4");
		long v141=SelfUpdateVerifier.parseSemanticVersion("1.4.1");
		long v1315=SelfUpdateVerifier.parseSemanticVersion("1.3.15-debug");
		long v1316=SelfUpdateVerifier.parseSemanticVersion("v1.3.16");

		assertTrue(v141>v14);
		assertTrue(v14>v1316);
		assertTrue(v1316>v1315);
		assertEquals(-1L, SelfUpdateVerifier.parseSemanticVersion("nightly-abc"));
		assertEquals(-1L, SelfUpdateVerifier.parseSemanticVersion(null));
	}

	@Test
	public void parseSha256FileExtractsValidDigestAndRejectsJunk(){
		String hex="9e6c898feb404dee7f774d8b9f5586e27d4ad2b7b3e1644b5afb0f32dfe6f887";
		assertEquals(hex, SelfUpdateVerifier.parseSha256File(hex+"  mammut-1.3.16.apk\n"));
		assertEquals(hex, SelfUpdateVerifier.parseSha256File(hex.toUpperCase()));
		assertNull(SelfUpdateVerifier.parseSha256File("not-a-hash  mammut.apk"));
		assertNull(SelfUpdateVerifier.parseSha256File(""));
		assertNull(SelfUpdateVerifier.parseSha256File(null));
	}

	@Test
	public void sha256HexHashesFileAndMatchesConstantTime() throws Exception{
		File f=File.createTempFile("selfupdate_test", ".bin");
		try{
			try(FileOutputStream out=new FileOutputStream(f)){
				out.write("mammut".getBytes(StandardCharsets.UTF_8));
			}
			String digest=SelfUpdateVerifier.sha256Hex(f);
			assertEquals(64, digest.length());
			assertTrue(SelfUpdateVerifier.digestsEqual(digest.toUpperCase(), digest));
			assertFalse(SelfUpdateVerifier.digestsEqual("0".repeat(64), digest));
		}finally{
			//noinspection ResultOfMethodCallIgnored
			f.delete();
		}
	}

	@Test
	public void browserUrlAllowlistBlocksNonHttpSchemes(){
		assertTrue(UiUtils.UrlPolicy.isAllowedBrowserUrl("https://mastodon.social/@user"));
		assertTrue(UiUtils.UrlPolicy.isAllowedBrowserUrl("http://example.org/page"));
		assertTrue(UiUtils.UrlPolicy.isAllowedBrowserUrl("  HTTPS://EXAMPLE.ORG  "));

		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl("javascript:alert(1)"));
		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl("file:///etc/hosts"));
		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl("content://app.mammut.android.fileprovider/share/x"));
		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl("tel:+1234567890"));
		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl("intent://evil/#Intent;end"));
		assertFalse(UiUtils.UrlPolicy.isAllowedBrowserUrl(null));
	}

	@Test
	public void notificationHandoffIsOneShotAndRejectsForgedTokens(){
		assertNull(MainActivity.NotificationHandoff.take("forged-external-token"));
		assertNull(MainActivity.NotificationHandoff.take(null));

		Notification n=new Notification();
		n.id="42";
		String token=MainActivity.NotificationHandoff.put("example.social_1", n);
		assertNotNull(MainActivity.NotificationHandoff.peek(token));
		MainActivity.NotificationHandoff.Entry taken=MainActivity.NotificationHandoff.take(token);
		assertNotNull(taken);
		assertEquals("example.social_1", taken.accountID);
		assertEquals("42", taken.notification.id);
		assertNull(MainActivity.NotificationHandoff.take(token));
	}
}
