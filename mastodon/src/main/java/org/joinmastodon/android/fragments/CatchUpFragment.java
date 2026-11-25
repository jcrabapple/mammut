package org.joinmastodon.android.fragments;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.timelines.GetHomeTimeline;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.api.session.AccountLocalPreferences;
import org.joinmastodon.android.events.StatusCreatedEvent;
import org.joinmastodon.android.events.StatusDisplaySettingsChangedEvent;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.TimelineDefinition;
import org.joinmastodon.android.ui.CatchUpGrouping;
import org.joinmastodon.android.ui.displayitems.CatchUpFooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.CatchUpHeaderStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.CatchUpProgressStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.ui.text.HtmlParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;

/**
 * MAMMUT: Catch up — the bounded reading view specified in docs/CATCH-UP-SPEC.md.
 *
 * A snapshot fetch of the home timeline over a fixed 24-hour window (or a 200-post
 * cap, whichever comes first), rendered once as author groups with the boosts in a
 * separate collapsible section, and an honest end footer. It is not a mode of Home
 * and never inserts fresh posts under the reader's finger: opening the screen is
 * the only way to refresh it, and leaving it cancels whatever is in flight.
 *
 * Fetch-first/display-once (spec §3): the sequential backward fill runs to the
 * window/cap regardless of filters; the current tab's view filters then project
 * the raw set into display items. Filters therefore never add a request, and the
 * request ceiling is exactly 5 (5 pages x 40 posts = the 200 cap) in the worst
 * case, plus one user-initiated request per "Load older posts" tap after that.
 */
public class CatchUpFragment extends StatusListFragment{
	public static final long CATCH_UP_WINDOW_MS=24L*60*60*1000; // 24 hours, epoch-elapsed
	public static final int CATCH_UP_MAX_POSTS=200;
	private org.joinmastodon.android.ui.views.CatchUpCardDecoration cardDecoration;
	private static final int PAGE_SIZE=40;
	private static final int REQUEST_CEILING=5; // 5 x 40 = 200 = CATCH_UP_MAX_POSTS by construction
	private static final java.time.format.DateTimeFormatter SINCE_FORMATTER=
			java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT);

	/** Start of the window this catch-up covers, in epoch millis. */
	private long windowStart;
	/** When this catch-up began; stored on completion so the next one starts there. */
	private long fillStartedAt;
	/** True when a previous catch-up exists, which changes what the scope line may claim. */
	private boolean hasPreviousCatchUp;
	/** How many of the fetched statuses are actually rendered: posts, not boosts or replies. */
	private int shownCount;

	private long cutoff;
	private boolean extended; // the user left the window via "Load older posts"
	private boolean fillRunning, extensionRunning;
	private boolean boostsCollapsed; // session-only, by design (spec §5)
	private Runnable pendingRetry; // resumes paging from the last cursor after an inline error
	/**
	 * Fill/pagination/marker state machine (R4 F3/F4/F5/F9/F10). Replaced on every
	 * fill; every cursor, stop-reason and marker decision lives in {@link CatchUpFill}
	 * so the transitions are unit-testable without Android.
	 */
	private CatchUpFill.State fill=CatchUpFill.initialState();

	private TextView scopeLine, filterSummary;
	private TextView emptySubtitle;
	private Button emptySecondaryButton;
	private Runnable emptyPrimaryAction;

	public CatchUpFragment(){
		setLayout(R.layout.fragment_catch_up);
		setListLayoutId(R.layout.fragment_catch_up_list);
		// MAMMUT: Catch up is a frozen snapshot (spec §3): the event listener ignores
		// status-created events entirely, but still honors deletions, edits and the
		// display-settings event that fires when the tab's view filters change.
		// Initialize once in the constructor so configuration changes on a retained
		// fragment never replace the Otto-registered listener in onAttach().
		eventListener=new CatchUpEventListener();
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		setTitle(R.string.catch_up);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		// Cards replace divider lines here: each group is its own surface, and a divider
		// drawn at a card boundary floats in the gap between them. Clear every decoration
		// the base fragment installed rather than matching one by class name — a divider
		// that survives this call is drawn between cards with nothing to explain it.
		while(list.getItemDecorationCount()>0)
			list.removeItemDecorationAt(0);
		cardDecoration=new org.joinmastodon.android.ui.views.CatchUpCardDecoration(view.getContext());
		list.addItemDecoration(cardDecoration);
		scopeLine=view.findViewById(R.id.catch_up_scope);
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
			scopeLine.setAccessibilityHeading(true); // spec §8: the screen's first heading
		filterSummary=view.findViewById(R.id.catch_up_filters);
		// R4 F14: the rule line states the view's scope; it names no action, so it
		// must not present as one. The layout dresses it as a button (primary color,
		// ripple background, clickable/focusable) with no click handler — an inert
		// control. Disable the interaction here; the words stay, the fake button goes.
		filterSummary.setClickable(false);
		filterSummary.setFocusable(false);
		// Catch up has no view settings of its own: what it shows is the point of it.
		// The custom empty view carries a supporting line and a secondary action on
		// top of the appkit empty_text/empty_button pair (spec §7 filtered-to-empty).
		if(emptyView!=null){
			emptySubtitle=emptyView.findViewById(R.id.empty_subtitle);
			emptySecondaryButton=emptyView.findViewById(R.id.empty_button_secondary);
			// The label must be set here: the button is only made visible in the
			// filtered-to-empty state, and showing it without text produced a blank bar.
			emptySecondaryButton.setText(R.string.catch_up_load_older);
			emptySecondaryButton.setOnClickListener(v->loadOlderPosts());
		}
		// MAMMUT: the spec's refresh story is "reopen the view" (§3); there is no
		// pull-to-refresh and no preloader-driven background paging in this view.
		if(refreshLayout!=null)
			setRefreshEnabled(false);
		updateScopeAndSummary();
		// Each time the view is created — first open and every configuration change —
		// the snapshot is refetched (spec §3: refetching is cheaper than retaining a
		// cursor-sensitive snapshot). startFill() cancels anything still in flight.
		startFill();
	}

