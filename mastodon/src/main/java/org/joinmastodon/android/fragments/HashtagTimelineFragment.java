package org.joinmastodon.android.fragments;

import android.app.Activity;
import android.content.res.TypedArray;import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonErrorResponse;
import org.joinmastodon.android.api.requests.filters.CreateFilter;
import org.joinmastodon.android.api.requests.filters.DeleteFilter;
import org.joinmastodon.android.api.requests.filters.GetFilters;
import org.joinmastodon.android.api.requests.tags.GetTag;
import org.joinmastodon.android.api.requests.tags.SetTagFollowed;
import org.joinmastodon.android.api.requests.timelines.GetHashtagTimeline;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Filter;
import org.joinmastodon.android.model.FilterAction;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.FilterKeyword;
import org.joinmastodon.android.model.Hashtag;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.TimelineDefinition;
import org.joinmastodon.android.ui.Snackbar;
import org.joinmastodon.android.ui.sheets.MuteHashtagConfirmationSheet;
import org.joinmastodon.android.ui.text.SpacerSpan;
import org.joinmastodon.android.ui.views.ProgressBarButton;
import org.parceler.Parcels;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.utils.MergeRecyclerAdapter;
import me.grishka.appkit.utils.SingleViewRecyclerAdapter;
import me.grishka.appkit.utils.V;

public class HashtagTimelineFragment extends PinnableStatusListFragment {
	private Hashtag hashtag;
	private String hashtagName;
	private TextView headerTitle, headerSubtitle;
	private ProgressBarButton followButton;
	private ProgressBar followProgress;
	private MenuItem followMenuItem, muteMenuItem;
	private boolean followRequestRunning;
	private boolean toolbarContentVisible;
	private String maxID;

	// MAMMUT: phase 2 port from upstream master — hashtag muting is implemented as a client-side filter
	private Optional<Filter> filter=Optional.empty();

	// MOSHIDON
	@Override
	protected boolean wantsComposeButton() {
		return true;
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		if(getArguments().containsKey("hashtag")){
			hashtag=Parcels.unwrap(getArguments().getParcelable("hashtag"));
			hashtagName=hashtag.name;
		}else{
			hashtagName=getArguments().getString("hashtagName");
		}
		setTitle('#'+hashtagName);
		setHasOptionsMenu(true);
	}

