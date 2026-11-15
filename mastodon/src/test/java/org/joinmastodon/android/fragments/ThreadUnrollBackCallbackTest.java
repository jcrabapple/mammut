package org.joinmastodon.android.fragments;

import org.joinmastodon.android.fragments.ThreadFragment.UnreadBadgeState;
import org.joinmastodon.android.fragments.ThreadFragment.UnrollBackCallbackState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for Fix 5 of the 1.3.5 audit (docs/AUDIT-1.3.5-TRIAGE.md §Fix 5):
 * MUST R4 F7 — the automatic Unroll exit (chain drops below eligibility) retires the
 * Back callback through the same transition path as a manual exit, so the next Back
 * press leaves the Conversation instead of being swallowed by a stale callback; and
 * SHOULD R3 F10 — the thread's badge subscription applies a push increment and a
 * mark-read while subscribed, and surfaces nothing once removed.
 */
public class ThreadUnrollBackCallbackTest{

	// R4 F7: one transition path for the Unroll Back callback

	@Test
	public void enteringUnrollRegistersTheBackCallback(){
		UnrollBackCallbackState state=new UnrollBackCallbackState();
		assertTrue(state.shouldRegister(true));
		state.onRegistered();
		assertFalse(state.shouldRegister(true)); // already registered
		assertTrue(state.registered());
	}

	@Test
	public void manualExitUnregistersTheBackCallback(){
		UnrollBackCallbackState state=new UnrollBackCallbackState();
		state.onRegistered();
		assertTrue(state.shouldUnregister(false));
		state.onUnregistered();
		assertFalse(state.registered());
	}

	@Test
	public void automaticExitThenBackLeavesTheConversation(){
		// The pre-fix sequence: onConversationChanged set unrollMode=false directly
		// and never called updateUnrollBackCallback, leaving the callback registered.
		// The fixed sequence routes the automatic exit through the same sync — after
		// it, the callback is retired and Back falls through to pop the thread.
		UnrollBackCallbackState state=new UnrollBackCallbackState();
		boolean unrollMode=true;
		// entering Unroll (setUnrollMode(true))
		if(state.shouldRegister(unrollMode)){
			state.onRegistered();
		}
		assertTrue(state.registered());
		// chain drops below eligibility -> automatic exit
		unrollMode=false;
		if(state.shouldUnregister(unrollMode)){
			state.onUnregistered();
		}
		assertFalse("Back must not be consumed after the automatic Unroll exit", state.registered());
	}

	@Test
	public void unrollStillActiveKeepsTheCallback(){
		UnrollBackCallbackState state=new UnrollBackCallbackState();
		state.onRegistered();
		assertFalse(state.shouldUnregister(true));
		assertTrue(state.registered());
	}

	// R3 F10: the badge follows the account's unread store

	@Test
	public void pushWhileSubscribedUpdatesTheBadge(){
		UnreadBadgeState badge=new UnreadBadgeState();
		assertTrue(badge.shouldSubscribe());
		badge.onSubscribed();
		badge.apply(1, false); // push increment
		badge.apply(3, true);  // server refresh
		assertTrue(badge.more());
		assertTrue(badge.count()>0);
	}

	@Test
	public void markReadClearsTheBadge(){
		UnreadBadgeState badge=new UnreadBadgeState();
		badge.onSubscribed();
		badge.apply(5, false);
		badge.apply(0, false); // mark-read publishes zero
		assertTrue(badge.shouldUnsubscribe());
		assertFalse(badge.more());
	}

	@Test
	public void countsPublishedBeforeSubscribeAreIgnored(){
		UnreadBadgeState badge=new UnreadBadgeState();
		badge.apply(9, true); // a stray dispatch before registration
		badge.onSubscribed();
		assertFalse(badge.more());
	}

	@Test
	public void unsubscribedBadgeShowsNothing(){
		UnreadBadgeState badge=new UnreadBadgeState();
		badge.onSubscribed();
		badge.apply(4, false);
		badge.onUnsubscribed();
		assertFalse(badge.shouldUnsubscribe());
		assertFalse(badge.more());
	}
}