// Fill (spec §2-§3)

	private void startFill(){
		if(currentRequest!=null){
			currentRequest.cancel();
			currentRequest=null;
		}
		data.clear();
		preloadedData.clear();
		displayItems.clear();
		adapter.notifyDataSetChanged();
		long now=System.currentTimeMillis();
		long lastAt=AccountSessionManager.get(accountID).getLocalPreferences().catchUpLastAt;
		// The window starts where the last completed catch-up started. A first run, or a
		// return after more than a day, falls back to 24 hours: a catch-up is a day's
		// reading, not an archive dig. R4 F9: a marker from the future (clock skew) is
		// rejected and falls back to the same 24h floor.
		hasPreviousCatchUp=CatchUpFill.hasPreviousCatchUp(now, lastAt, CATCH_UP_WINDOW_MS);
		windowStart=CatchUpFill.windowStart(now, lastAt, CATCH_UP_WINDOW_MS);
		fillStartedAt=now;
		cutoff=windowStart;
		fill=CatchUpFill.initialState();
		extended=false;
		boostsCollapsed=false;
		pendingRetry=null;
		fillRunning=true;
		loaded=false;
		dataLoading=true;
		updateScopeAndSummary();
		showProgress();
		fetchPage(null, false);
	}

	private void fetchPage(String maxID, boolean extension){
		currentRequest=new GetHomeTimeline(maxID, null, PAGE_SIZE, null)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(List<Status> page){
						if(getActivity()==null)
							return;
						currentRequest=null;
						// MAMMUT: the server's own pagination end can only be read from the RAW
						// page size. Server-side filters (keyword, blur) remove items here, so a
						// filtered page shorter than PAGE_SIZE must never be mistaken for
						// "the server has nothing older" — that is the one claim the footer is
						// allowed to make only when the server licenses it. R4 F4: the raw ids
						// are also the fill's pagination cursor, captured BEFORE filtering.
						ArrayList<String> rawIDs=new ArrayList<>(page.size());
						for(Status s:page)
							rawIDs.add(s.id);
						AccountSessionManager.get(accountID).filterStatuses(page, FilterContext.HOME);
						if(extension){
							onExtensionPageLoaded(page, rawIDs);
						}else{
							onFillPageLoaded(page, rawIDs);
						}
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()==null)
							return;
						currentRequest=null;
						if(extension){
							extensionRunning=false;
							// The failed tap leaves all existing content untouched
							// (spec §6); the inline row offers the retry.
							pendingRetry=CatchUpFragment.this::loadOlderPosts;
							buildList();
						}else if(data.isEmpty()){
							// First request failed: no snapshot exists and none is
							// faked (spec §7). The framework error view's Try again
							// restarts the whole fill.
							fillRunning=false;
							dataLoading=false;
							CatchUpFragment.this.onError(error);
						}else{
							// Error mid-fill: keep what loaded, offer to resume from
							// the last cursor (spec §7 partially-loaded state).
							fillRunning=false;
							dataLoading=false;
							loaded=true;
							dataLoaded();
							pendingRetry=CatchUpFragment.this::retryMidFill;
							buildList();
						}
					}
				})
				.exec(accountID);
	}

	private void onFillPageLoaded(List<Status> page, List<String> rawIDs){
		ArrayList<CatchUpFill.PageItem> items=new ArrayList<>(page.size());
		for(Status s:page)
			items.add(new CatchUpFill.PageItem(s.id, s.createdAt.toEpochMilli()));
		// Inclusion tests the wrapper's createdAt — a boost of a week-old post inside
		// the window counts (spec §2). Pages are newest-first, so every wrapper from
		// the first older-than-cutoff one on is dropped as a boundary straggler.
		int keep=CatchUpFill.onFillPage(fill, rawIDs, items, cutoff, PAGE_SIZE, CATCH_UP_MAX_POSTS, REQUEST_CEILING);
		if(keep<page.size())
			page=new ArrayList<>(page.subList(0, keep));
		if(!page.isEmpty())
			ingest(page);
		if(fill.end==CatchUpFill.End.NONE){
			// R4 F4: the fill cursor is the RAW oldest of the last page, so a page
			// that filtering emptied still advances — the same page can never repeat.
			fetchPage(fill.networkCursor, false);
		}else{
			if(fill.end==CatchUpFill.End.CAP){
				// Trim to the cap; the trimmed stragglers stay re-reachable because the
				// first extension fetch keys on the oldest *kept* wrapper.
				while(data.size()>CATCH_UP_MAX_POSTS)
					data.remove(data.size()-1);
				if(!data.isEmpty())
					CatchUpFill.noteCapTrim(fill, data.get(data.size()-1).id);
			}
			endFill();
		}
	}

	private void ingest(List<Status> page){
		for(Status s:page){
			data.add(s);
			addAccountToKnown(s);
		}
		postprocessNewlyLoadedStatuses(page);
		loadRelationships(page.stream().map(Status::getAccountID).filter(Objects::nonNull).collect(Collectors.toSet()));
	}

	private void endFill(){
		// Only a completed, non-cap-truncated fill advances the marker: abandoning the
		// view mid-fetch, erroring out, or stopping at the cap must never skip posts the
		// reader never saw (R4 F5 — a CAP-truncated window keeps the old marker, so the
		// remainder is still inside the next catch-up's window).
		if(pendingRetry==null && CatchUpFill.shouldAdvanceMarker(fill) && fillStartedAt>0){
			AccountLocalPreferences lp=AccountSessionManager.get(accountID).getLocalPreferences();
			lp.catchUpLastAt=fillStartedAt;
			lp.save();
		}
		fillRunning=false;
		dataLoading=false;
		loaded=true;
		currentRequest=null;
		CatchUpFill.prepareExtension(fill);
		dataLoaded();
		updateScopeAndSummary();
		buildList();
	}

