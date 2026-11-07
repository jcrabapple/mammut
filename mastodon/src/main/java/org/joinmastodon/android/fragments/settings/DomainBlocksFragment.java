package org.joinmastodon.android.fragments.settings;

import android.os.Bundle;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.accounts.GetDomainBlocks;
import org.joinmastodon.android.api.requests.accounts.SetDomainBlocked;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.Snackbar;

import java.util.stream.Collectors;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.SimpleCallback;

public class DomainBlocksFragment extends BaseSettingsFragment<String>{
	private String nextMaxID;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_hidden_domains);
		setEmptyText(R.string.no_hidden_domains);
		loadData();
	}

	@Override
	protected void doLoadData(int offset, int count){
		currentRequest=new GetDomainBlocks(offset==0 ? null : nextMaxID, count)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(HeaderPaginationList<String> result){
						if(getActivity()==null)
							return;
						nextMaxID=result.getNextPageMaxID();
						onDataLoaded(result.stream().map(d->makeListItem(d)).collect(Collectors.toList()), nextMaxID!=null);
					}
				})
				.exec(accountID);
	}

	private ListItem<String> makeListItem(String domain){
		return new ListItem<>(domain, null, this::onDomainClick, domain);
	}

	private void onDomainClick(ListItem<String> item){
		String domain=item.parentObject;
		new M3AlertDialogBuilder(getActivity())
				.setTitle(getString(R.string.unblock_domain, domain))
				.setMessage(R.string.unblock_domain_explanation)
				.setPositiveButton(R.string.unblock, (dialog, which)->unblockDomain(item))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void unblockDomain(ListItem<String> item){
		String domain=item.parentObject;
		item.isEnabled=false;
		rebindItem(item);
		new SetDomainBlocked(domain, false)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Object result){
						if(getActivity()==null)
							return;
						int index=data.indexOf(item);
						if(index>=0){
							data.remove(index);
							itemsAdapter.notifyItemRemoved(index);
						}
						new Snackbar.Builder(getActivity())
								.setText(getString(R.string.unblocked_domain_x, domain))
								.show();
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()==null)
							return;
						item.isEnabled=true;
						item.subtitle=getString(R.string.error_unblocking_domain, domain);
						rebindItem(item);
						error.showToast(getActivity());
					}
				})
				.exec(accountID);
	}
}
