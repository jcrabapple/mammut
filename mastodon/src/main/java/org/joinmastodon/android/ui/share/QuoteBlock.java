package org.joinmastodon.android.ui.share;

import org.joinmastodon.android.model.Quote;

/**
 * Pure geometry and gating for the quote block on a share card (spec §11).
 * No Android types, so the clamp and column-width rules are unit-testable on
 * the JVM.
 */
public class QuoteBlock{
	/** Rounded block corner (card px). */
	public static final int CORNER_RADIUS=20;
	/** Outline stroke width (card px). */
	public static final int OUTLINE_WIDTH=1;
	/** Padding between the outline and the block contents. */
	public static final int INNER_PADDING=32;
	/** Quoted author name and handle size (spec's 28 sp maps 1:1 to px). */
	public static final int AUTHOR_SIZE=28;
	/** Quoted text size (spec's 32 sp maps 1:1 to px). */
	public static final int TEXT_SIZE=32;
	/** Quoted text is clamped to this many lines. */
	public static final int MAX_TEXT_LINES=4;
	/** Right-hand thumbnail of the quoted status's first media attachment. */
	public static final int MEDIA_TILE=160;
	/** Gap between the text column and the media tile. */
	public static final int MEDIA_GAP=24;

	/**
	 * Timeline gate (StatusDisplayItem): a quote renders only when accepted and
	 * the quoted status object is attached; a quoted status that itself quotes is
	 * not recursed into — the renderer simply never looks at quote.quote.
	 */
	public static boolean shouldRender(Quote quote){
		return quote!=null && quote.state==Quote.State.ACCEPTED && quote.quotedStatus!=null;
	}

	/** §11: the quoted status's first media attachment becomes the right-hand tile. */
	public static boolean hasMediaTile(int attachmentCount){
		return attachmentCount>0;
	}

	/** §11 clamp: quoted text never exceeds {@link #MAX_TEXT_LINES} lines. */
	public static int clampedLineCount(int measuredLines){
		if(measuredLines<0)
			return 0;
		return Math.min(measuredLines, MAX_TEXT_LINES);
	}

	/** Text column width inside a block of the given width. */
	public static int textColumnWidth(int blockWidth, boolean hasMediaTile){
		int w=blockWidth-INNER_PADDING*2;
		if(hasMediaTile)
			w-=MEDIA_TILE+MEDIA_GAP;
		return Math.max(0, w);
	}
}