// Load older posts (spec §6)

	private void retryMidFill(){
		if(fillRunning)
			return;
		fillRunning=true;
		pendingRetry=null;
		buildList();
		fetchPage(fill.networkCursor, false);
	}

	private void loadOlderPosts(){
		if(extensionRunning || fill.serverEnded)
			return;
		extensionRunning=true;
		pendingRetry=null;
		buildList(); // swaps the footer for the inline progress row
		fetchPage(fill.extensionCursor, true);
	}

	private void onExtensionPageLoaded(List<Status> page, List<String> rawIDs){
		extensionRunning=false;
		extended=true; // the 24-hour window and the 200 cap no longer gate this view
		ArrayList<CatchUpFill.PageItem> items=new ArrayList<>(page.size());
		for(Status s:page)
			items.add(new CatchUpFill.PageItem(s.id, s.createdAt.toEpochMilli()));
		CatchUpFill.onExtensionPage(fill, rawIDs, items);
		if(!page.isEmpty())
			ingest(page);
		updateScopeAndSummary();
		buildList();
	}

// List construction (spec §4-§6)

	@Override
	protected boolean shouldShowStatus(Status s){
		// Catch up reads posts. Boosts and replies are not part of that reading, and the
		// view offers no control that could ask for them.
		return s.reblog==null && s.inReplyToId==null;
	}

	private void buildList(){
		if(getActivity()==null)
			return;
		displayItems.clear();
		ArrayList<Status> filtered=new ArrayList<>();
		for(Status s:data){
			if(shouldShowStatus(s))
				filtered.add(s);
		}
		// Card groups: one elevated card per author group (and one for the boosts
		// section). Negative means the row sits outside any card.
		ArrayList<Integer> cardOf=new ArrayList<>();
		shownCount=shownCountOf(data);
		if(!filtered.isEmpty()){
			CatchUpGrouping.Result grouping=CatchUpGrouping.group(filtered);
			int groupIndex=0;
			for(CatchUpGrouping.AuthorGroup g:grouping.authors){
				int cardId=groupIndex++;
				displayItems.add(new CatchUpHeaderStatusDisplayItem("catchup_author_"+g.account.id, this, getActivity(),
						authorDisplayName(g),
						getResources().getQuantityString(R.plurals.catch_up_card_posts, g.posts.size(), g.posts.size()),
						false, false, null, g.account));
				cardOf.add(cardId);
				for(Status s:g.posts){
					// A relative time is the wrong instrument in a bounded reading view: the
					// reader is inside a fixed 24 hours and needs the actual moment.
					cardOf.add(cardId);
					displayItems.add(new org.joinmastodon.android.ui.displayitems.CatchUpTimestampStatusDisplayItem("catchup_ts_"+s.id, this, getActivity(), s.createdAt));
					List<StatusDisplayItem> postItems=buildDisplayItems(s, StatusDisplayItem.FLAG_NO_HEADER);
					populateNestedQuotes(postItems);
					// MAMMUT: with no header row, a media-only post starts flush against the
					// divider above it. Give the first block a gap so it reads as a post.
					for(StatusDisplayItem di:postItems){
						if(di instanceof org.joinmastodon.android.ui.displayitems.MediaGridStatusDisplayItem mg){
							if(di==postItems.get(0)) mg.extraTopSpacingPx=me.grishka.appkit.utils.V.dp(8);
							// Catch up is an explicitly chosen reading mode: descriptions are part of
							// it, not a global preference the reader has to have found first.
							mg.forceInlineAlt=true;
							// The card supplies the outer margin, so media starts level with the
							// text instead of carrying the ordinary header-row inset.
							mg.leadingInsetPx=me.grishka.appkit.utils.V.dp(16);
						}else if(di instanceof org.joinmastodon.android.ui.displayitems.TextStatusDisplayItem t){
							// No avatar column exists inside a card, so the 48dp header inset would
							// leave the text floating in the middle of the card.
							t.leadingInsetPx=0;
						}else if(di instanceof org.joinmastodon.android.ui.displayitems.LinkCardStatusDisplayItem l){
							// Same 64dp avatar-column inset as text: there is no avatar column here.
							l.leadingInsetPx=me.grishka.appkit.utils.V.dp(16);
						}else if(di instanceof org.joinmastodon.android.ui.displayitems.FooterStatusDisplayItem f){
							// The action row keeps its own 8dp, landing it on the same 16dp line as text.
							f.leadingInsetPx=me.grishka.appkit.utils.V.dp(8);
						}
					}
					for(int i=0; i<postItems.size(); i++)
						cardOf.add(cardId);
					displayItems.addAll(postItems);
				}
			}
			addTrailingRow();
		}else{
			// Filtered-to-empty has no end footer (spec §6): its state lives in the
			// empty view. The mid-fill / extension inline error row still applies.
			if(pendingRetry!=null)
				addTrailingRow();
		}
		// Anything added by the trailing row helpers sits outside every card.
		while(cardOf.size()<displayItems.size())
			cardOf.add(-1);
		int[] groups=new int[cardOf.size()];
		for(int i=0; i<groups.length; i++)
			groups[i]=cardOf.get(i);
		if(cardDecoration!=null)
			cardDecoration.setGroups(groups);
		adapter.notifyDataSetChanged();
		updateEmptyView(filtered.size());
	}

	private void addTrailingRow(){
		if(fillRunning || extensionRunning){
			displayItems.add(new CatchUpProgressStatusDisplayItem("catchup_progress", this, getActivity(),
					CatchUpProgressStatusDisplayItem.Mode.PROGRESS, getString(R.string.catch_up_loading_older), null, null));
		}else if(pendingRetry!=null){
			displayItems.add(new CatchUpProgressStatusDisplayItem("catchup_progress", this, getActivity(),
					CatchUpProgressStatusDisplayItem.Mode.ERROR, errorText(), getString(R.string.try_again), pendingRetry));
		}else{
			displayItems.add(new CatchUpFooterStatusDisplayItem("catchup_footer", this, getActivity(),
					footerTitle(), footerBody(), getString(R.string.catch_up_load_older),
					fill.serverEnded ? null : this::loadOlderPosts));
		}
	}

	private CharSequence authorDisplayName(CatchUpGrouping.AuthorGroup g){
		String name=g.account.displayName;
		if(TextUtils.isEmpty(name))
			name=g.account.username;
		return g.account.emojis==null ? name : HtmlParser.parseCustomEmoji(name, g.account.emojis);
	}

	private String footerTitle(){
		if(fill.end==CatchUpFill.End.CAP)
			return getString(R.string.catch_up_footer_title_cap);
		return getString(R.string.catch_up_footer_title_done);
	}

	private String footerBody(){
		// Counts describe what is on screen. Fetched statuses that this view refuses to
		// show (boosts, replies) must not be counted as if the reader had seen them.
		// R4 F6: the footer states what Mammut fetched, never what "the server has" —
		// the only count it can vouch for is the fetched originals it shows.
		int n=shownCount;
		return switch(CatchUpFill.footerBody(extended, fill.end, hasPreviousCatchUp)){
			case CAP -> getResources().getQuantityString(R.plurals.catch_up_footer_body_cap, n, n);
			case EXTENDED -> getResources().getQuantityString(R.plurals.catch_up_footer_body_extended, n, n);
			case SINCE -> getResources().getQuantityString(R.plurals.catch_up_footer_body_since, n, n);
			case WINDOW -> getResources().getQuantityString(R.plurals.catch_up_footer_body_window, n, n);
		};
	}

	/** R4 F3: posts shown by this view — no boosts, no replies — counted from the list itself, never from a stale field. */
	static int shownCountOf(List<Status> statuses){
		int n=0;
		for(Status s:statuses){
			if(s.reblog==null && s.inReplyToId==null)
				n++;
		}
		return n;
	}



