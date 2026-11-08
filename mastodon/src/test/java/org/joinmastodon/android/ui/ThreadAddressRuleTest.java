package org.joinmastodon.android.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Address line (docs/ADDRESS-LINE-SPEC.md §1, §4, §5): the full truth table of
 * {@link ThreadAddressRule} with the resource wording inlined, so the tests pin
 * the exact visible and accessibility strings the fragment passes as templates.
 */
public class ThreadAddressRuleTest{

	private static ThreadAddressRule.Input input(){
		ThreadAddressRule.Input in=new ThreadAddressRule.Input();
		in.tmplReplyingTo="Replying to %s";
		in.tmplReplyingToNotShown="Replying to %s · parent post not shown";
		in.tmplReplyingToUnresolved="Replying to a post that isn't shown";
		in.tmplThreadNumbered="Thread %1$d/%2$d";
		in.tmplThreadBare="Thread";
		in.tmplA11yReplyingTo="Replying to %s";
		in.tmplA11yReplyingToNotShown="Replying to %s, parent post not shown";
		in.tmplA11yReplyingToUnresolved="Replying to a post that isn't shown";
		in.tmplA11yThreadNumbered="Self-reply, thread %1$d of %2$d";
		in.tmplA11yThreadBare="Self-reply, part of a thread";
		return in;
	}

	//region §1.6: not a reply

