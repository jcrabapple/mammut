package org.joinmastodon.android.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

/**
 * The upload decision matrix, with no Android types under it. This is the test that would have
 * caught the HEIC bug: a content URI whose provider size is NULL must never be read as "zero
 * bytes", and HEIC must never ride the wire as HEIC.
 */
public class UploadSourceTest{
	@Test
	public void contentUriWithUnknownSizeIsStagedAndNotRejected(){
		UploadSource src=new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, "image/heic", "IMG_0001.heic");
		assertEquals(UploadSource.Stage.COPY_TO_CACHE, src.stage());
		// The whole bug: a provider that reports nothing is not a file that contains nothing.
		assertFalse(src.isEmptyFile());
		assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG, src.format());
	}

	@Test
	public void contentUriReportingZeroIsStillStaged(){
		// A provider that answers 0 is indistinguishable from a NULL column read through getInt();
		// only the verified copy after staging may decide that the upload is empty.
		UploadSource src=new UploadSource(UploadSource.Kind.CONTENT, 0, "image/jpeg", "a.jpg");
		assertFalse(src.isEmptyFile());
		assertEquals(UploadSource.Stage.COPY_TO_CACHE, src.stage());
		assertEquals(UploadSource.Format.SEND_AS_IS, src.format());
	}

	@Test
	public void emptyFileOnDiskIsRejectedBeforeAnyRequest(){
		UploadSource src=new UploadSource(UploadSource.Kind.FILE, 0, "image/jpeg", "a.jpg");
		assertTrue(src.isEmptyFile());
		assertEquals(UploadSource.Format.REJECT_EMPTY, src.format());
		assertEquals(UploadSource.Stage.USE_FILE, src.stage());
	}

	@Test
	public void heicIsTranscodedByMimeRegardlessOfCase(){
		assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG,
				new UploadSource(UploadSource.Kind.CONTENT, 12, "IMAGE/HEIF", "x.bin").format());
		assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG,
				new UploadSource(UploadSource.Kind.FILE, 12, "image/heic", "x.heic").format());
	}

	@Test
	public void heicIsTranscodedByExtensionWhenProviderGivesNoType(){
		assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG,
				new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, null, "PXL_20261031.HEIC").format());
	}

	@Test
	public void ordinaryImageAndVideoPassThrough(){
		assertEquals(UploadSource.Format.SEND_AS_IS,
				new UploadSource(UploadSource.Kind.CONTENT, 12, "image/jpeg", "a.jpg").format());
		assertEquals(UploadSource.Format.SEND_AS_IS,
				new UploadSource(UploadSource.Kind.CONTENT, 12, "video/mp4", "a.mp4").format());
		assertFalse(new UploadSource(UploadSource.Kind.CONTENT, 12, "video/mp4", "a.mp4").isImage());
	}

	@Test
	public void realSizeOnDiskIsKept(){
		UploadSource src=new UploadSource(UploadSource.Kind.FILE, 2_689_818, "image/heic", "20261031_181921.heic");
		assertEquals(2_689_818, src.size());
		assertEquals(UploadSource.Stage.USE_FILE, src.stage());
	}

	@Test
	public void heicWithoutNameOrMimeIsNotMistakenForHeic(){
		UploadSource src=new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, null, null);
		assertFalse(src.isHeic());
		assertEquals(UploadSource.Format.SEND_AS_IS, src.format());
	}

	@Test
	public void heicDetectionIsLocaleSafe(){
		// t_8ab12a49 M3: Turkish/Azerbaijani lowercasing maps I→ı, so a default-locale
		// toLowerCase() turns "IMG_0001.HEIC" into "ımg_0001.heıc" and silently disables
		// HEIC detection — the exact 500 this branch fixes. Detection must use Locale.ROOT.
		Locale previous=Locale.getDefault();
		Locale.setDefault(new Locale("tr", "TR"));
		try{
			UploadSource byName=new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, null, "IMG_0001.HEIC");
			assertTrue(byName.isHeic());
			assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG, byName.format());
			assertTrue(UploadSource.isHeicMime("IMAGE/HEIC"));
			assertEquals(UploadSource.Format.TRANSCODE_TO_JPEG,
					new UploadSource(UploadSource.Kind.CONTENT, 12, "IMAGE/HEIF", "x.bin").format());
		}finally{
			Locale.setDefault(previous);
		}
	}
}
