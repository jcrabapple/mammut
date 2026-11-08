package org.joinmastodon.android.ui.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure solver for the media grid on a share card. Implements the exact tile
 * geometry from the post-as-image spec §5. No Android types, so it is unit-testable
 * on the JVM.
 *
 * The grid is fixed at 968 px wide (1080 px card minus 56 px side padding) with an
 * 8 px gap. Attachments beyond the fourth are ignored; the fourth tile then carries
 * a "+N" badge (flag on the returned tile).
 */
public class MediaGridSolver{
	public static final int GRID_WIDTH=968;
	public static final int GAP=8;
	public static final int TILE=480;
	public static final int SINGLE_MAX_HEIGHT=726; // 968 * 3/4
	public static final int MAX_TILES=4;

	/** Aspect ratio (width/height) used when an attachment has no known dimensions. */
	public static final float DEFAULT_ASPECT=4f/3f;

	public static class Tile{
		public final int x, y, width, height;
		/** True when this tile must show a "+N" badge for the ignored attachments. */
		public final boolean moreBadge;

		public Tile(int x, int y, int width, int height, boolean moreBadge){
			this.x=x;
			this.y=y;
			this.width=width;
			this.height=height;
			this.moreBadge=moreBadge;
		}

		public int right(){
			return x+width;
		}

		public int bottom(){
			return y+height;
		}

		@Override
		public String toString(){
			return "Tile{"+x+", "+y+" "+width+"x"+height+(moreBadge ? " +N" : "")+'}';
		}
	}

	/**
	 * Compute the tiles for {@code count} attachments.
	 *
	 * @param count number of attachments on the status (any value; only the first
	 *              {@link #MAX_TILES} are laid out)
	 * @param firstAspect width/height of the first attachment; values &le; 0 (unknown)
	 *                    fall back to {@link #DEFAULT_ASPECT}
	 */
	public static List<Tile> solve(int count, float firstAspect){
		if(count<=0)
			return Collections.emptyList();
		int shown=Math.min(count, MAX_TILES);
		ArrayList<Tile> tiles=new ArrayList<>(shown);
		boolean badge=count>MAX_TILES;
		switch(shown){
			case 1 -> {
				int height=singleHeight(firstAspect);
				tiles.add(new Tile(0, 0, GRID_WIDTH, height, false));
			}
			case 2 -> {
				tiles.add(new Tile(0, 0, TILE, TILE, false));
				tiles.add(new Tile(TILE+GAP, 0, TILE, TILE, false));
			}
			case 3 -> {
				int small=(TILE-GAP)/2; // 236
				tiles.add(new Tile(0, 0, TILE, TILE, false));
				tiles.add(new Tile(TILE+GAP, 0, TILE, small, false));
				tiles.add(new Tile(TILE+GAP, small+GAP, TILE, small, false));
			}
			default -> {
				for(int i=0;i<4;i++){
					tiles.add(new Tile((i%2)*(TILE+GAP), (i/2)*(TILE+GAP), TILE, TILE, badge && i==3));
				}
			}
		}
		return tiles;
	}

	/**
	 * Height of the single-attachment tile: the image's aspect is preserved with the
	 * height capped at 4:3 (726 px); anything taller than 4:3 is centre-cropped into
	 * the 968×726 box.
	 */
	public static int singleHeight(float aspect){
		if(aspect<=0)
			aspect=DEFAULT_ASPECT;
		return Math.min(SINGLE_MAX_HEIGHT, Math.round(GRID_WIDTH/aspect));
	}

	/**
	 * Total height the grid occupies for the given inputs; 0 when there is no media.
	 */
	public static int gridHeight(int count, float firstAspect){
		List<Tile> tiles=solve(count, firstAspect);
		int max=0;
		for(Tile t:tiles)
			max=Math.max(max, t.bottom());
		return max;
	}
}
