package org.joinmastodon.android.fragments;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.joinmastodon.android.E;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.accounts.AcceptFollowRequest;
import org.joinmastodon.android.api.requests.accounts.GetFollowRequests;
import org.joinmastodon.android.api.requests.accounts.RejectFollowRequest;
import org.joinmastodon.android.events.FollowRequestHandledEvent;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.Relationship;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.viewmodel.AccountViewModel;
import org.joinmastodon.android.ui.BetterItemAnimator;
import org.joinmastodon.android.ui.DividerItemDecoration;
import org.joinmastodon.android.ui.OutlineProviders;
import org.parceler.Parcels;

import java.util.HashMap;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.imageloader.ImageLoaderRecyclerAdapter;
import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.views.UsableRecyclerView;

/**
 * MAMMUT: port from upstream master — the list of pending follow requests, reachable from the
 * notifications toolbar. Rows mirror the notification-request rows instead of master's card UI.
 */
public class FollowRequestsListFragment extends MastodonRecyclerFragment<Account>{
	private String accountID;
	private String maxID;
	private HashMap<String, AccountViewModel> accountViewModels=new HashMap<>();
	private View endMark;
	private FollowRequestsAdapter adapter;

	public FollowRequestsListFragment(){
		super(50);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		accountID=getArguments().getString("account");
		setTitle(R.string.sk_follow_requests);
		loadData();
		E.register(this);
	}

	@Override
	public void onDestroy(){
		E.unregister(this);
		super.onDestroy();
	}

	@Override
	protected void doLoadData(int offset, int count){
		if(!refreshing && endMark!=null)
			endMark.setVisibility(View.GONE);
		currentRequest=new GetFollowRequests(offset==0 ? null : maxID, count)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(HeaderPaginationList<Account> result){
						if(data.isEmpty() || refreshing)
							accountViewModels.clear();
						maxID=result.getNextPageMaxID();
						for(Account acc:result)
							accountViewModels.put(acc.id, new AccountViewModel(acc, accountID, false, getActivity()));
						onDataLoaded(result, !TextUtils.isEmpty(maxID));
						endMark.setVisibility(TextUtils.isEmpty(maxID) ? View.VISIBLE : View.GONE);
					}
				})
				.exec(accountID);
	}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		return adapter=new FollowRequestsAdapter();
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		list.setItemAnimator(new BetterItemAnimator());
		list.addItemDecoration(new DividerItemDecoration(getActivity(), R.attr.colorM3OutlineVariant, 1, 0, 0, vh->vh instanceof FollowRequestViewHolder).setDrawBelowLastItem(true));
	}

	@Override
	protected View onCreateFooterView(LayoutInflater inflater){
		View v=inflater.inflate(R.layout.load_more_with_end_mark, null);
		endMark=v.findViewById(R.id.end_mark);
		endMark.setVisibility(View.GONE);
		return v;
	}

	private void removeAccount(Account acc){
		int index=data.indexOf(acc);
		if(index==-1)
			return;
		data.remove(index);
		adapter.notifyItemRemoved(index);
		if(data.isEmpty() && endMark!=null)
			endMark.setVisibility(View.VISIBLE);
	}

	private class FollowRequestsAdapter extends UsableRecyclerView.Adapter<FollowRequestViewHolder> implements ImageLoaderRecyclerAdapter{
		public FollowRequestsAdapter(){
			super(imgLoader);
		}

		@NonNull
		@Override
		public FollowRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new FollowRequestViewHolder();
		}

		@Override
		public int getItemCount(){
			return data.size();
		}

		@Override
		public void onBindViewHolder(FollowRequestViewHolder holder, int position){
			holder.bind(data.get(position));
			super.onBindViewHolder(holder, position);
		}

		@Override
		public int getImageCountForItem(int position){
			return Objects.requireNonNull(accountViewModels.get(data.get(position).id)).emojiHelper.getImageCount()+1;
		}

		@Override
		public ImageLoaderRequest getImageRequest(int position, int image){
			AccountViewModel model=Objects.requireNonNull(accountViewModels.get(data.get(position).id));
			return switch(image){
				case 0 -> model.avaRequest;
				default -> model.emojiHelper.getImageRequest(image-1);
			};
		}
	}

	private class FollowRequestViewHolder extends BindableViewHolder<Account> implements ImageLoaderViewHolder, UsableRecyclerView.Clickable{
		private final TextView name, username;
		private final ImageView ava;
		private final ImageButton accept, reject;

		public FollowRequestViewHolder(){
			super(getActivity(), R.layout.item_follow_request, list);
			name=findViewById(R.id.name);
			username=findViewById(R.id.username);
			ava=findViewById(R.id.ava);
			accept=findViewById(R.id.btn_accept);
			reject=findViewById(R.id.btn_reject);
			ava.setOutlineProvider(OutlineProviders.roundedRect(8));
			ava.setClipToOutline(true);
			accept.setOnClickListener(v->handle(true));
			reject.setOnClickListener(v->handle(false));
		}

		@Override
		public void onBind(Account item){
			AccountViewModel model=Objects.requireNonNull(accountViewModels.get(item.id));
			name.setText(model.parsedName);
			username.setText(item.getDisplayUsername());
		}

		@Override
		public void setImage(int index, Drawable image){
			if(index==0){
				if(image==null)
					ava.setImageResource(R.drawable.image_placeholder);
				else
					ava.setImageDrawable(image);
			}else{
				AccountViewModel model=Objects.requireNonNull(accountViewModels.get(item.id));
				model.emojiHelper.setImageDrawable(index-1, image);
				name.invalidate();
			}
		}

		@Override
		public void onClick(){
			Bundle args=new Bundle();
			args.putString("account", accountID);
			args.putParcelable("profileAccount", Parcels.wrap(item));
			Nav.go(getActivity(), ProfileFragment.class, args);
		}

		private void handle(boolean accepted){
			Account acc=item;
			accept.setEnabled(false);
			reject.setEnabled(false);
			MastodonAPIRequest<Relationship> req=accepted ? new AcceptFollowRequest(acc.id) : new RejectFollowRequest(acc.id);
			req.setCallback(new Callback<>(){
						@Override
						public void onSuccess(Relationship result){
							removeAccount(acc);
							E.post(new FollowRequestHandledEvent(accountID, accepted, acc, result));
						}

						@Override
						public void onError(ErrorResponse error){
							accept.setEnabled(true);
							reject.setEnabled(true);
							error.showToast(getActivity());
						}
			})
			.exec(accountID);
		}
	}
}
