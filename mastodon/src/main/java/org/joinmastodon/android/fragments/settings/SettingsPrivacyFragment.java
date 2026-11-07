package org.joinmastodon.android.fragments.settings;

import android.os.Bundle;

import androidx.annotation.StringRes;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.Source;
import org.joinmastodon.android.model.StatusPrivacy;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SettingsPrivacyFragment extends BaseSettingsFragment<Void>{
	private CheckableListItem<Void> discoverableItem, indexableItem, lockedItem;
	private ListItem<Void> privacyItem;
	private StatusPrivacy privacy=null;
	private Instance instance;

	// MAMMUT: port from upstream master
	private CheckableListItem<Void> unlistedRepliesItem, removeTrackingParamsItem;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_privacy);
		AccountSession session=AccountSessionManager.get(accountID);
		Account self=session.self;
		if(self.source==null)
			self.source=new Source();
		instance=AccountSessionManager.getInstance().getInstanceInfo(session.domain);
		privacy=self.source.privacy;

		List<ListItem<Void>> items=new ArrayList<>();
		items.add(privacyItem=new ListItem<>(R.string.sk_settings_default_visibility, getPrivacyString(privacy), R.drawable.ic_fluent_eye_24_regular, this::onPrivacyClick, 0, false));
		// MAMMUT: port from upstream master
		items.add(unlistedRepliesItem=new CheckableListItem<>(R.string.mo_change_default_reply_visibility_to_unlisted, R.string.mo_setting_default_reply_privacy_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.defaultToUnlistedReplies, R.drawable.ic_fluent_lock_open_24_regular, this::toggleCheckableItem));
		items.add(removeTrackingParamsItem=new CheckableListItem<>(R.string.mo_settings_remove_tracking_params, R.string.mo_settings_remove_tracking_params_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.removeTrackingParams, R.drawable.ic_fluent_eye_tracking_off_24_filled, i->toggleCheckableItem(removeTrackingParamsItem), true));
		items.add(lockedItem=new CheckableListItem<>(R.string.sk_settings_lock_account, 0, CheckableListItem.Style.SWITCH, self.locked, R.drawable.ic_fluent_person_available_24_regular, this::toggleCheckableItem));

		if(instance==null || !instance.isAkkoma()){
			items.add(discoverableItem=new CheckableListItem<>(R.string.settings_discoverable, 0, CheckableListItem.Style.SWITCH, self.discoverable, R.drawable.ic_thumbs_up_down_24px, this::toggleCheckableItem));
			items.add(indexableItem=new CheckableListItem<>(R.string.settings_indexable, 0, CheckableListItem.Style.SWITCH, self.source.indexable!=null ? self.source.indexable : true, R.drawable.ic_search_24px, this::toggleCheckableItem));
			if(self.source.indexable==null)
				indexableItem.isEnabled=false;
		}

		onDataLoaded(items);
	}

	@Override
	protected void doLoadData(int offset, int count){}

	private @StringRes int getPrivacyString(StatusPrivacy p){
		if(p==null) return R.string.visibility_public;
		return switch(p){
			case PUBLIC -> R.string.visibility_public;
			case UNLISTED -> R.string.sk_visibility_unlisted;
			case PRIVATE -> R.string.visibility_followers_only;
			case DIRECT -> R.string.visibility_private;
			case LOCAL -> R.string.sk_local_only;
		};
	}

	private void onPrivacyClick(ListItem<?> item_){
		List<StatusPrivacy> options=new ArrayList<>(List.of(StatusPrivacy.PUBLIC, StatusPrivacy.UNLISTED, StatusPrivacy.PRIVATE, StatusPrivacy.DIRECT));
		if(instance!=null && instance.isAkkoma())
			options.add(StatusPrivacy.LOCAL);
		int selected=options.indexOf(privacy);
		int[] newSelected={selected};
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.sk_settings_default_visibility)
				.setSingleChoiceItems(options.stream().map(this::getPrivacyString).map(this::getString).toArray(String[]::new),
						selected, (dlg, item)->newSelected[0]=item)
				.setPositiveButton(R.string.ok, (dlg, item)->{
					privacy=options.get(newSelected[0]);
					privacyItem.subtitleRes=getPrivacyString(privacy);
					rebindItem(privacyItem);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@Override
	public void onPause(){
		super.onPause();
		// MAMMUT: port from upstream master
		GlobalUserPreferences.defaultToUnlistedReplies=unlistedRepliesItem.checked;
		GlobalUserPreferences.removeTrackingParams=removeTrackingParamsItem.checked;
		GlobalUserPreferences.save();

		AccountSession s=AccountSessionManager.get(accountID);
		Account self=s.self;
		boolean savePlease=self.locked!=lockedItem.checked
				|| self.source.privacy!=privacy
				|| (discoverableItem!=null && self.discoverable!=discoverableItem.checked)
				|| (indexableItem!=null && self.source.indexable!=null && self.source.indexable!=indexableItem.checked);
		if(savePlease){
			if(discoverableItem!=null)
				self.discoverable=discoverableItem.checked;
			if(indexableItem!=null)
				self.source.indexable=indexableItem.checked;
			self.locked=lockedItem.checked;
			self.source.privacy=privacy;
			if(s.preferences!=null)
				s.preferences.postingDefaultVisibility=privacy;
			s.savePreferencesLater();
		}
	}
}
