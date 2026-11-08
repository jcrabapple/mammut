package org.joinmastodon.android.fragments.settings;

import android.os.Bundle;

import org.joinmastodon.android.E;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountLocalPreferences;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.StatusDisplaySettingsChangedEvent;
import org.joinmastodon.android.fragments.HasAccountID;
import org.joinmastodon.android.fragments.StatusListFragment;
import org.joinmastodon.android.model.TimelineDefinition;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: F1 per-tab timeline view filters (docs/FEATURE-PLAN.md §3). The three switches write
 * straight to the tab's TimelineDefinition inside AccountLocalPreferences.timelines, so each tab
 * of each account keeps its own boosts/replies/media-only state across restarts.
 */
public class TimelineViewSettingsFragment extends BaseSettingsFragment<Void> implements HasAccountID{
	/** Optional string resource naming the view that opened this screen (Catch up). */
	public static final String EXTRA_LABEL_RES="labelRes";

	private TimelineDefinition definition;
	private CheckableListItem<Void> showBoostsItem, showRepliesItem, mediaOnlyItem;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.timeline_view);
		int index=getArguments().getInt(StatusListFragment.EXTRA_TIMELINE_DEF_INDEX, -1);
		AccountLocalPreferences lp=AccountSessionManager.get(accountID).getLocalPreferences();
		if(lp.timelines!=null && index>=0 && index<lp.timelines.size())
			definition=lp.timelines.get(index);
		ArrayList<ListItem<Void>> items=new ArrayList<>();
		if(definition!=null){
			// The rules belong to the tab, but the screen was opened from a specific view:
			// when Catch up opens this, the row must name Catch up, not the tab behind it.
			int labelRes=getArguments().getInt(EXTRA_LABEL_RES, 0);
			String label=labelRes!=0 ? getString(labelRes) : definition.getTitle(getActivity()).toString();
			items.add(new ListItem<>(label, getString(R.string.timeline_view_explanation), 0, null, null, 0, true));
			items.add(showBoostsItem=new CheckableListItem<>(R.string.timeline_view_show_boosts, R.string.timeline_view_show_boosts_explanation, CheckableListItem.Style.SWITCH, definition.isShowBoosts(), R.drawable.ic_fluent_arrow_repeat_all_24_regular, i->onShowBoostsClick()));
			items.add(showRepliesItem=new CheckableListItem<>(R.string.timeline_view_show_replies, R.string.timeline_view_show_replies_explanation, CheckableListItem.Style.SWITCH, definition.isShowReplies(), R.drawable.ic_fluent_arrow_reply_24_regular, i->onShowRepliesClick()));
			items.add(mediaOnlyItem=new CheckableListItem<>(R.string.timeline_view_media_only, R.string.timeline_view_media_only_explanation, CheckableListItem.Style.SWITCH, definition.isMediaOnly(), R.drawable.ic_fluent_image_24_regular, i->onMediaOnlyClick()));
			// MAMMUT: the switch itself must persist too. SwitchListItemViewHolder only writes
			// item.checked when there is no checkedChangeListener, so tapping the toggle flipped
			// the control without reaching the save path — the row body saved, the switch did
			// not, and the setting appeared to revert after leaving the screen.
			showBoostsItem.checkedChangeListener=checked->{
				showBoostsItem.checked=checked; // the widget does not write the model back when a listener is set
				definition.setShowBoosts(checked);
				saveAndApply();
			};
			showRepliesItem.checkedChangeListener=checked->{
				showRepliesItem.checked=checked; // the widget does not write the model back when a listener is set
				definition.setShowReplies(checked);
				saveAndApply();
			};
			mediaOnlyItem.checkedChangeListener=checked->{
				mediaOnlyItem.checked=checked; // the widget does not write the model back when a listener is set
				definition.setMediaOnly(checked);
				saveAndApply();
			};
		}
		onDataLoaded(items);
	}

	@Override
	protected void doLoadData(int offset, int count){}

	private void onShowBoostsClick(){
		toggleCheckableItem(showBoostsItem);
		definition.setShowBoosts(showBoostsItem.checked);
		saveAndApply();
	}

	private void onShowRepliesClick(){
		toggleCheckableItem(showRepliesItem);
		definition.setShowReplies(showRepliesItem.checked);
		saveAndApply();
	}

	private void onMediaOnlyClick(){
		toggleCheckableItem(mediaOnlyItem);
		definition.setMediaOnly(mediaOnlyItem.checked);
		saveAndApply();
	}

	private void saveAndApply(){
		AccountSessionManager.get(accountID).getLocalPreferences().save();
		// The visible tab rebuilds its display items from the raw statuses it already holds.
		E.post(new StatusDisplaySettingsChangedEvent(accountID));
	}

	@Override
	public String getAccountID(){
		return accountID;
	}
}