// Header, empty view and error copy (spec §4, §7)

	private void updateScopeAndSummary(){
		if(scopeLine==null)
			return;
		if(extended){
			// The window no longer applies once the user has left it; the scope line
			// must not keep claiming a span it is not showing (spec §6's no-overclaim rule).
			scopeLine.setText(R.string.catch_up_scope_extended);
		}else if(fill.capTruncated){
			// R4 F3: the count is derived from `data` at format time, not read from a
			// field buildList may not have refreshed yet — the moment the fill ends the
			// scope line names the real, nonzero count.
			scopeLine.setText(getString(R.string.catch_up_scope_cap, shownCountOf(data)));
		}else if(hasPreviousCatchUp){
			// An Instant carries no calendar fields, so the localized formatter needs a zone
			// before it can name a month. Without it this threw on the second open.
			scopeLine.setText(getString(R.string.catch_up_scope_since,
					SINCE_FORMATTER.withZone(java.time.ZoneId.systemDefault()).format(Instant.ofEpochMilli(windowStart))));
		}else{
			scopeLine.setText(R.string.catch_up_scope);
		}
		filterSummary.setText(R.string.catch_up_summary_posts_only);
	}

	private void updateEmptyView(int filteredCount){
		boolean filteredEmpty=!data.isEmpty() && filteredCount==0;
		boolean emptyWindow=data.isEmpty() && !fillRunning && fill.end!=CatchUpFill.End.NONE && pendingRetry==null;
		if(emptySubtitle!=null)
			emptySubtitle.setVisibility(filteredEmpty ? View.VISIBLE : View.GONE);
		// R4 F10: with the server ended, "Load older posts" can never return anything;
		// a button that is a guaranteed no-op is hidden rather than shown inert.
		if(emptySecondaryButton!=null)
			emptySecondaryButton.setVisibility(filteredEmpty && CatchUpFill.showFilteredEmptyLoadOlder(fill) ? View.VISIBLE : View.GONE);
		if(filteredEmpty){
			setEmptyText(R.string.catch_up_empty_posts_only);
			if(emptySubtitle!=null)
				emptySubtitle.setText(getResources().getQuantityString(R.plurals.catch_up_empty_posts_only_sub, data.size(), data.size()));
			setEmptyButtonVisible(false);
			emptyPrimaryAction=null;
		}else if(emptyWindow){
			setEmptyText(hasPreviousCatchUp ? R.string.catch_up_empty_window_since : R.string.catch_up_empty_window);
			if(fill.serverEnded){
				// The server has no posts at all for this account; a Load older
				// button here would be a no-op, so the state offers nothing it
				// cannot deliver (spec §6's honesty rules).
				setEmptyButtonVisible(false);
				emptyPrimaryAction=null;
			}else{
				setEmptyButtonText(R.string.catch_up_load_older);
				setEmptyButtonVisible(true);
				emptyPrimaryAction=this::loadOlderPosts;
			}
		}else{
			emptyPrimaryAction=null;
		}
	}

	private String errorLine(){
		String host=getSession().getInstanceUri().getHost();
		return getString(R.string.catch_up_error_offline, host);
	}

	private String errorText(){
		return errorLine()+"\n"+getString(R.string.catch_up_error_unchanged);
	}

	@Override
	public void onError(ErrorResponse error){
		super.onError(error);
		// Spec §7: this view never surfaces raw HTTP codes or server exception text.
		// The 401 reauth path keeps the framework's own copy.
		if(errorView!=null && !doReauthOnErrorRetry){
			TextView text=errorView.findViewById(R.id.error_text);
			if(text!=null)
				text.setText(errorText());
		}
	}

	@Override
	protected void onEmptyViewBtnClick(){
		if(emptyPrimaryAction!=null)
			emptyPrimaryAction.run();
	}



