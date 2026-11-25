package org.joinmastodon.android.fragments.settings;

import android.os.Bundle;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.HasAccountID;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

import me.grishka.appkit.Nav;

/**
 * Per-instance server information and account web-settings shortcuts.
 */
public class SettingsInstanceFragment extends BaseSettingsFragment<Void> implements HasAccountID{
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.sk_settings_instance);
		AccountSession s=AccountSessionManager.get(accountID);
		ArrayList<ListItem<Void>> items=new ArrayList<>(List.of(
				new ListItem<>(s.domain, getString(R.string.settings_server_explanation), R.drawable.ic_fluent_server_24_regular, this::onServerClick),
				new ListItem<>(R.string.sk_settings_profile, 0, R.drawable.ic_fluent_open_24_regular, i->UiUtils.launchWebBrowser(getActivity(), "https://"+s.domain+"/settings/profile")),
				new ListItem<>(R.string.sk_settings_posting, 0, R.drawable.ic_fluent_open_24_regular, i->UiUtils.launchWebBrowser(getActivity(), "https://"+s.domain+"/settings/preferences/other")),
				new ListItem<>(R.string.sk_settings_auth, 0, R.drawable.ic_fluent_open_24_regular, i->UiUtils.launchWebBrowser(getActivity(), "https://"+s.domain+"/auth/edit"))
		));
		onDataLoaded(items);
	}

	@Override
	protected void doLoadData(int offset, int count){}

	private void onServerClick(ListItem<?> item){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		Nav.go(getActivity(), SettingsServerFragment.class, args);
	}

	@Override
	public String getAccountID(){
		return accountID;
	}
}