	@Override
	protected void doLoadData(int offset, int count){
		currentRequest=new GetHashtagTimeline(hashtagName, offset==0 ? null : maxID, null, count)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(List<Status> result){
						if(!result.isEmpty())
							maxID=result.get(result.size()-1).id;
						AccountSessionManager.get(accountID).filterStatuses(result, FilterContext.PUBLIC);
						onDataLoaded(result, !result.isEmpty());
					}
				})
				.exec(accountID);
	}

	@Override
	protected void onShown(){
		super.onShown();
		if(!getArguments().getBoolean("noAutoLoad") && !loaded && !dataLoading)
			loadData();
	}

	@Override
	public void loadData(){
		reloadTag();
		super.loadData();
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		fab=view.findViewById(R.id.fab);
		fab.setOnClickListener(this::onFabClick);

		// MOSHIDON:
		if(getParentFragment() instanceof HomeTabFragment) return;

		list.addOnScrollListener(new RecyclerView.OnScrollListener(){
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
				View topChild=recyclerView.getChildAt(0);
				int firstChildPos=recyclerView.getChildAdapterPosition(topChild);
				float newAlpha=firstChildPos>0 ? 1f : Math.min(1f, -topChild.getTop()/(float)headerTitle.getHeight());

				// MOSHIDON:
//				toolbarTitleView.setAlpha(newAlpha);

				boolean newToolbarVisibility=newAlpha>0.5f;
				if(newToolbarVisibility!=toolbarContentVisible){
					toolbarContentVisible=newToolbarVisibility;
					if(followMenuItem!=null)
						followMenuItem.setVisible(toolbarContentVisible);
				}
			}
		});
	}

	// MOSHIDON:
	@Override
	public void onFabClick(View v){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		args.putString("prefilledText", '#'+hashtagName+' ');
		Nav.go(getActivity(), ComposeFragment.class, args);
	}

	@Override
	protected void onSetFabBottomInset(int inset){
		((ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin=V.dp(16)+inset;
	}

	@Override
	protected RecyclerView.Adapter getAdapter(){
		View header=getActivity().getLayoutInflater().inflate(R.layout.header_hashtag_timeline, list, false);
		headerTitle=header.findViewById(R.id.title);
		headerSubtitle=header.findViewById(R.id.subtitle);
		followButton=header.findViewById(R.id.profile_action_btn);
		followProgress=header.findViewById(R.id.action_progress);

		headerTitle.setText("#"+hashtagName);
		followButton.setVisibility(View.GONE);
		followButton.setOnClickListener(v->{
			if(hashtag==null)
				return;
			setFollowed(!hashtag.following);
		});
		updateHeader();

		MergeRecyclerAdapter mergeAdapter=new MergeRecyclerAdapter();

		// MOSHIDON: so the fragment can integrate nicely in the HomeTabFragment
		if(!(getParentFragment() instanceof HomeTabFragment)){
			mergeAdapter.addAdapter(new SingleViewRecyclerAdapter(header));
		}

		mergeAdapter.addAdapter(super.getAdapter());
		return mergeAdapter;
	}

	@Override
	public int getMainAdapterOffset(){
		return 1;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		followMenuItem=menu.add(getString(hashtag!=null && hashtag.following ? R.string.unfollow_user : R.string.follow_user, "#"+hashtagName));
		followMenuItem.setVisible(toolbarContentVisible);
		followMenuItem.setIcon(hashtag!=null && hashtag.following ? R.drawable.ic_fluent_person_delete_24_filled : R.drawable.ic_fluent_person_add_24_regular);
		followMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

		// MAMMUT: phase 2 port from upstream master
		muteMenuItem=menu.add(0, 1, 1, getString(R.string.mute_user, "#"+hashtagName));
		muteMenuItem.setIcon(R.drawable.ic_fluent_speaker_off_24_regular);
		muteMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		muteMenuItem.setVisible(toolbarContentVisible);
		new GetFilters().setCallback(new Callback<>(){
			@Override
			public void onSuccess(List<Filter> filters){
				if(getActivity()==null) return;
				// MAMMUT: hashtags are muted via a whole-word filter titled "#name"
				filter=filters.stream().filter(f -> f.title.equals("#"+hashtagName)).findAny();
				updateMuteState(filter.isPresent());
			}

			@Override
			public void onError(ErrorResponse error){
				if(getActivity()!=null) error.showToast(getActivity());
			}
		}).exec(accountID);
	}

	// MAMMUT: phase 2 port from upstream master
	private void updateMuteState(boolean newMute){
		if(muteMenuItem==null) return;
		muteMenuItem.setTitle(getString(newMute ? R.string.unmute_user : R.string.mute_user, "#"+hashtagName));
		muteMenuItem.setIcon(newMute ? R.drawable.ic_fluent_speaker_2_24_regular : R.drawable.ic_fluent_speaker_off_24_regular);
	}

	// MAMMUT: phase 2 port from upstream master
	private void showMuteDialog(boolean currentlyMuted){
		if(currentlyMuted){
			unmuteHashtag();
			return;
		}
		if(hashtag==null){
			// entered via hashtag name only; the sheet only needs the name
			Hashtag tag=new Hashtag();
			tag.name=hashtagName;
			hashtag=tag;
		}

		// pass references, so they can be changed inside the confirmation sheet
		AtomicReference<Duration> muteDuration=new AtomicReference<>(Duration.ZERO);
		new MuteHashtagConfirmationSheet(getContext(), null, muteDuration, hashtag, (onSuccess, onError)->{
			FilterKeyword hashtagFilter=new FilterKeyword();
			hashtagFilter.wholeWord=true;
			hashtagFilter.keyword="#"+hashtagName;
			new CreateFilter("#"+hashtagName, EnumSet.of(FilterContext.HOME), FilterAction.HIDE, (int) muteDuration.get().getSeconds(), List.of(hashtagFilter)).setCallback(new Callback<>(){
				@Override
				public void onSuccess(Filter result){
					filter=Optional.of(result);
					updateMuteState(true);
					onSuccess.run();
				}

				@Override
				public void onError(ErrorResponse error){
					error.showToast(getContext());
					onError.run();
				}
			}).exec(accountID);
		}).show();
	}

	// MAMMUT: phase 2 port from upstream master
	private void unmuteHashtag(){
		// safe to get, this is only called if filter is present
		new DeleteFilter(filter.get().id).setCallback(new Callback<>(){
			@Override
			public void onSuccess(Void result){
				filter=Optional.empty();
				updateMuteState(false);
				new Snackbar.Builder(getContext())
						.setText(getContext().getString(R.string.unmuted_user_x, '#'+hashtagName))
						.show();
			}

			@Override
			public void onError(ErrorResponse error){
				error.showToast(getContext());
			}
		}).exec(accountID);
	}

	// MOSHIDON:
	@Override
	protected TimelineDefinition makeTimelineDefinition(){
		return TimelineDefinition.ofHashtag(hashtagName);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		// MAMMUT: phase 2 port from upstream master — distinguish the two programmatically-created items by id
		if(item.getItemId()==1){
			if(hashtag!=null && filter!=null)
				showMuteDialog(filter.isPresent());
			return true;
		}
		if(hashtag!=null){
			setFollowed(!hashtag.following);
		}
		return true;
	}

	@Override
	protected void onUpdateToolbar(){
		super.onUpdateToolbar();
		toolbarTitleView.setAlpha(toolbarContentVisible ? 1f : 0f);
		if(followMenuItem!=null)
			followMenuItem.setVisible(toolbarContentVisible);
		if(muteMenuItem!=null)
			muteMenuItem.setVisible(toolbarContentVisible);
	}

	private void updateHeader(){
		if(hashtag==null || getActivity()==null)
			return;

		if(hashtag.history!=null && !hashtag.history.isEmpty()){
			int weekPosts=hashtag.history.stream().mapToInt(h->h.uses).sum();
			int todayPosts=hashtag.history.get(0).uses;
			int numAccounts=hashtag.history.stream().mapToInt(h->h.accounts).sum();
			int hSpace=V.dp(8);
			SpannableStringBuilder ssb=new SpannableStringBuilder();
			ssb.append(getResources().getQuantityString(R.plurals.x_posts, weekPosts, weekPosts));
			ssb.append(" ", new SpacerSpan(hSpace, 0), 0);
			ssb.append('·');
			ssb.append(" ", new SpacerSpan(hSpace, 0), 0);
			ssb.append(getResources().getQuantityString(R.plurals.x_participants, numAccounts, numAccounts));
			ssb.append(" ", new SpacerSpan(hSpace, 0), 0);
			ssb.append('·');
			ssb.append(" ", new SpacerSpan(hSpace, 0), 0);
			ssb.append(getResources().getQuantityString(R.plurals.x_posts_today, todayPosts, todayPosts));
			headerSubtitle.setText(ssb);
		}

		int styleRes;
		followButton.setVisibility(View.VISIBLE);
		if(hashtag.following){
			followButton.setText(R.string.button_following);
			styleRes=R.style.Widget_Mastodon_M3_Button_Tonal;
		}else{
			followButton.setText(R.string.button_follow);
			styleRes=R.style.Widget_Mastodon_M3_Button_Filled;
		}
		TypedArray ta=followButton.getContext().obtainStyledAttributes(styleRes, new int[]{android.R.attr.background});
		followButton.setBackground(ta.getDrawable(0));
		ta.recycle();
		ta=followButton.getContext().obtainStyledAttributes(styleRes, new int[]{android.R.attr.textColor});
		followButton.setTextColor(ta.getColorStateList(0));
		followProgress.setIndeterminateTintList(ta.getColorStateList(0));
		ta.recycle();

		followButton.setTextVisible(true);
		followProgress.setVisibility(View.GONE);
		if(followMenuItem!=null){
			followMenuItem.setTitle(getString(hashtag.following ? R.string.unfollow_user : R.string.follow_user, "#"+hashtagName));
		}
	}

	private void reloadTag(){
		new GetTag(hashtagName)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Hashtag result){
						hashtag=result;
						updateHeader();
					}

					@Override
					public void onError(ErrorResponse error){

					}
				})
				.exec(accountID);
	}

	private void setFollowed(boolean followed){
		if(followRequestRunning)
			return;
		followButton.setTextVisible(false);
		followProgress.setVisibility(View.VISIBLE);
		followRequestRunning=true;
		new SetTagFollowed(hashtagName, followed)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Hashtag result){
						if(getActivity()==null)
							return;
						hashtag=result;
						updateHeader();
						followRequestRunning=false;
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()==null)
							return;
						if(error instanceof MastodonErrorResponse er && "Duplicate record".equals(er.error)){
							hashtag.following=true;
						}else{
							error.showToast(getActivity());
						}
						updateHeader();
						followRequestRunning=false;
					}
				})
				.exec(accountID);
	}

	public String getHashtagName(){
		return hashtagName;
	}


	// MOSHIDON:
	@Override
	protected FilterContext getFilterContext() {
		return FilterContext.PUBLIC;
	}

	@Override
	public Uri getWebUri(Uri.Builder base) {
		return base.path((isInstanceAkkoma() ? "/tag/" : "/tags/") + hashtagName).build();
	}
}