// Framework plumbing

	@Override
	protected void doLoadData(int offset, int count){
		// Reached from the framework's error-retry path (and any other loadData
		// caller): the fill is the only load this view knows.
		startFill();
	}

	@Override
	protected FilterContext getFilterContext(){
		return FilterContext.HOME;
	}

	@Override
	public Uri getWebUri(Uri.Builder base){
		return base.path("/").build();
	}

	private class CatchUpEventListener extends EventListener{
		@Subscribe
		@Override
		public void onStatusCreated(StatusCreatedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			// Spec §3: the snapshot is frozen at open. New posts never insert into
			// Catch up, and the "Posted · Undo" snackbar is a Home affordance.
		}

		@Subscribe
		@Override
		public void onStatusDisplaySettingsChanged(StatusDisplaySettingsChangedEvent ev){
			if(!ev.accountID.equals(accountID))
				return;
			if(!loaded || fillRunning)
				return;
			// The tab's view filters changed: rebuild the grouping from the raw
			// statuses already fetched — no refetch, cursors untouched (spec §4).
			updateScopeAndSummary();
			buildList();
		}
	}

	/**
	 * MAMMUT: audit R4 F3/F4/F5/F9/F10 — the pure Catch up fill/pagination/marker state
	 * machine (docs/AUDIT-1.3.5-TRIAGE.md §Fix 5, improvement #1: the pagination/marker
	 * transitions extracted from the fragment so they are unit-testable). The fragment
	 * owns requests, ingestion side effects and display; every cursor, stop-reason,
	 * marker and footer decision lives here.
	 *
	 * Two cursors are tracked separately (R4 F4):
	 *  - {@link State#networkCursor}: the RAW oldest id of the most recent page,
	 *    captured BEFORE filtering. A page that filtering empties still advances it,
	 *    so the fill can never re-request the same page (the pre-fix repeating page).
	 *  - {@link State#extensionCursor}: starts at the oldest kept wrapper, so
	 *    window-trimmed stragglers stay re-reachable from "Load older posts"; later
	 *    extension pages advance by their own raw oldest.
	 */
	static final class CatchUpFill{
		enum End{NONE, WINDOW_END, CAP, SERVER_END}
		/** R4 F6: the footer states a fetched-originals fact scoped by the window — no "what the server has" variant exists. */
		enum FooterBody{WINDOW, SINCE, CAP, EXTENDED}

		static final class PageItem{
			final String id;
			final long createdAtMillis;

			PageItem(String id, long createdAtMillis){
				this.id=id;
				this.createdAtMillis=createdAtMillis;
			}
		}

		static final class State{
			int pages;
			int keptCount;
			End end=End.NONE;
			boolean capTruncated;
			boolean serverEnded;
			/** Raw oldest id of the most recent page (pre-filter); drives fill pagination and mid-fill retries. */
			String networkCursor;
			/** Oldest ingested wrapper; seeds the first extension fetch. */
			String oldestKeptID;
			/** Cursor for the next "Load older posts" tap. */
			String extensionCursor;
		}

		static State initialState(){
			return new State();
		}

		/**
		 * Accounts for one fill page. {@code rawIDs} lists EVERY id the server
		 * returned, newest first, before filtering; {@code items} lists the filtered
		 * page's (id, createdAt) in the same order. Returns how many leading items
		 * pass the window and must be ingested.
		 */
		static int onFillPage(State s, List<String> rawIDs, List<PageItem> items, long cutoff,
				int pageSize, int maxPosts, int requestCeiling){
			s.pages++;
			if(!rawIDs.isEmpty())
				s.networkCursor=rawIDs.get(rawIDs.size()-1);
			// Inclusion tests the wrapper's createdAt; pages are newest-first, so every
			// wrapper from the first older-than-cutoff one on is a boundary straggler.
			int keep=items.size();
			for(int i=0;i<items.size();i++){
				if(items.get(i).createdAtMillis<cutoff){
					keep=i;
					break;
				}
			}
			if(keep<items.size()){
				s.keptCount+=keep;
				if(keep>0)
					s.oldestKeptID=items.get(keep-1).id;
				s.end=End.WINDOW_END;
				return keep;
			}
			s.keptCount+=items.size();
			if(!items.isEmpty())
				s.oldestKeptID=items.get(items.size()-1).id;
			if(rawIDs.size()<pageSize){
				// Server pagination end: the only condition under which the fill may
				// stop for a reason other than the window or the cap.
				s.end=End.SERVER_END;
				s.serverEnded=true;
			}else if(s.keptCount>=maxPosts){
				s.end=End.CAP;
				s.capTruncated=true;
			}else if(s.pages>=requestCeiling){
				// Coincides with the cap by construction: ceiling x pageSize = maxPosts.
				s.end=End.CAP;
				s.capTruncated=true;
			}
			return keep;
		}

		/** The fragment trimmed `data` to the cap; the kept-oldest cursor must follow the trim. */
		static void noteCapTrim(State s, String trimmedOldestKeptID){
			s.oldestKeptID=trimmedOldestKeptID;
		}

		/** Called once when the fill ends: the first extension re-reads from the oldest kept wrapper. */
		static void prepareExtension(State s){
			s.extensionCursor=s.oldestKeptID;
		}

		/**
		 * Accounts for one extension page. An empty raw page is the server's own
		 * pagination end; anything else advances the extension cursor to the page's
		 * raw oldest so a page that filtering emptied can never repeat either.
		 */
		static void onExtensionPage(State s, List<String> rawIDs, List<PageItem> items){
			if(rawIDs.isEmpty()){
				s.serverEnded=true;
				return;
			}
			s.extensionCursor=rawIDs.get(rawIDs.size()-1);
			if(!items.isEmpty())
				s.oldestKeptID=items.get(items.size()-1).id;
		}

		/**
		 * R4 F5: only a fill that ran to a non-cap stop advanced the whole window the
		 * reader asked about. A CAP-truncated fill leaves the marker where it was, so
		 * the unseen remainder of the window is still inside the next catch-up.
		 */
		static boolean shouldAdvanceMarker(State s){
			return s.end!=End.NONE && !s.capTruncated;
		}

		/** R4 F9: a marker from the future (clock skew, restored backup) is rejected; the window falls back to the 24h floor. */
		static long windowStart(long now, long lastAt, long windowMs){
			long floor=now-windowMs;
			if(lastAt<=0 || lastAt>now || lastAt<=floor)
				return floor;
			return lastAt;
		}

		static boolean hasPreviousCatchUp(long now, long lastAt, long windowMs){
			long floor=now-windowMs;
			return lastAt>0 && lastAt<=now && lastAt>floor;
		}

		/** R4 F6: the footer states what Mammut fetched — scoped by the window when there is one, plain once extended. */
		static FooterBody footerBody(boolean extended, End end, boolean hasPreviousCatchUp){
			if(extended)
				return FooterBody.EXTENDED;
			if(end==End.CAP)
				return FooterBody.CAP;
			return hasPreviousCatchUp ? FooterBody.SINCE : FooterBody.WINDOW;
		}

		/** R4 F10: a filtered-to-empty "Load older posts" is offered only while the server may still have something older. */
		static boolean showFilteredEmptyLoadOlder(State s){
			return !s.serverEnded;
		}
	}
}
