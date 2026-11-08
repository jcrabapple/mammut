package org.joinmastodon.android.ui;

import org.joinmastodon.android.model.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MAMMUT: Reading Room (docs/READING-ROOM-SPEC.md §4) — the detection rule that
 * finds the connected same-author chain containing the focused status.
 *
 * The unroll is not a tree and not a ranking: it is the single path from the chain
 * head down through the focused status, restricted to the focused status's author,
 * with at most one continuation per step. Side branches are everything fetched
 * that is not on the path. Nothing is fabricated: an ancestor the server did not
 * return (or that filtering removed) simply ends the upward walk, and the chain
 * starts after the gap.
 *
 * This class is pure model: it takes the fetched statuses plus the focused one and
 * returns chain, per-post side-branch counts, side-branch total, author name and
 * conversation totals. It never builds display items and never touches the
 * RecyclerView, so the fragment stays the only place that knows about rows.
 */
public class ConversationChain{
	/** Chain members in reading order: head first, focused status included. */
	public final List<Status> chain;
	/** Chain post id -> count of its fetched direct replies that are off the chain. */
	public final Map<String,Integer> sideBranchCounts;
	/** Every fetched status not on the chain. */
	public final int sideBranchTotal;
	public final String authorID;
	/** Plain display name (no bidi isolates), username fallback. */
	public final String authorDisplayName;
	/** Every status in the fetched context, including the focused one. */
	public final int totalPosts;
	/** Distinct account ids across the fetched context. */
	public final int totalPeople;
	/** Spec §4: at least 3 members and contains the focused status. */
	public final boolean eligible;

	private final List<String> chainIDs;

	private ConversationChain(List<Status> chain, Map<String,Integer> sideBranchCounts, int sideBranchTotal,
			String authorID, String authorDisplayName, int totalPosts, int totalPeople, boolean eligible){
		this.chain=chain;
		this.sideBranchCounts=sideBranchCounts;
		this.sideBranchTotal=sideBranchTotal;
		this.authorID=authorID;
		this.authorDisplayName=authorDisplayName;
		this.totalPosts=totalPosts;
		this.totalPeople=totalPeople;
		this.eligible=eligible;
		this.chainIDs=new ArrayList<>(chain.size());
		for(Status s:chain)
			chainIDs.add(s.id);
	}

	public static ConversationChain build(List<Status> fetched, Status focused){
		Map<String,Status> byId=new HashMap<>();
		Set<String> people=new HashSet<>();
		for(Status s:fetched){
			if(s==null || s.id==null)
				continue;
			byId.put(s.id, s);
			if(s.account!=null)
				people.add(s.account.id);
		}
		byId.put(focused.id, focused);
		if(focused.account!=null)
			people.add(focused.account.id);

		String authorID=focused.account==null ? null : focused.account.id;
		String authorName=authorID==null ? "" :
				(focused.account.displayName==null || focused.account.displayName.isEmpty()
						? focused.account.username
						: focused.account.displayName);

		// Upward walk (spec §4): follow inReplyToId while the parent is present in the
		// fetched context and has the same author. The head is where the walk stops.
		ArrayList<Status> upward=new ArrayList<>();
		Set<String> seen=new HashSet<>();
		seen.add(focused.id);
		Status cur=focused;
		while(authorID!=null){
			String parentID=cur.inReplyToId;
			if(parentID==null)
				break;
			Status parent=byId.get(parentID);
			if(parent==null || parent.account==null || !authorID.equals(parent.account.id))
				break;
			if(!seen.add(parent.id))
				break; // defensive: a reply cycle ends the walk instead of looping
			upward.add(parent);
			cur=parent;
		}
		Collections.reverse(upward);

		// The path to the focused status IS the upward walk, so preferring the child on
		// that path is guaranteed by construction. Below the focused status there is no
		// path to prefer: the earliest by id wins, the others become side branches.
		ArrayList<Status> chain=new ArrayList<>(upward.size()+1);
		chain.addAll(upward);
		chain.add(focused);
		Set<String> chainIDSet=new HashSet<>(seen);
		cur=focused;
		while(authorID!=null && chain.size()<byId.size()){
			Status next=null;
			for(Status s:byId.values()){
				if(s.account==null || !authorID.equals(s.account.id))
					continue;
				if(!cur.id.equals(s.inReplyToId) || chainIDSet.contains(s.id))
					continue;
				if(next==null || compareIDs(s.id, next.id)<0)
					next=s;
			}
			if(next==null)
				break;
			chain.add(next);
			chainIDSet.add(next.id);
			cur=next;
		}

		boolean containsFocused=chainIDSet.contains(focused.id);
		boolean eligible=chain.size()>=3 && containsFocused;

		// Side branches (spec §4): every fetched status not on the chain, including
		// other authors' replies. Per-post counts cover direct off-chain replies only.
		Map<String,Integer> sideBranchCounts=new HashMap<>();
		int sideBranchTotal=0;
		for(Status s:byId.values()){
			if(chainIDSet.contains(s.id))
				continue;
			sideBranchTotal++;
			String parentID=s.inReplyToId;
			if(parentID!=null && chainIDSet.contains(parentID))
				sideBranchCounts.merge(parentID, 1, Integer::sum);
		}

		return new ConversationChain(Collections.unmodifiableList(chain), Collections.unmodifiableMap(sideBranchCounts),
				sideBranchTotal, authorID==null ? "" : authorID, authorName, byId.size(), people.size(), eligible);
	}

	/** Position of {@code statusID} in the chain, or -1 when it is not a member. */
	public int indexOf(String statusID){
		return chainIDs.indexOf(statusID);
	}

	/** Numeric id comparison: Mastodon ids are decimal snowflakes, so compare by length first. */
	private static int compareIDs(String a, String b){
		if(a.length()!=b.length())
			return Integer.compare(a.length(), b.length());
		return a.compareTo(b);
	}
}
