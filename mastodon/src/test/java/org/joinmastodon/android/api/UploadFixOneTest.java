package org.joinmastodon.android.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Regression tests for the Fix 1 wave of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md
 * §Fix 1). These pin the pure decision objects behind R2 F1/F2/F3/F11; the Android-bound
 * bodies ({@link ResizedImageRequestBody} and friends) delegate to exactly these seams,
 * because a plain JVM cannot construct a Bitmap or fake a ContentProvider.
 *
 * <p>F1: avatar/header bodies are built only from a verified local copy — a provider
 * size column (NULL, 0, or plausible) must never decide whether staging happens.
 * F2: PNG is detected by type/subtype — the old {@code "image/png".equals(MediaType)}
 * was always false and transparent PNGs were flattened to JPEG.
 * F3: temp files live for the whole request lifetime and are deleted exactly once on
 * explicit release — never on first write, which broke OkHttp-level retries.
 * F11: a failed staging leaves no {@code mastodon_upload_*} artifact behind.</p>
 */
public class UploadFixOneTest{

	// --- R2 F1: avatar/header staged through the verified local-file pipeline ---

	@Test
	public void contentSourceAlwaysRequiresVerifiedCopyEvenWhenProviderReportsSize(){
		// The 1.3.5 F1 failure: a 400x400 avatar whose provider reports a NULL size was
		// read as 0 and uploaded as an empty part; a plausible size must not skip staging
		// either — only a verified local copy may feed a request body.
		assertTrue(new UploadSource(UploadSource.Kind.CONTENT, 12_345, "image/jpeg", "a.jpg").requiresVerifiedCopy());
		assertTrue(new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, "image/png", null).requiresVerifiedCopy());
		// A provider's 0 is indistinguishable from a NULL column read through getInt().
		assertTrue(new UploadSource(UploadSource.Kind.CONTENT, 0, "image/jpeg", "a.jpg").requiresVerifiedCopy());
	}

	@Test
	public void fileSourceDoesNotRequireVerifiedCopy(){
		assertFalse(new UploadSource(UploadSource.Kind.FILE, 12_345, "image/jpeg", "a.jpg").requiresVerifiedCopy());
	}

	// --- R2 F2: PNG detection compares type/subtype, preserving alpha + MIME ---

	@Test
	public void pngDetectedByTypeAndSubtype(){
		assertTrue(UploadSource.isPngMime("image/png"));
		assertFalse(UploadSource.isPngMime("image/pngx"));
		assertFalse(UploadSource.isPngMime("image/jpeg"));
		assertFalse(UploadSource.isPngMime(null));
		assertTrue(new UploadSource(UploadSource.Kind.CONTENT, 1, "image/png", "p.png").isPng());
		assertFalse(new UploadSource(UploadSource.Kind.CONTENT, 1, "image/jpeg", "p.jpg").isPng());
	}

	@Test
	public void pngDetectionIsCaseAndLocaleSafe(){
		// Same Turkish-locale trap as the HEIC check: default-locale lowercasing turns
		// "IMAGE/PNG" into "ıMAGE/PNG" and silently flattens a transparent PNG to JPEG.
		Locale previous=Locale.getDefault();
		Locale.setDefault(new Locale("tr", "TR"));
		try{
			assertTrue(UploadSource.isPngMime("IMAGE/PNG"));
			assertTrue(new UploadSource(UploadSource.Kind.CONTENT, 1, "IMAGE/PNG", null).isPng());
		}finally{
			Locale.setDefault(previous);
		}
	}

	// --- R2 F3: temp files survive until explicit terminal release, then die exactly once ---

	@Test
	public void tempFilesAreDeletedExactlyOnceOnRelease() throws IOException{
		UploadStaging.TempFiles temps=new UploadStaging.TempFiles();
		File a=File.createTempFile("mammut-fix1-a", ".tmp");
		File b=File.createTempFile("mammut-fix1-b", ".tmp");
		temps.add(a);
		temps.add(b);
		assertFalse(temps.isEmpty());
		temps.releaseAll();
		assertFalse("staged temp must be deleted on release", a.exists());
		assertFalse("decoded temp must be deleted on release", b.exists());
		assertTrue(temps.isEmpty());
		// Release is terminal and idempotent — a second call must not throw.
		temps.releaseAll();
	}

	@Test
	public void tempFilesTolerateNullAndMissingFiles(){
		UploadStaging.TempFiles temps=new UploadStaging.TempFiles();
		File missing=new File("/nonexistent/mammut-fix1-missing.tmp");
		temps.add(null);
		temps.add(missing);
		temps.releaseAll();
		assertTrue(temps.isEmpty());
	}

	// --- R2 F1/F11: the staging copy itself, straight from a stream ---

	@Test
	public void stagedCopyContainsEveryByteWithUploadName() throws IOException{
		Path dir=Files.createTempDirectory("mammut-fix1-stage");
		byte[] data=new byte[200_000];
		new Random(20261115L).nextBytes(data);
		File staged=UploadStaging.copyToCache(new ByteArrayInputStream(data), dir.toFile(), ".png");
		try{
			assertTrue("staged file keeps the mastodon_upload_ name: "+staged.getName(),
					staged.getName().startsWith("mastodon_upload_"));
			assertTrue("staged file keeps the source suffix: "+staged.getName(),
					staged.getName().endsWith(".png"));
			assertArrayEquals("every provider byte must reach the staged file", data, Files.readAllBytes(staged.toPath()));
			assertEquals(data.length, staged.length());
		}finally{
			staged.delete();
		}
	}

	@Test
	public void emptyProviderStreamLeavesNoUploadArtifact() throws IOException{
		Path dir=Files.createTempDirectory("mammut-fix1-empty");
		assertThrows(EmptyUploadException.class,
				()->UploadStaging.copyToCache(new ByteArrayInputStream(new byte[0]), dir.toFile(), ".jpg"));
		try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir, "mastodon_upload_*")){
			assertFalse("a failed staging must not leak a mastodon_upload_* temp", ds.iterator().hasNext());
		}
	}

	@Test
	public void nullProviderStreamLeavesNoUploadArtifact() throws IOException{
		Path dir=Files.createTempDirectory("mammut-fix1-null");
		assertThrows(IOException.class,
				()->UploadStaging.copyToCache(null, dir.toFile(), ".jpg"));
		try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir, "mastodon_upload_*")){
			assertFalse(ds.iterator().hasNext());
		}
	}
}
