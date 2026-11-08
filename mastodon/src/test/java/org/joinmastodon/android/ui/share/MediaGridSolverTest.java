package org.joinmastodon.android.ui.share;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaGridSolverTest{

	private static void assertFitsAndNoOverlap(List<MediaGridSolver.Tile> tiles){
		for(MediaGridSolver.Tile t:tiles){
			assertTrue("tile starts inside grid", t.x>=0 && t.y>=0);
			assertTrue("tile ends inside grid width", t.right()<=MediaGridSolver.GRID_WIDTH);
		}
		for(int i=0;i<tiles.size();i++){
			for(int j=i+1;j<tiles.size();j++){
				MediaGridSolver.Tile a=tiles.get(i), b=tiles.get(j);
				boolean overlap=a.x<b.right() && b.x<a.right() && a.y<b.bottom() && b.y<a.bottom();
				assertFalse("tiles "+i+" and "+j+" overlap", overlap);
			}
		}
	}

	@Test
	public void zeroAttachmentsYieldsEmptyGrid(){
		assertTrue(MediaGridSolver.solve(0, 0).isEmpty());
		assertEquals(0, MediaGridSolver.gridHeight(0, 0));
	}

	@Test
	public void singleImageUnknownAspectUsesDefault(){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(1, 0);
		assertEquals(1, tiles.size());
		MediaGridSolver.Tile t=tiles.get(0);
		assertEquals(0, t.x);
		assertEquals(0, t.y);
		assertEquals(968, t.width);
		assertEquals(726, t.height); // 4:3 default
		assertFalse(t.moreBadge);
	}

	@Test
	public void singleImageAspectPreservedWithinCap(){
		// Square image: 968 wide → 968 tall... capped at 726 by the 4:3 cap.
		assertEquals(726, MediaGridSolver.singleHeight(1f));
		// 16:9 landscape: 968*9/16 = 544.5 → 545.
		assertEquals(545, MediaGridSolver.singleHeight(16f/9f));
		// Panorama keeps aspect (short tile).
		assertEquals(323, MediaGridSolver.singleHeight(3f));
		// Tall portrait is cropped to the 4:3 box.
		assertEquals(726, MediaGridSolver.singleHeight(9f/16f));
		assertEquals(726, MediaGridSolver.singleHeight(0.2f));
	}

	@Test
	public void singleTileHeightNeverExceedsCap(){
		assertEquals(MediaGridSolver.SINGLE_MAX_HEIGHT,
				MediaGridSolver.gridHeight(1, 0.01f)); // absurdly tall → cropped to 4:3
		// Absurdly wide keeps its aspect (short tile) — the formula is literal.
		assertEquals(10, MediaGridSolver.gridHeight(1, 100f));
	}

	@Test
	public void twoImagesSideBySide(){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(2, 1);
		assertEquals(2, tiles.size());
		assertEquals(480, tiles.get(0).width);
		assertEquals(480, tiles.get(0).height);
		assertEquals(488, tiles.get(1).x);
		assertEquals(480, tiles.get(1).width);
		assertEquals(480, MediaGridSolver.gridHeight(2, 1));
		assertFitsAndNoOverlap(tiles);
	}

	@Test
	public void threeImagesOneLargeTwoStacked(){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(3, 1);
		assertEquals(3, tiles.size());
		assertEquals(480, tiles.get(0).width);
		assertEquals(480, tiles.get(0).height);
		MediaGridSolver.Tile topRight=tiles.get(1), bottomRight=tiles.get(2);
		assertEquals(488, topRight.x);
		assertEquals(480, topRight.width);
		assertEquals(236, topRight.height);
		assertEquals(488, bottomRight.x);
		assertEquals(244, bottomRight.y); // 236 + 8 gap
		assertEquals(236, bottomRight.height);
		assertEquals(480, MediaGridSolver.gridHeight(3, 1));
		assertFitsAndNoOverlap(tiles);
	}

	@Test
	public void fourImagesTwoByTwo(){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(4, 1);
		assertEquals(4, tiles.size());
		assertEquals(968, MediaGridSolver.gridHeight(4, 1)); // 480 + 8 + 480
		assertFitsAndNoOverlap(tiles);
		for(MediaGridSolver.Tile t:tiles){
			assertEquals(480, t.width);
			assertEquals(480, t.height);
			assertFalse(t.moreBadge);
		}
	}

	@Test
	public void fivePlusImagesBadgeOnFourth(){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(7, 1);
		assertEquals(4, tiles.size());
		assertFitsAndNoOverlap(tiles);
		for(int i=0;i<3;i++)
			assertFalse("badge only on fourth tile", tiles.get(i).moreBadge);
		assertTrue("fourth tile carries the +N badge", tiles.get(3).moreBadge);
	}

	@Test
	public void gridWidthIsRespected(){
		for(int count=1;count<=9;count++){
			List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(count, 1.77f);
			assertFitsAndNoOverlap(tiles);
		}
	}
}
