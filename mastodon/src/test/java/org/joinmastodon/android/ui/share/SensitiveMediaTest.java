package org.joinmastodon.android.ui.share;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensitiveMediaTest{

	@Test
	public void statusSensitiveBlursEveryTile(){
		assertTrue(SensitiveMedia.isBlurred(true, false));
		assertTrue(SensitiveMedia.isBlurred(true, true));
		boolean[] flags=SensitiveMedia.blurFlags(true, new boolean[]{false, false, false});
		assertArrayEquals(new boolean[]{true, true, true}, flags);
	}

	@Test
	public void attachmentSensitiveBlursThatTileOnly(){
		assertTrue(SensitiveMedia.isBlurred(false, true));
		assertFalse(SensitiveMedia.isBlurred(false, false));
		boolean[] flags=SensitiveMedia.blurFlags(false, new boolean[]{false, true, false});
		assertArrayEquals(new boolean[]{false, true, false}, flags);
	}

	@Test
	public void notSensitiveBlursNothing(){
		boolean[] flags=SensitiveMedia.blurFlags(false, new boolean[]{false, false});
		assertArrayEquals(new boolean[]{false, false}, flags);
		assertFalse(SensitiveMedia.anyBlurred(false, new boolean[]{false, false}));
	}

	@Test
	public void anySensitiveAttachmentDetected(){
		assertTrue(SensitiveMedia.anyBlurred(false, new boolean[]{false, true, false}));
		assertTrue(SensitiveMedia.anyBlurred(true, new boolean[]{false, false}));
		assertFalse(SensitiveMedia.anyBlurred(false, new boolean[]{}));
	}

	@Test
	public void emptySetStaysClear(){
		assertFalse(SensitiveMedia.anyBlurred(true, new boolean[]{}));
		assertArrayEquals(new boolean[]{}, SensitiveMedia.blurFlags(true, new boolean[]{}));
	}
}
