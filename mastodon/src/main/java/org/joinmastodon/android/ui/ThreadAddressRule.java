package org.joinmastodon.android.ui;

import java.util.Locale;

/**
 * MAMMUT: Address line (docs/ADDRESS-LINE-SPEC.md §1, §4, §5) — the pure decision
 * rule that picks the wording of the thread address row for one status in
 * Conversation.
 *
 * Every status in Conversation gets exactly one of six states (§1), decided only
 * by data the app actually has: whether it is a reply at all, whether the parent
 * author is this author, whether the parent account resolved to a name, whether
 * the parent post itself is in the fetched context, and — for self-replies —
 * whether the ConversationChain position is known. The rule never sees an account
 * id and never invents a name.
 *
 * The class is pure model (no Android types): the caller passes the wording
 * templates from resources, so the JVM tests drive the whole truth table with
 * literal strings, including the 24-character visible-name clamp (§4) and the
 * full name surviving in the accessibility string (§5).
 */
public class ThreadAddressRule{
	/** §4: the visible name is clamped to this many characters, then "…". */
	public static final int MAX_VISIBLE_NAME_LENGTH=24;

	/** The six states of §1. NONE renders no row at all. */
	public enum State{
		/** §1.1: reply to a different author, parent post present. */
		REPLY_PRESENT,
		/** §1.2: reply to a different author, account known, parent post absent. */
		REPLY_PARENT_NOT_SHOWN,
		/** §1.3: reply to a different author, account unresolved. Never an id, never a name. */
		REPLY_UNRESOLVED,
		/** §1.4: self-reply with a known chain position. */
		THREAD_NUMBERED,
		/** §1.5: self-reply, chain ineligible or bounds unknown. Bare, quiet. */
		THREAD_BARE,
		/** §1.6: not a reply, or top of the conversation. No row, no placeholder. */
		NONE
	}

	/**
	 * Plain inputs for one status. {@code chainIndex}/{@code chainSize} are -1/0
	 * when the chain position is absent or the chain is not eligible (§1.5);
	 * the caller (Conversation) is the one that asks ConversationChain.
	 */
	public static class Input{
		/** True when the status replies to anything (inReplyToAccountId present). */
		public boolean reply;
		/** Resolved parent display name (plain, no bidi isolates), or null when unresolved. */
		public String parentName;
		/** True when the parent post itself is in the fetched context. */
		public boolean parentPostPresent;
		/** True when the parent author is this status's author. */
		public boolean sameAuthor;
		/** 0-based index in the eligible chain, -1 when absent. */
		public int chainIndex=-1;
		/** Chain size, 0 when absent. */
		public int chainSize;
		// Wording templates, supplied by the caller from resources (§4).
		public String tmplReplyingTo;
		public String tmplReplyingToNotShown;
		public String tmplReplyingToUnresolved;
		public String tmplThreadNumbered;
		public String tmplThreadBare;
		public String tmplA11yReplyingTo;
		public String tmplA11yReplyingToNotShown;
		public String tmplA11yReplyingToUnresolved;
		public String tmplA11yThreadNumbered;
		public String tmplA11yThreadBare;
	}

	/** What one evaluation produced: the state plus the two strings (§5). */
	public static class Result{
		public final State state;
		/** The visible row text, or null for NONE. */
		public final String visible;
		/** The accessibility text (full name, never clamped), or null for NONE. */
		public final String accessibility;

		private Result(State state, String visible, String accessibility){
			this.state=state;
			this.visible=visible;
			this.accessibility=accessibility;
		}
	}

	/**
	 * §1's truth table. Self-reply wins over the parent-name states because it is
	 * decidable from the parent author id alone, even when the account is
	 * unresolved; the cross-author states need a resolved name (§1.3 fires when
	 * there is none, never leaking an id).
	 */
	public static Result evaluate(Input in){
		if(!in.reply)
			return new Result(State.NONE, null, null);
		if(in.sameAuthor){
			// §1.4, M-review: the number is claimed only for in-bounds chain
			// positions — never "Thread 0/0" or "Thread -1/0".
			if(in.chainSize>0 && in.chainIndex>=0 && in.chainIndex<in.chainSize){
				// §1.4: n is this post's index in the fetched chain (1-based).
				return new Result(State.THREAD_NUMBERED,
						format(in.tmplThreadNumbered, in.chainIndex+1, in.chainSize),
						format(in.tmplA11yThreadNumbered, in.chainIndex+1, in.chainSize));
			}
			// §1.5: a number is a claim; without eligible bounds the badge stays bare.
			return new Result(State.THREAD_BARE, in.tmplThreadBare, in.tmplA11yThreadBare);
		}
		// M4: a blank (or whitespace-only) name is unresolved too — the row
		// must never dangle at "Replying to ".
		if(in.parentName==null || in.parentName.trim().isEmpty())
			return new Result(State.REPLY_UNRESOLVED, in.tmplReplyingToUnresolved, in.tmplA11yReplyingToUnresolved);
		String visibleName=clampName(in.parentName);
		if(in.parentPostPresent){
			// §1.1: the accessibility string carries the full, unclamped name.
			return new Result(State.REPLY_PRESENT,
					String.format(in.tmplReplyingTo, visibleName),
					String.format(in.tmplA11yReplyingTo, in.parentName));
		}
		// §1.2: the "· parent post not shown" suffix is never truncated (§4).
		return new Result(State.REPLY_PARENT_NOT_SHOWN,
				String.format(in.tmplReplyingToNotShown, visibleName),
				String.format(in.tmplA11yReplyingToNotShown, in.parentName));
	}

	/**
	 * §4: truncate the visible name at {@link #MAX_VISIBLE_NAME_LENGTH} code
	 * points with "…". Code points, not chars, so supplementary characters
	 * (emoji) count as one and are never split.
	 */
	public static String clampName(String name){
		if(name==null)
			return null;
		int length=name.length();
		if(name.codePointCount(0, length)<=MAX_VISIBLE_NAME_LENGTH)
			return name;
		int end=name.offsetByCodePoints(0, MAX_VISIBLE_NAME_LENGTH);
		return name.substring(0, end)+"…";
	}

	private static String format(String template, int n, int total){
		return String.format(Locale.getDefault(), template, n, total);
	}
}
