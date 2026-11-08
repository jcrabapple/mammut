package org.joinmastodon.android.ui.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure model of the vertical block sequence on a share card (spec §4/§6/§11).
 * No Android types, so the gap decisions that used to live inline in
 * {@code ShareImageRenderer.measureAndLayout} are unit-testable on the JVM.
 *
 * The renderer used to derive the header gap from {@code bodyPresent}, but a
 * poll post's text is the poll question — the body view is never inserted, so
 * the block still owed its own gap and two gaps stacked (96 px). The rule here
 * is that every gap belongs to the block that follows it, derived from the
 * blocks actually inserted.
 *
 * Block order is fixed by spec §11: CW and body first (§6), then quote, then
 * poll ("quote block above poll block"), then media ("both above media").
 */
public class CardFlow{
	/** Gap header → first text block (spec §4). */
	public static final int HEADER_BODY_GAP=56;
	/** Gap between two content blocks (spec §11). */
	public static final int BLOCK_GAP=40;
	/** Gap above the media grid (spec §4 "body → media"; §6 media-only posts close the 56 px gap). */
	public static final int BODY_MEDIA_GAP=40;

	public enum Kind{
		/** "CW: <spoilerText>" line (spec §6). */
		CW,
		/** The post's body text as its own block (spec §4). */
		BODY,
		/** Quoted status block (spec §11). */
		QUOTE,
		/** Poll block; the question is the post's own text when one exists (spec §11). */
		POLL,
		/** Media attachment grid (spec §5). */
		MEDIA
	}

	/** Which parts of the status exist. {@code bodyText} is the parsed post text (non-empty). */
	public static class Input{
		public boolean cw, bodyText, quote, poll, media;

		public Input(boolean cw, boolean bodyText, boolean quote, boolean poll, boolean media){
			this.cw=cw;
			this.bodyText=bodyText;
			this.quote=quote;
			this.poll=poll;
			this.media=media;
		}
	}

	/** One inserted block and the vertical gap above it. */
	public static class Entry{
		public final Kind kind;
		/** Vertical space inserted directly above this block. Always > 0. */
		public final int gapAbove;
		/** POLL only: the block renders {@code bodyText} as its question. */
		public final boolean pollQuestion;

		private Entry(Kind kind, int gapAbove, boolean pollQuestion){
			this.kind=kind;
			this.gapAbove=gapAbove;
			this.pollQuestion=pollQuestion;
		}
	}

	/**
	 * The ordered blocks for the card, each with the gap that precedes it.
	 * Empty when the status has no content at all (no gap is owed to nothing).
	 *
	 * The body is its own block only when it is not consumed by the poll as the
	 * question ({@code bodyText && !poll}); the poll carries the question when
	 * {@code bodyText && poll}.
	 *
	 * Gap rules: the first block gets {@link #HEADER_BODY_GAP} (§4 header →
	 * body), except a lone media block which gets {@link #BODY_MEDIA_GAP}
	 * (§6: media-only closes the 56 px gap). Every block after the first gets
	 * {@link #BLOCK_GAP}. Because the gap belongs to the following block, two
	 * gaps can never stack above one block.
	 */
	public static List<Entry> build(Input in){
		boolean bodyBlock=in.bodyText && !in.poll;
		boolean pollQuestion=in.bodyText && in.poll;
		ArrayList<Kind> kinds=new ArrayList<>(5);
		if(in.cw)
			kinds.add(Kind.CW);
		if(bodyBlock)
			kinds.add(Kind.BODY);
		if(in.quote)
			kinds.add(Kind.QUOTE);
		if(in.poll)
			kinds.add(Kind.POLL);
		if(in.media)
			kinds.add(Kind.MEDIA);
		if(kinds.isEmpty())
			return Collections.emptyList();

		ArrayList<Entry> entries=new ArrayList<>(kinds.size());
		for(int i=0;i<kinds.size();i++){
			Kind kind=kinds.get(i);
			int gapAbove;
			if(i==0)
				gapAbove=kind==Kind.MEDIA ? BODY_MEDIA_GAP : HEADER_BODY_GAP;
			else
				gapAbove=BLOCK_GAP;
			entries.add(new Entry(kind, gapAbove, kind==Kind.POLL && pollQuestion));
		}
		return entries;
	}
}
