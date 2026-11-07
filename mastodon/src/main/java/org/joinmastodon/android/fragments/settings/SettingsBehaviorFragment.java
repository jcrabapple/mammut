package org.joinmastodon.android.fragments.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;

import java.util.List;
import java.util.stream.IntStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public class SettingsBehaviorFragment extends BaseSettingsFragment<Void>{
	private ListItem<Void> customTabsItem;
	private CheckableListItem<Void> altTextItem, playGifsItem, confirmUnfollowItem, confirmBoostItem, confirmDeleteItem;

	// MAMMUT: phase 1 port from upstream master
	private ListItem<Void> prefixRepliesItem;
	private CheckableListItem<Void> hapticFeedbackItem, mentionRebloggerAutomaticallyItem;
	// MAMMUT: parity port from upstream master
	private CheckableListItem<Void> loadNewPostsItem, seeNewPostsBtnItem;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_behavior);

		onDataLoaded(List.of(
				customTabsItem=new ListItem<>(R.string.settings_custom_tabs, GlobalUserPreferences.useCustomTabs ? R.string.in_app_browser : R.string.system_browser, R.drawable.ic_open_in_browser_24px, this::onCustomTabsClick),
				altTextItem=new CheckableListItem<>(R.string.settings_alt_text_reminders, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.altTextReminders, R.drawable.ic_alt_24px, this::toggleCheckableItem),
				playGifsItem=new CheckableListItem<>(R.string.settings_gif, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.playGifs, R.drawable.ic_animation_24px, this::toggleCheckableItem),
				// MAMMUT: phase 1 port from upstream master
				prefixRepliesItem=new ListItem<>(R.string.sk_settings_prefix_reply_cw_with_re, getPrefixWithRepliesString(), R.drawable.ic_fluent_arrow_reply_24_regular, this::onPrefixRepliesClick),
				loadNewPostsItem=new CheckableListItem<>(R.string.sk_settings_load_new_posts, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.loadNewPosts, R.drawable.ic_fluent_arrow_sync_24_regular, i->onLoadNewPostsClick()),
				seeNewPostsBtnItem=new CheckableListItem<>(R.string.sk_settings_see_new_posts_button, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.showNewPostsButton, R.drawable.ic_fluent_arrow_up_24_regular, this::toggleCheckableItem),
				hapticFeedbackItem=new CheckableListItem<>(R.string.mo_haptic_feedback, R.string.mo_setting_haptic_feedback_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.hapticFeedback, R.drawable.ic_fluent_phone_vibrate_24_regular, this::toggleCheckableItem),
				mentionRebloggerAutomaticallyItem=new CheckableListItem<>(R.string.mo_mention_reblogger_automatically, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.mentionRebloggerAutomatically, R.drawable.ic_fluent_comment_mention_24_regular, this::toggleCheckableItem),
				confirmUnfollowItem=new CheckableListItem<>(R.string.settings_confirm_unfollow, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmUnfollow, R.drawable.ic_person_remove_24px, this::toggleCheckableItem),
				confirmBoostItem=new CheckableListItem<>(R.string.settings_confirm_boost, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmBoost, R.drawable.ic_repeat_24px, this::toggleCheckableItem),
				confirmDeleteItem=new CheckableListItem<>(R.string.settings_confirm_delete_post, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.confirmDeletePost, R.drawable.ic_delete_24px, this::toggleCheckableItem)
		));
	}

	@Override
	protected void doLoadData(int offset, int count){}

	private void onCustomTabsClick(ListItem<?> item){
		Intent intent=new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"));
		ResolveInfo info=getActivity().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
		final String browserName;
		if(info==null){
			browserName="??";
		}else{
			browserName=info.loadLabel(getActivity().getPackageManager()).toString();
		}
		ArrayAdapter<CharSequence> adapter=new ArrayAdapter<>(getActivity(), R.layout.item_alert_single_choice_2lines_but_different, R.id.text,
				new String[]{getString(R.string.in_app_browser), getString(R.string.system_browser)}){
			@Override
			public boolean hasStableIds(){
				return true;
			}

			@NonNull
			@Override
			public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent){
				View view=super.getView(position, convertView, parent);
				TextView subtitle=view.findViewById(R.id.subtitle);
				if(position==0){
					subtitle.setVisibility(View.GONE);
				}else{
					subtitle.setVisibility(View.VISIBLE);
					subtitle.setText(browserName);
				}
				return view;
			}
		};
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.settings_custom_tabs)
				.setSingleChoiceItems(adapter, GlobalUserPreferences.useCustomTabs ? 0 : 1, (dlg, which)->{
					GlobalUserPreferences.useCustomTabs=which==0;
					customTabsItem.subtitleRes=GlobalUserPreferences.useCustomTabs ? R.string.in_app_browser : R.string.system_browser;
					rebindItem(customTabsItem);
					dlg.dismiss();
				})
				.show();
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		GlobalUserPreferences.playGifs=playGifsItem.checked;
		GlobalUserPreferences.altTextReminders=altTextItem.checked;
		// MAMMUT: phase 1 port from upstream master
		GlobalUserPreferences.hapticFeedback=hapticFeedbackItem.checked;
		GlobalUserPreferences.mentionRebloggerAutomatically=mentionRebloggerAutomaticallyItem.checked;
		GlobalUserPreferences.confirmUnfollow=confirmUnfollowItem.checked;
		GlobalUserPreferences.confirmBoost=confirmBoostItem.checked;
		GlobalUserPreferences.confirmDeletePost=confirmDeleteItem.checked;
		// MAMMUT: parity port from upstream master
		GlobalUserPreferences.loadNewPosts=loadNewPostsItem.checked;
		GlobalUserPreferences.showNewPostsButton=seeNewPostsBtnItem.checked;
		GlobalUserPreferences.save();
	}

	// MAMMUT: parity port from upstream master
	private void onLoadNewPostsClick(){
		toggleCheckableItem(loadNewPostsItem);
		seeNewPostsBtnItem.checked=loadNewPostsItem.checked;
		seeNewPostsBtnItem.isEnabled=loadNewPostsItem.checked;
		rebindItem(seeNewPostsBtnItem);
	}

	// MAMMUT: phase 1 port from upstream master
	private @StringRes int getPrefixWithRepliesString(){
		return switch(GlobalUserPreferences.prefixReplies){
			case NEVER -> R.string.sk_settings_prefix_replies_never;
			case ALWAYS -> R.string.sk_settings_prefix_replies_always;
			case TO_OTHERS -> R.string.sk_settings_prefix_replies_to_others;
		};
	}

	// MAMMUT: phase 1 port from upstream master
	private void onPrefixRepliesClick(ListItem<?> item_){
		int selected=GlobalUserPreferences.prefixReplies.ordinal();
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.sk_settings_prefix_reply_cw_with_re)
				.setSingleChoiceItems((String[]) IntStream.of(R.string.sk_settings_prefix_replies_never, R.string.sk_settings_prefix_replies_always, R.string.sk_settings_prefix_replies_to_others).mapToObj(this::getString).toArray(String[]::new),
						selected, (dlg, which)->{
							GlobalUserPreferences.prefixReplies=GlobalUserPreferences.PrefixRepliesMode.values()[which];
							GlobalUserPreferences.save();
							prefixRepliesItem.subtitleRes=getPrefixWithRepliesString();
							rebindItem(prefixRepliesItem);
							dlg.dismiss();
						})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}
}
