package org.joinmastodon.android.ui.photoviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PhotoViewerFilenameSanitizerTest{

	@Test
	public void stripsDirectoryTraversalAndPathSeparators(){
		assertEquals("passwd", PhotoViewer.sanitizeFileName("../../etc/passwd"));
		assertEquals("bar.jpg", PhotoViewer.sanitizeFileName("foo\\..\\bar.jpg"));
		assertEquals("file_name.png", PhotoViewer.sanitizeFileName("file..name.png"));
	}

	@Test
	public void stripsLeadingDotsAndControlCharacters(){
		assertEquals("hidden.png", PhotoViewer.sanitizeFileName("...hidden.png"));
		assertEquals("photo__.png", PhotoViewer.sanitizeFileName("photo\r\n.png"));
		assertNull(PhotoViewer.sanitizeFileName("..."));
		assertNull(PhotoViewer.sanitizeFileName("   "));
		assertNull(PhotoViewer.sanitizeFileName(null));
	}

	@Test
	public void preservesSafeFilenames(){
		assertEquals("normal_photo-1.webp", PhotoViewer.sanitizeFileName("normal_photo-1.webp"));
	}
}
