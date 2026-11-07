package org.joinmastodon.android.fragments.discover;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.joinmastodon.android.api.requests.trends.GetTrendingStatuses;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.StatusListFragment;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.utils.DiscoverInfoBannerHelper;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.utils.MergeRecyclerAdapter;

public class DiscoverPostsFragment extends StatusListFragment{
	private static final String TAG="DiscoverPostsFragment";
	private DiscoverInfoBannerHelper bannerHelper;
	private int realOffset=0;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		bannerHelper=new DiscoverInfoBannerHelper(DiscoverInfoBannerHelper.BannerType.TRENDING_POSTS, accountID);
	}

	@Override
	protected void doLoadData(int offset, int count){
		// MAMMUT: this screen had no diagnostics at all, which made "it spins forever" impossible
		// to tell apart from "the request never left the device" or "every item was dropped".
		Log.i(TAG, "doLoadData: offset="+offset+" count="+count+" account="+accountID);
		currentRequest=new GetTrendingStatuses(offset==0 ? 0 : realOffset, count)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(List<Status> result){
						Log.i(TAG, "trending statuses: "+result.size()+" items after filtering");
						realOffset+=result.size();
						AccountSessionManager.get(accountID).filterStatuses(result, FilterContext.PUBLIC);
						onDataLoaded(result, !result.isEmpty());
						bannerHelper.onBannerBecameVisible();
					}

					@Override
					public void onError(me.grishka.appkit.api.ErrorResponse error){
						Log.e(TAG, "trending statuses failed: "+error);
						super.onError(error);
					}
				}).exec(accountID);
	}

	@Override
	protected RecyclerView.Adapter getAdapter(){
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
	public Uri getWebUri(Uri.Builder base){
		return isInstanceAkkoma() ? null : base.path("/explore/posts").build();
	}
}
