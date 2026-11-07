package org.joinmastodon.android.fragments;

import android.app.Activity;
import android.os.Bundle;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.PollUpdatedEvent;
import org.joinmastodon.android.events.RemoveAccountPostsEvent;
import org.joinmastodon.android.events.StatusCountersUpdatedEvent;
import org.joinmastodon.android.events.StatusCreatedEvent;
import org.joinmastodon.android.events.StatusDeletedEvent;
import org.joinmastodon.android.events.StatusDisplaySettingsChangedEvent;
import org.joinmastodon.android.events.StatusUpdatedEvent;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.Mention;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.TimelineDefinition;
import org.joinmastodon.android.ui.displayitems.ExtendedFooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.FooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.events.StatusMuteChangedEvent;
import org.joinmastodon.android.ui.displayitems.HeaderStatusDisplayItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public abstract class StatusListFragment extends BaseStatusListFragment<Status>{
	// MAMMUT: F1 — HomeTabFragment stamps tab fragments with their index inside
	// AccountLocalPreferences.timelines so the per-tab view filters of that definition apply.
	public static final String EXTRA_TIMELINE_DEF_INDEX="__timeline_def_index";

	protected EventListener eventListener=new EventListener();

	// MAMMUT: F1 — whether the most recently loaded raw page said more data exists. Kept here
	// because Preloader updates its own moreAvailable flag only after onAppendItems returns.
	private boolean lastPageHadMore;
	private CharSequence defaultEmptyText;
	private boolean usedFilteredEmptyText;

	protected List<StatusDisplayItem> buildDisplayItems(Status s){
		// MAMMUT: port from upstream master — spectator mode removes the footer, the
		// media-preview preference swaps thumbnails for attachment rows
		int flags=0;
		if(GlobalUserPreferences.spectatorMode)
			flags|=StatusDisplayItem.FLAG_NO_FOOTER;
		if(!GlobalUserPreferences.showMediaPreview)
			flags|=StatusDisplayItem.FLAG_NO_MEDIA_PREVIEW;
		return StatusDisplayItem.buildItems(this, getActivity(), s, accountID, s, knownAccounts, flags);
	}

	protected abstract FilterContext getFilterContext();

	// MAMMUT: F1 per-tab timeline view filters (docs/FEATURE-PLAN.md §3). Only fragments that
	// HomeTabFragment stamped with a timeline definition index are filtered — threads, profiles,
	// bookmarks, search and the legacy home timeline mode never carry the extra argument and
	// therefore always return null here and stay unfiltered.
	@Nullable
	protected TimelineDefinition getTimelineDefinition(){
		Bundle args=getArguments();
		if(args==null || !args.containsKey(EXTRA_TIMELINE_DEF_INDEX))
			return null;
		int index=args.getInt(EXTRA_TIMELINE_DEF_INDEX, -1);
		ArrayList<TimelineDefinition> timelines=AccountSessionManager.get(accountID).getLocalPreferences().timelines;
		if(timelines==null || index<0 || index>=timelines.size())
			return null;
		return timelines.get(index);
	}

	// MAMMUT: F1 — hide boosts/hide replies keep self-relevant rows: a reply that mentions you and
	// a boost of your own post always stay visible, so conversations you're part of don't vanish
	// from a filtered tab the way they still would from a server-side exclusion. Predicates
	// combine with AND; the raw status is never removed from `data`.
	protected boolean shouldShowStatus(Status s){
		TimelineDefinition def=getTimelineDefinition();
		if(def==null)
			return true;
		String selfID=AccountSessionManager.get(accountID).self.id;
		if(!def.isShowBoosts() && s.reblog!=null && !isSelfContent(s.reblog, selfID))
			return false;
		if(!def.isShowReplies() && s.inReplyToAccountId!=null && !mentionsSelf(s, selfID))
			return false;
		if(def.isMediaOnly() && (s.getContentStatus().mediaAttachments==null || s.getContentStatus().mediaAttachments.isEmpty()))
			return false;
		return true;
	}

	private static boolean isSelfContent(Status contentStatus, String selfID){
		return contentStatus.account!=null && selfID.equals(contentStatus.account.id);
	}

	private static boolean mentionsSelf(Status s, String selfID){
		if(s.mentions==null)
			return false;
		for(Mention m:s.mentions){
			if(m.id!=null && m.id.equals(selfID))
				return true;
		}
		return false;
	}

	@Override
	protected boolean shouldIncludeItemInList(Status item){
		return shouldShowStatus(item);
	}

	@Override
	public void onAppendItems(List<Status> items){
		int prevDisplayItemCount=displayItems.size();
		super.onAppendItems(items);
		if(displayItems.size()>prevDisplayItemCount || items.isEmpty())
			return;
		// MAMMUT: F1 — the whole raw page was hidden by this tab's view filters. Keep loading
		// older pages until a visible row arrives or the server runs out of data, otherwise an
		// all-filtered page would either loop on the same request or fake an empty timeline.
		// The retry is posted so the fragment's maxID field has been updated by the callback
		// that delivered this page, and it defers to a load that is already in flight.
		if(!lastPageHadMore || list==null)
			return;
		list.post(()->{
			if(getActivity()==null || isDataLoading())
				return;
			preloader.onScrolledToLastItem();
		});
	}

	@Override
	protected void onDataLoaded(List<Status> d, boolean more){
		lastPageHadMore=more;
		super.onDataLoaded(d, more);
		updateFilteredEmptyText();
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		defaultEmptyText=emptyText;
	}

	// MAMMUT: F1 — when filters hide everything that has been loaded, say so instead of showing
	// the generic empty-list text. Raw data is still there and paging continues in the background.
	private void updateFilteredEmptyText(){
		TimelineDefinition def=getTimelineDefinition();
		boolean filtering=def!=null && def.hasActiveViewFilters();
		if(displayItems.isEmpty() && filtering && !data.isEmpty()){
			setEmptyText(R.string.timeline_view_empty_filtered);
			usedFilteredEmptyText=true;
		}else if(usedFilteredEmptyText){
			setEmptyText(defaultEmptyText);
			usedFilteredEmptyText=false;
		}
	}

	@Override
	protected void addAccountToKnown(Status s){
		if(!knownAccounts.containsKey(s.account.id))
			knownAccounts.put(s.account.id, s.account);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		E.register(eventListener);
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(eventListener);
	}

	@Override
	public void onItemClick(String id){
		Status status=getContentStatusByID(id);
		if(status==null)
			return;
		navigateToStatus(status);
	}

	@Override
	public void onItemClick(String id, boolean quote){
		Status status=getContentStatusByID(id);
		if(status==null)
			return;
		navigateToStatus(quote ? status.quote.quotedStatus : status);
	}

	@Override
	protected Status asStatus(Status s){
		return s;
	}

	protected void onStatusCreated(Status status){}

	protected void onStatusUpdated(Status status){
		ArrayList<Status> statusesForDisplayItems=new ArrayList<>();
		for(int i=0;i<data.size();i++){
			Status s=data.get(i);
			if(s.reblog!=null && s.reblog.id.equals(status.id)){
				s.reblog=status.clone();
				statusesForDisplayItems.add(s);
			}else if(s.id.equals(status.id)){
				data.set(i, status);
				statusesForDisplayItems.add(status);
			}
		}
		for(int i=0;i<preloadedData.size();i++){
			Status s=preloadedData.get(i);
			if(s.reblog!=null && s.reblog.id.equals(status.id)){
				s.reblog=status.clone();
			}else if(s.id.equals(status.id)){
				preloadedData.set(i, status);
			}
		}

		if(statusesForDisplayItems.isEmpty())
			return;

		for(Status s:statusesForDisplayItems){
			int i=0;
			for(StatusDisplayItem item:displayItems){
				if(item.parentID.equals(s.id)){
					int start=i;
					for(;i<displayItems.size();i++){
						if(!displayItems.get(i).parentID.equals(s.id))
							break;
					}
					List<StatusDisplayItem> postItems=displayItems.subList(start, i);
					postItems.clear();
					postItems.addAll(buildDisplayItems(s));
					int oldSize=i-start, newSize=postItems.size();
					if(oldSize==newSize){
						adapter.notifyItemRangeChanged(start, newSize);
					}else if(oldSize<newSize){
						adapter.notifyItemRangeChanged(start, oldSize);
						adapter.notifyItemRangeInserted(start+oldSize, newSize-oldSize);
					}else{
						adapter.notifyItemRangeChanged(start, newSize);
						adapter.notifyItemRangeRemoved(start+newSize, oldSize-newSize);
					}
					break;
				}
				i++;
			}
		}
	}

	protected Status getContentStatusByID(String id){
		Status s=getStatusByID(id);
		return s==null ? null : s.getContentStatus();
	}

	protected Status getStatusByID(String id){
		for(Status s:data){
			if(s.id.equals(id)){
				return s;
			}
		}
		for(Status s:preloadedData){
			if(s.id.equals(id)){
				return s;
			}
		}
		return null;
	}

	protected boolean shouldRemoveAccountPostsWhenUnfollowing(){
		return false;
	}

	protected void onRemoveAccountPostsEvent(RemoveAccountPostsEvent ev){
		List<Status> toRemove=Stream.concat(data.stream(), preloadedData.stream())
				.filter(s->s.account.id.equals(ev.postsByAccountID) || (!ev.isUnfollow && s.reblog!=null && s.reblog.account.id.equals(ev.postsByAccountID)))
				.collect(Collectors.toList());
		for(Status s:toRemove){
			removeStatus(s);
		}
	}

	protected void removeStatus(Status status){
		data.remove(status);
		preloadedData.remove(status);
		int index=-1;
		for(int i=0;i<displayItems.size();i++){
			if(status.id.equals(displayItems.get(i).parentID)){
				index=i;
				break;
			}
		}
		if(index==-1)
			return;
		int lastIndex;
		for(lastIndex=index;lastIndex<displayItems.size();lastIndex++){
			if(!displayItems.get(lastIndex).parentID.equals(status.id))
				break;
		}
		displayItems.subList(index, lastIndex).clear();
		adapter.notifyItemRangeRemoved(index, lastIndex-index);
	}

	public class EventListener{

		@Subscribe
		public void onStatusMuteChanged(StatusMuteChangedEvent ev){
			// MAMMUT: port from upstream master
			for(Status s:data){
				if(s.getContentStatus().id.equals(ev.id)){
					s.update(ev);
					for(int i=0;i<list.getChildCount();i++){
						RecyclerView.ViewHolder holder=list.getChildViewHolder(list.getChildAt(i));
						if(holder instanceof HeaderStatusDisplayItem.Holder header && header.getItem().status==s.getContentStatus())
							header.rebind();
					}
				}
			}
		}

		@Subscribe
		public void onStatusCountersUpdated(StatusCountersUpdatedEvent ev){
			for(Status s:data){
				if(s.getContentStatus().id.equals(ev.id)){
					s.update(ev);
					for(int i=0;i<list.getChildCount();i++){
						RecyclerView.ViewHolder holder=list.getChildViewHolder(list.getChildAt(i));
						if(holder instanceof FooterStatusDisplayItem.Holder footer && footer.getItem().status==s.getContentStatus()){
							footer.rebind();
						}else if(holder instanceof ExtendedFooterStatusDisplayItem.Holder footer && footer.getItem().status==s.getContentStatus()){
							footer.rebind();
						}
					}
				}
			}
			for(Status s:preloadedData){
				if(s.id.equals(ev.id)){
					s.update(ev);
				}
			}
		}

		@Subscribe
		public void onStatusDeleted(StatusDeletedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			Status status=getStatusByID(ev.id);
			if(status==null)
				return;
			removeStatus(status);
		}

		@Subscribe
		public void onStatusCreated(StatusCreatedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			StatusListFragment.this.onStatusCreated(ev.status.clone());
		}

		@Subscribe
		public void onStatusUpdated(StatusUpdatedEvent ev){
			StatusListFragment.this.onStatusUpdated(ev.status);
		}

		@Subscribe
		public void onPollUpdated(PollUpdatedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			for(Status status:data){
				Status contentStatus=status.getContentStatus();
				if(contentStatus.poll!=null && contentStatus.poll.id.equals(ev.poll.id)){
					updatePoll(status.id, contentStatus, ev.poll);
				}
			}
		}

		@Subscribe
		public void onRemoveAccountPostsEvent(RemoveAccountPostsEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			if(ev.isUnfollow && !shouldRemoveAccountPostsWhenUnfollowing())
				return;
			StatusListFragment.this.onRemoveAccountPostsEvent(ev);
		}

		@Subscribe
		public void onStatusDisplaySettingsChanged(StatusDisplaySettingsChangedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			// MAMMUT: F1 — the per-tab view filters changed; rebuild the visible list from the
			// raw statuses, which were kept in `data` precisely so this needs no re-fetch.
			if(getTimelineDefinition()==null)
				return;
			rebuildAllDisplayItems();
			updateFilteredEmptyText();
		}
	}
}