	@Test
	public void notAReplyRendersNothing(){
		ThreadAddressRule.Input in=input();
		in.reply=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.NONE, r.state);
		assertNull(r.visible);
		assertNull(r.accessibility);
	}

	//endregion

	//region §1.1: cross-author, parent present

	@Test
	public void crossAuthorParentPresent(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName="Mara";
		in.parentPostPresent=true;
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_PRESENT, r.state);
		assertEquals("Replying to Mara", r.visible);
		assertEquals("Replying to Mara", r.accessibility);
	}

	//endregion

	//region §1.2: cross-author, account known, parent post absent

	@Test
	public void crossAuthorParentAbsent(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName="Mara";
		in.parentPostPresent=false; // account resolved, post not in the fetched context
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_PARENT_NOT_SHOWN, r.state);
		assertEquals("Replying to Mara · parent post not shown", r.visible);
		assertEquals("Replying to Mara, parent post not shown", r.accessibility);
	}

	//endregion

	//region §1.3: cross-author, account unresolved

	@Test
	public void crossAuthorUnresolved(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName=null; // never an id, never an invented name
		in.parentPostPresent=false;
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_UNRESOLVED, r.state);
		assertEquals("Replying to a post that isn't shown", r.visible);
		assertEquals("Replying to a post that isn't shown", r.accessibility);
	}

	@Test
	public void resolvedNameWinsOverAbsentPost(){
		// A name without the post is §1.2; only the total absence of the account
		// may fall to §1.3.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName="Mara";
		in.parentPostPresent=false;
		in.sameAuthor=false;
		assertEquals(ThreadAddressRule.State.REPLY_PARENT_NOT_SHOWN, ThreadAddressRule.evaluate(in).state);
	}

	//endregion

	//region §1.4/§1.5: self-reply

	@Test
	public void selfReplyWithKnownBounds(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.parentName=null; // never needed for the self-reply badge
		in.chainIndex=1; // 0-based: the second chain post
		in.chainSize=4;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_NUMBERED, r.state);
		assertEquals("Thread 2/4", r.visible);
		assertEquals("Self-reply, thread 2 of 4", r.accessibility);
	}

	@Test
	public void selfReplyChainIndexIsOneBasedInText(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.chainIndex=0;
		in.chainSize=3;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals("Thread 1/3", r.visible);
		assertEquals("Self-reply, thread 1 of 3", r.accessibility);
	}

	@Test
	public void selfReplyWithoutChainBoundsIsBare(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.chainIndex=-1; // chain ineligible or status not a member
		in.chainSize=0;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_BARE, r.state);
		assertEquals("Thread", r.visible);
		assertEquals("Self-reply, part of a thread", r.accessibility);
	}

	@Test
	public void selfReplyIgnoresParentName(){
		// A self-reply is decidable from the author id alone; even if the parent
		// account resolved, the badge never becomes "Replying to {me}".
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.parentName="Mara";
		in.parentPostPresent=true;
		in.chainIndex=-1;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_BARE, r.state);
		assertEquals("Thread", r.visible);
	}

	//endregion

	//region §4: the 24-character clamp

	@Test
	public void shortNameIsNotClamped(){
		String name="Mara";
		assertEquals(name, ThreadAddressRule.clampName(name));
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName=name;
		in.parentPostPresent=true;
		assertEquals("Replying to Mara", ThreadAddressRule.evaluate(in).visible);
	}

	@Test
	public void exactlyTwentyFourCharactersStays(){
		String name="abcdefghijklmnopqrstuvwx"; // 24
		assertEquals(name, ThreadAddressRule.clampName(name));
	}

	@Test
	public void longNameIsClampedWithEllipsis(){
		String name="abcdefghijklmnopqrstuvwxyz"; // 26
		assertEquals("abcdefghijklmnopqrstuvwx…", ThreadAddressRule.clampName(name));
	}

	@Test
	public void clampedVisibleNameKeepsFullNameInAccessibility(){
		String name="A very long display name that will not fit"; // 42 chars
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName=name;
		in.parentPostPresent=true;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals("Replying to A very long display name…", r.visible);
		assertEquals("Replying to A very long display name that will not fit", r.accessibility);
	}

	@Test
	public void notShownSuffixSurvivesTheClamp(){
		// §4: "· parent post not shown" is a suffix, not part of the name — it
		// must never be truncated by the clamp.
		String name="A very long display name that will not fit";
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName=name;
		in.parentPostPresent=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals("Replying to A very long display name… · parent post not shown", r.visible);
		assertEquals("Replying to A very long display name that will not fit, parent post not shown", r.accessibility);
	}

	@Test
	public void clampCountsCodePointsNotChars(){
		// 24 emoji (supplementary code points, 2 chars each) fit exactly; one more
		// clamps without splitting a surrogate pair.
		StringBuilder sb=new StringBuilder();
		for(int i=0;i<24;i++)
			sb.append("😀");
		assertEquals(24, sb.toString().codePointCount(0, sb.length()));
		assertEquals(sb.toString(), ThreadAddressRule.clampName(sb.toString()));
		StringBuilder sb25=new StringBuilder(sb).append("😀");
		assertEquals(sb.toString()+"…", ThreadAddressRule.clampName(sb25.toString()));
	}

	@Test
	public void emptyNameIsNotClampedToEllipsis(){
		// Username fallback is the fragment's job; an empty string stays empty.
		assertEquals("", ThreadAddressRule.clampName(""));
	}

	//endregion

	//region M4: a blank parent name is unresolved, never a dangling row

	@Test
	public void emptyParentNameIsUnresolved(){
		// The fragment must not send this (M4), but the rule guards anyway:
		// no "Replying to " with nothing after it, in either string.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName="";
		in.parentPostPresent=true;
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_UNRESOLVED, r.state);
		assertEquals("Replying to a post that isn't shown", r.visible);
		assertEquals("Replying to a post that isn't shown", r.accessibility);
	}

	@Test
	public void whitespaceOnlyParentNameIsUnresolved(){
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName="   ";
		in.parentPostPresent=false;
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_UNRESOLVED, r.state);
		assertEquals("Replying to a post that isn't shown", r.visible);
		assertEquals("Replying to a post that isn't shown", r.accessibility);
	}

	//endregion

	//region M5: a reply id without an account id still renders §1.3

	@Test
	public void replyWithOnlyPostIdIsUnresolved(){
		// M5: the fragment feeds in.reply=true when only inReplyToId is
		// present; with no account id, sameAuthor is false and no name
		// resolves — the row must be §1.3, never NONE.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.parentName=null;
		in.parentPostPresent=false;
		in.sameAuthor=false;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.REPLY_UNRESOLVED, r.state);
		assertEquals("Replying to a post that isn't shown", r.visible);
		assertEquals("Replying to a post that isn't shown", r.accessibility);
	}

	//endregion

	//region M-review: chain bounds are defensive — "Thread n/N" only in-bounds

	@Test
	public void selfReplyWithZeroChainSizeIsBare(){
		// Never "Thread 1/0": a zero size means the chain is absent.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.chainIndex=0;
		in.chainSize=0;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_BARE, r.state);
		assertEquals("Thread", r.visible);
		assertEquals("Self-reply, part of a thread", r.accessibility);
	}

	@Test
	public void selfReplyWithNegativeChainIndexIsBare(){
		// Never "Thread 0/0": a negative index means the position is absent.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.chainIndex=-1;
		in.chainSize=3;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_BARE, r.state);
		assertEquals("Thread", r.visible);
	}

	@Test
	public void selfReplyWithOutOfBoundsChainIndexIsBare(){
		// Never "Thread 4/3": an index at or past the size is not a claim
		// the row may print.
		ThreadAddressRule.Input in=input();
		in.reply=true;
		in.sameAuthor=true;
		in.chainIndex=3;
		in.chainSize=3;
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		assertEquals(ThreadAddressRule.State.THREAD_BARE, r.state);
		assertEquals("Thread", r.visible);
	}

	//endregion
}
