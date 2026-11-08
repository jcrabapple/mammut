package org.joinmastodon.android.ui.share;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The vertical block sequence on a share card (review M2): every gap belongs to
 * the block that follows it, derived from the blocks actually inserted. A poll
 * post's text is the poll question, so {@code bodyPresent} must not drive the
 * header gap — that stacked a 56 px header gap and a 40 px block gap (96 px)
 * above the poll.
 */
public class CardFlowTest{

	private static CardFlow.Entry entry(List<CardFlow.Entry> flow, int index){
		return flow.get(index);
	}

	/**
	 * The invariants every non-empty flow must satisfy: no two gaps are ever
	 * adjacent (each gap sits directly on the block it precedes), the 56 px
	 * header gap appears at most once and only as the first block's gap (so
	 * always with a block after it), and each block-to-block transition is the
	 * 40 px block gap (a lone first media block is the §6 exception at 40 px).
	 */
	private static void assertFlowInvariants(List<CardFlow.Entry> flow){
		assertFalse(flow.isEmpty());
		for(CardFlow.Entry e:flow){
			assertTrue("gap above "+e.kind+" must be positive", e.gapAbove>0);
			if(e.kind==CardFlow.Kind.POLL)
				continue; // pollQuestion asserted per scenario
			else
				assertFalse(e.pollQuestion);
		}
		int headerGaps=0;
		for(int i=0;i<flow.size();i++){
			CardFlow.Entry e=flow.get(i);
			if(i==0){
				if(e.kind==CardFlow.Kind.MEDIA)
					assertEquals(CardFlow.BODY_MEDIA_GAP, e.gapAbove);
				else{
					assertEquals(CardFlow.HEADER_BODY_GAP, e.gapAbove);
					headerGaps++;
				}
			}else{
				// Exactly one gap separates each pair of blocks: the following
				// block's own gapAbove. Nothing may insert a second one.
				assertEquals("gap above block-to-block transition "+i, CardFlow.BLOCK_GAP, e.gapAbove);
			}
		}
		// The 56 px header gap appears at most once — only ever as the first
		// block's gap, so always with a block after it (a lone first media block
		// uses BODY_MEDIA_GAP instead, per §6).
		assertTrue("HEADER_BODY_GAP appears at most once", headerGaps<=1);
	}

	private static void assertKinds(List<CardFlow.Entry> flow, CardFlow.Kind... kinds){
		assertEquals(kinds.length, flow.size());
		for(int i=0;i<kinds.length;i++)
			assertEquals(kinds[i], flow.get(i).kind);
	}

	//region Required scenarios

	@Test
	public void textOnly(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, true, false, false, false));
		assertKinds(flow, CardFlow.Kind.BODY);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertFlowInvariants(flow);
	}

	@Test
	public void mediaOnly(){
		// §6: a media-only post omits the body block and closes the 56 px gap.
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, false, false, false, true));
		assertKinds(flow, CardFlow.Kind.MEDIA);
		assertEquals(CardFlow.BODY_MEDIA_GAP, entry(flow, 0).gapAbove);
		assertFlowInvariants(flow);
	}

	@Test
	public void cwPlusText(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(true, true, false, false, false));
		assertKinds(flow, CardFlow.Kind.CW, CardFlow.Kind.BODY);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 1).gapAbove);
		assertFlowInvariants(flow);
	}

	@Test
	public void pollWithBodyAsQuestion(){
		// The M2 regression: the body text is the poll question, so no body view
		// is inserted — the poll owes exactly one gap, the 56 px header gap.
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, true, false, true, false));
		assertKinds(flow, CardFlow.Kind.POLL);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertTrue(entry(flow, 0).pollQuestion);
		assertFlowInvariants(flow);
	}

	@Test
	public void pollWithCwAndNoBodyText(){
		// The other M2 case: CW present but no body text — CW gets the header
		// gap and the poll gets one 40 px block gap, never 56+40.
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(true, false, false, true, false));
		assertKinds(flow, CardFlow.Kind.CW, CardFlow.Kind.POLL);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 1).gapAbove);
		assertFalse(entry(flow, 1).pollQuestion);
		assertFlowInvariants(flow);
	}

	@Test
	public void quotePlusText(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, true, true, false, false));
		assertKinds(flow, CardFlow.Kind.BODY, CardFlow.Kind.QUOTE);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 1).gapAbove);
		assertFlowInvariants(flow);
	}

	@Test
	public void quotePlusPollPlusMedia(){
		// §11 ordering: quote above poll, both above media; the poll consumes
		// the body text as its question.
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, true, true, true, true));
		assertKinds(flow, CardFlow.Kind.QUOTE, CardFlow.Kind.POLL, CardFlow.Kind.MEDIA);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 1).gapAbove);
		assertTrue(entry(flow, 1).pollQuestion);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 2).gapAbove);
		assertFlowInvariants(flow);
	}

	//endregion

	//region Edge cases

	@Test
	public void emptyStatusHasNoBlocksAndNoGaps(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, false, false, false, false));
		assertTrue(flow.isEmpty());
	}

	@Test
	public void pollWithoutBodyTextHasNoQuestion(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, false, false, true, false));
		assertKinds(flow, CardFlow.Kind.POLL);
		assertFalse(entry(flow, 0).pollQuestion);
		assertFlowInvariants(flow);
	}

	@Test
	public void fullStackCwQuotePollMedia(){
		// With body text and a poll, the body is consumed as the poll question:
		// CW, quote, poll, media.
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(true, true, true, true, true));
		assertKinds(flow, CardFlow.Kind.CW, CardFlow.Kind.QUOTE, CardFlow.Kind.POLL, CardFlow.Kind.MEDIA);
		assertTrue(entry(flow, 2).pollQuestion);
		assertFlowInvariants(flow);
	}

	@Test
	public void quoteOnly(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, false, true, false, false));
		assertKinds(flow, CardFlow.Kind.QUOTE);
		assertEquals(CardFlow.HEADER_BODY_GAP, entry(flow, 0).gapAbove);
		assertFlowInvariants(flow);
	}

	@Test
	public void textPlusMedia(){
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(false, true, false, false, true));
		assertKinds(flow, CardFlow.Kind.BODY, CardFlow.Kind.MEDIA);
		assertEquals(CardFlow.BLOCK_GAP, entry(flow, 1).gapAbove);
		assertFlowInvariants(flow);
	}

	//endregion
}
