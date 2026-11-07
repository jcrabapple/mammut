package org.joinmastodon.android.ui;

import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md §5) — the grouping/ordering model that
 * turns the filtered raw statuses into sections. Original posts group by author;
 * boosts are pulled out into one section subgrouped by booster. Group order is by
 * each group's most recent in-window wrapper (most recent first); within a group
 * the input order is kept, which is newest-first — the same reading direction as
 * every other surface in the app. A group of one renders exactly like every other
 * group; there is deliberately no "group only when busy" threshold.
 *
 * This class is pure model: it never builds display items and never touches the
 * RecyclerView, so the fragment stays the only place that knows about rows.
 */
public class CatchUpGrouping{
	private CatchUpGrouping(){}

	public static class AuthorGroup{
		public final Account account;
		public final List<Status> posts=new ArrayList<>();

		public AuthorGroup(Account account){
			this.account=account;
		}

		/** Epoch ms of the group's most recent wrapper; groups sort by this, descending. */
		public long latestActivity(){
			return posts.get(0).createdAt.toEpochMilli();
		}
	}

	public static class Result{
		public final List<AuthorGroup> authors;
		public final List<AuthorGroup> boosts;
		public final int boostCount;

		private Result(List<AuthorGroup> authors, List<AuthorGroup> boosts, int boostCount){
			this.authors=authors;
			this.boosts=boosts;
			this.boostCount=boostCount;
		}
	}

	public static Result group(List<Status> filtered){
		Map<String, AuthorGroup> authorsByID=new HashMap<>();
		Map<String, AuthorGroup> boostsByBooster=new HashMap<>();
		int boostCount=0;
		for(Status s:filtered){
			// The wrapper account is the author for original posts and the booster
			// for boosts — grouping key in both cases. The boosted original's author
			// never forms a group here (§5: boosts live only in the boosts section).
			Map<String, AuthorGroup> map=s.reblog!=null ? boostsByBooster : authorsByID;
			AuthorGroup g=map.computeIfAbsent(s.account.id, id->new AuthorGroup(s.account));
			g.posts.add(s);
			if(s.reblog!=null)
				boostCount++;
		}
		List<AuthorGroup> authors=new ArrayList<>(authorsByID.values());
		authors.sort(Comparator.comparingLong(AuthorGroup::latestActivity).reversed());
		List<AuthorGroup> boosts=new ArrayList<>(boostsByBooster.values());
		boosts.sort(Comparator.comparingLong(AuthorGroup::latestActivity).reversed());
		return new Result(authors, boosts, boostCount);
	}
}
