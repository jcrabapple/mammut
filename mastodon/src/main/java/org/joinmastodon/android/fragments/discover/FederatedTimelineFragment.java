package org.joinmastodon.android.fragments.discover;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.timelines.GetPublicTimeline;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.HomeFragment;
import org.joinmastodon.android.fragments.StatusListFragment;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.utils.DiscoverInfoBannerHelper;
import org.joinmastodon.android.ui.utils.NavToolbarUtils;
import org.joinmastodon.android.utils.ProvidesAssistContent;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.utils.MergeRecyclerAdapter;

public class FederatedTimelineFragment extends StatusListFragment implements ProvidesAssistContent.ProvidesWebUri{
	private DiscoverInfoBannerHelper bannerHelper;

	private String maxID;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		// MAMMUT: title + supporting line, only when this fragment is a root
		// destination; inside a home pager tab the bar belongs to HomeTabFragment
		if(getParentFragment() instanceof HomeFragment){
			setTitle(R.string.nav_destination_federated);
			setSubtitle(R.string.nav_subtitle_federated);
			setHasOptionsMenu(true);
		}
		bannerHelper=new DiscoverInfoBannerHelper(DiscoverInfoBannerHelper.BannerType.FEDERATED_TIMELINE, accountID);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		// MAMMUT: root destinations carry the avatar/account-switcher entry point
		if(getParentFragment() instanceof HomeFragment)
			NavToolbarUtils.addAvatarButton(getActivity(), getToolbar(), getParentFragment(), accountID);
			NavToolbarUtils.addUnreadChip(getActivity(), getToolbar(), getParentFragment(), accountID);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		// MAMMUT: search entry point, root destinations only
		if(getParentFragment() instanceof HomeFragment)
			inflater.inflate(R.menu.nav_toolbar, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(item.getItemId()==R.id.nav_search){
			NavToolbarUtils.openDiscover(getActivity(), accountID);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void doLoadData(int offset, int count){
		currentRequest=new GetPublicTimeline(false, false, getMaxID(), null, count, null, getLocalPrefs().timelineReplyVisibility)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(List<Status> result){
						if(getActivity()==null) return;
						boolean more=applyMaxID(result);
						AccountSessionManager.get(accountID).filterStatuses(result, getFilterContext());
						onDataLoaded(result, more);
						bannerHelper.onBannerBecameVisible();
					}
				})
				.exec(accountID);
	}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		MergeRecyclerAdapter adapter=new MergeRecyclerAdapter();
		bannerHelper.maybeAddBanner(list, adapter);
		adapter.addAdapter(super.getAdapter());
		return adapter;
	}

	@Override
	protected FilterContext getFilterContext() {
		return FilterContext.PUBLIC;
	}

	@Override
	public Uri getWebUri(Uri.Builder base) {
		return base.path(isInstanceAkkoma() ? "/main/all" : "/public").build();
	}
}
