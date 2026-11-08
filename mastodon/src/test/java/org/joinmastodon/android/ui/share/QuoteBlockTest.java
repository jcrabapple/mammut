package org.joinmastodon.android.ui.share;

import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Quote;
import org.joinmastodon.android.model.Status;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuoteBlockTest{

	//region Four-line clamp (§11)

	@Test
	public void shortQuoteIsNotClamped(){
		assertEquals(1, QuoteBlock.clampedLineCount(1));
		assertEquals(3, QuoteBlock.clampedLineCount(3));
	}

	@Test
	public void exactlyFourLinesFits(){
		assertEquals(4, QuoteBlock.clampedLineCount(4));
	}

	@Test
	public void longerQuoteClampsToFourLines(){
		// The spec's case: a quoted post longer than 4 lines.
		assertEquals(4, QuoteBlock.clampedLineCount(5));
		assertEquals(4, QuoteBlock.clampedLineCount(40));
	}

	@Test
	public void negativeLineCountIsZero(){
		assertEquals(0, QuoteBlock.clampedLineCount(-3));
	}

	//endregion

	//region Geometry

	@Test
	public void textColumnWidthWithoutTile(){
		// 968 block − 2 × 32 inner padding.
		assertEquals(904, QuoteBlock.textColumnWidth(968, false));
	}

	@Test
	public void textColumnWidthWithTile(){
		// ... minus the 160 px tile and its 24 px gap.
		assertEquals(720, QuoteBlock.textColumnWidth(968, true));
	}

	@Test
	public void textColumnNeverNegative(){
		assertEquals(0, QuoteBlock.textColumnWidth(100, true));
	}

	@Test
	public void mediaTilePresence(){
		assertFalse(QuoteBlock.hasMediaTile(0));
		assertTrue(QuoteBlock.hasMediaTile(1));
		assertTrue(QuoteBlock.hasMediaTile(4));
	}

	//endregion

	//region Render gate (timeline parity, no nested quotes)

	@Test
	public void nullQuoteDoesNotRender(){
		assertFalse(QuoteBlock.shouldRender(null));
	}

	@Test
	public void pendingQuoteDoesNotRender(){
		Quote quote=new Quote();
		quote.state=Quote.State.PENDING;
		quote.quotedStatus=statusWithAccount();
		assertFalse(QuoteBlock.shouldRender(quote));
	}

	@Test
	public void acceptedQuoteWithoutStatusDoesNotRender(){
		Quote quote=new Quote();
		quote.state=Quote.State.ACCEPTED;
		assertFalse(QuoteBlock.shouldRender(quote));
	}

	@Test
	public void acceptedQuoteWithStatusRenders(){
		Quote quote=new Quote();
		quote.state=Quote.State.ACCEPTED;
		quote.quotedStatus=statusWithAccount();
		assertTrue(QuoteBlock.shouldRender(quote));
		// A quoted status that itself quotes is handled by the renderer simply
		// never looking at quote.quotedStatus.quote — the gate is one level deep.
		Quote nested=new Quote();
		nested.state=Quote.State.ACCEPTED;
		nested.quotedStatus=statusWithAccount();
		quote.quotedStatus.quote=nested;
		assertTrue(QuoteBlock.shouldRender(quote));
	}

	private static Status statusWithAccount(){
		Status status=new Status();
		status.account=new Account();
		return status;
	}

	//endregion
}
