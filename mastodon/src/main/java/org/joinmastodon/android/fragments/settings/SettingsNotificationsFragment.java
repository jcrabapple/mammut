package org.joinmastodon.android.fragments.settings;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.PushSubscriptionManager;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.PushSubscription;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.utils.HideableSingleViewRecyclerAdapter;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.utils.UnifiedPushHelper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.utils.MergeRecyclerAdapter;

public class SettingsNotificationsFragment extends BaseSettingsFragment<Void>{
	private PushSubscription pushSubscription;
	private CheckableListItem<Void> pauseItem;
	private ListItem<Void> policyItem;
	private MergeRecyclerAdapter mergeAdapter;

	private HideableSingleViewRecyclerAdapter bannerAdapter;
	private ImageView bannerIcon;
	private TextView bannerText;
	private Button bannerButton;

	private CheckableListItem<Void> mentionsItem, boostsItem, favoritesItem, followersItem, pollsItem, statusesItem, quotesItem;
	// MAMMUT: parity port from upstream master
	private CheckableListItem<Void> uniformIconItem, swapBookmarkWithReblogItem, deleteNotificationsItem;
	// MAMMUT: port from upstream master
	private CheckableListItem<Void> unifiedPushItem;
	private boolean useUnifiedPush, hasAnyUnifiedPushDistributor;
	// MAMMUT: guard against the listener firing again when we reset the switch programmatically
	private boolean suppressUnifiedPushCallback;
	private List<CheckableListItem<Void>> typeItems;
	private boolean needUpdateNotificationSettings;
	private boolean notificationsAllowed=true;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_notifications);

		getPushSubscription();
		// MAMMUT: port from upstream master
		useUnifiedPush=UnifiedPushHelper.isUnifiedPushEnabled(getContext());
		hasAnyUnifiedPushDistributor=UnifiedPushHelper.hasAnyDistributorInstalled(getContext());
		if(useUnifiedPush && !UnifiedPushHelper.isRegistrationAcknowledged(getContext())){
			// A distributor is chosen but the registration never completed; retry quietly.
			UnifiedPushHelper.registerAllAccounts(getContext());
		}

		onDataLoaded(List.of(
				pauseItem=new CheckableListItem<>(getString(R.string.pause_all_notifications), getPauseItemSubtitle(), CheckableListItem.Style.SWITCH, false, R.drawable.ic_notifications_paused_24px, i->onPauseNotificationsClick(false)),
				policyItem=new ListItem<>(R.string.settings_notifications_policy, 0, R.drawable.ic_group_24px, this::onNotificationsPolicyClick),

				mentionsItem=new CheckableListItem<>(R.string.notification_type_mentions_and_replies, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.mention, this::toggleCheckableItem),
				boostsItem=new CheckableListItem<>(R.string.notification_type_reblog, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.reblog, this::toggleCheckableItem),
				favoritesItem=new CheckableListItem<>(R.string.notification_type_favorite, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.favourite, this::toggleCheckableItem),
				followersItem=new CheckableListItem<>(R.string.notification_type_follow, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.follow, this::toggleCheckableItem),
				pollsItem=new CheckableListItem<>(R.string.notification_type_poll, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.poll, this::toggleCheckableItem),
				statusesItem=new CheckableListItem<>(R.string.notification_type_status, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.status, this::toggleCheckableItem),
				quotesItem=new CheckableListItem<>(R.string.notification_type_quote, 0, CheckableListItem.Style.CHECKBOX, pushSubscription.alerts.quote, this::toggleCheckableItem),
				uniformIconItem=new CheckableListItem<>(R.string.sk_settings_uniform_icon_for_notifications, R.string.mo_setting_uniform_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.uniformNotificationIcon, R.drawable.ic_ntf_logo, this::toggleCheckableItem),
				swapBookmarkWithReblogItem=new CheckableListItem<>(R.string.mo_swap_bookmark_with_reblog, R.string.mo_swap_bookmark_with_reblog_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.swapBookmarkWithBoostAction, R.drawable.ic_boost, this::toggleCheckableItem),
				deleteNotificationsItem=new CheckableListItem<>(R.string.sk_settings_enable_delete_notifications, 0, CheckableListItem.Style.SWITCH, GlobalUserPreferences.enableDeleteNotifications, R.drawable.ic_fluent_mail_inbox_dismiss_24_regular, this::toggleCheckableItem),
				unifiedPushItem=new CheckableListItem<>(R.string.sk_settings_unifiedpush, 0, CheckableListItem.Style.SWITCH, useUnifiedPush, R.drawable.ic_fluent_alert_24_regular, i->onUnifiedPushClick(), true)
		));

		typeItems=List.of(mentionsItem, boostsItem, favoritesItem, followersItem, pollsItem, statusesItem, quotesItem);
		pauseItem.checkedChangeListener=checked->onPauseNotificationsClick(true);
		// MAMMUT: port from upstream master
		unifiedPushItem.checkedChangeListener=checked->{
			if(suppressUnifiedPushCallback)
				return;
			onUnifiedPushClick();
		};
		unifiedPushItem.isEnabled=hasAnyUnifiedPushDistributor;
		if(!hasAnyUnifiedPushDistributor)
			unifiedPushItem.subtitleRes=R.string.sk_settings_unifiedpush_no_distributor;
		updatePolicyItem(null);
		updatePauseItem();
	}

	@Override
	protected void doLoadData(int offset, int count){}

	@Override
	protected void onHidden(){
		super.onHidden();
		PushSubscription ps=getPushSubscription();
		needUpdateNotificationSettings|=mentionsItem.checked!=ps.alerts.mention
				|| boostsItem.checked!=ps.alerts.reblog
				|| favoritesItem.checked!=ps.alerts.favourite
				|| followersItem.checked!=ps.alerts.follow
				|| pollsItem.checked!=ps.alerts.poll
				|| statusesItem.checked!=ps.alerts.status
				|| quotesItem.checked!=ps.alerts.quote;
		// MAMMUT: parity port from upstream master
		GlobalUserPreferences.uniformNotificationIcon=uniformIconItem.checked;
		GlobalUserPreferences.swapBookmarkWithBoostAction=swapBookmarkWithReblogItem.checked;
		GlobalUserPreferences.enableDeleteNotifications=deleteNotificationsItem.checked;
		GlobalUserPreferences.save();
		if(needUpdateNotificationSettings && PushSubscriptionManager.arePushNotificationsAvailable()){
			ps.alerts.mention=mentionsItem.checked;
			ps.alerts.reblog=boostsItem.checked;
			ps.alerts.favourite=favoritesItem.checked;
			ps.alerts.follow=followersItem.checked;
			ps.alerts.poll=pollsItem.checked;
			ps.alerts.status=statusesItem.checked;
			ps.alerts.quote=quotesItem.checked;
			AccountSessionManager.getInstance().getAccount(accountID).getPushSubscriptionManager().updatePushSettings(pushSubscription);
		}
	}

	@Override
	protected void onShown(){
		super.onShown();
		boolean allowed=areNotificationsAllowed();
		PushSubscription ps=getPushSubscription();
		if(allowed!=notificationsAllowed){
			notificationsAllowed=allowed;
			updateBanner();
			pauseItem.isEnabled=allowed;
			policyItem.isEnabled=allowed;
			rebindItem(pauseItem);
			rebindItem(policyItem);
			for(CheckableListItem<Void> item:typeItems){
				item.isEnabled=allowed && ps.policy!=PushSubscription.Policy.NONE;
				rebindItem(item);
			}
		}
	}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		View banner=getActivity().getLayoutInflater().inflate(R.layout.item_settings_banner, list, false);
		bannerText=banner.findViewById(R.id.text);
		bannerIcon=banner.findViewById(R.id.icon);
		bannerButton=banner.findViewById(R.id.button);
		bannerAdapter=new HideableSingleViewRecyclerAdapter(banner);
		bannerAdapter.setVisible(false);
		banner.findViewById(R.id.button2).setVisibility(View.GONE);
		banner.findViewById(R.id.title).setVisibility(View.GONE);

		mergeAdapter=new MergeRecyclerAdapter();
		mergeAdapter.addAdapter(bannerAdapter);
		mergeAdapter.addAdapter(super.getAdapter());
		return mergeAdapter;
	}

	@Override
	protected int indexOfItemsAdapter(){
		return mergeAdapter.getPositionForAdapter(itemsAdapter);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		updateBanner();
	}

	private boolean areNotificationsAllowed(){
		return Build.VERSION.SDK_INT<Build.VERSION_CODES.N || getActivity().getSystemService(NotificationManager.class).areNotificationsEnabled();
	}

	private PushSubscription getPushSubscription(){
		if(pushSubscription!=null)
			return pushSubscription;
		AccountSession session=AccountSessionManager.getInstance().getAccount(accountID);
		if(session.pushSubscription==null){
			pushSubscription=new PushSubscription();
			pushSubscription.alerts=PushSubscription.Alerts.ofAll();
		}else{
			pushSubscription=session.pushSubscription.clone();
		}
		return pushSubscription;
	}

	private String getPauseItemSubtitle(){
		return getString(R.string.pause_notifications_off);
	}

	private void resumePausedNotifications(){
		AccountSessionManager.get(accountID).getLocalPreferences().setNotificationsPauseEndTime(0);
		updatePauseItem();
	}

	private void openSystemNotificationSettings(){
		Intent intent;
		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O){
			intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getActivity().getPackageName(), null));
		}else{
			intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
			intent.putExtra(Settings.EXTRA_APP_PACKAGE, getActivity().getPackageName());
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private void onPauseNotificationsClick(boolean fromSwitch){
		long time=AccountSessionManager.get(accountID).getLocalPreferences().getNotificationsPauseEndTime();
		if(time>System.currentTimeMillis() && fromSwitch){
			resumePausedNotifications();
			return;
		}
		int[] durationOptions={
				1800,
				3600,
				12*3600,
				24*3600,
				3*24*3600,
				7*24*3600
		};
		AlertDialog alert=new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.pause_all_notifications_title)
				.setSupportingText(time>System.currentTimeMillis() ? getString(R.string.pause_notifications_ends, UiUtils.formatRelativeTimestampAsMinutesAgo(getActivity(), Instant.ofEpochMilli(time), false)) : null)
				.setSingleChoiceItems((String[])Arrays.stream(durationOptions).mapToObj(d->UiUtils.formatDuration(getActivity(), d)).toArray(String[]::new), -1, (dlg, item)->{
					AccountSessionManager.get(accountID).getLocalPreferences().setNotificationsPauseEndTime(System.currentTimeMillis()+durationOptions[item]*1000L);
					dlg.dismiss();
				})
				.show();
		alert.setOnDismissListener(dialog->updatePauseItem());
		alert.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
	}

	private void onNotificationsPolicyClick(ListItem<?> item_){
		String[] items=Stream.of(
				R.string.notifications_policy_anyone,
				R.string.notifications_policy_followed,
				R.string.notifications_policy_follower,
				R.string.notifications_policy_no_one
		).map(this::getString).toArray(String[]::new);
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.settings_notifications_policy)
				.setSingleChoiceItems(items, getPushSubscription().policy.ordinal(), (dlg, which)->{
					dlg.dismiss();
					PushSubscription.Policy prevValue=getPushSubscription().policy;
					PushSubscription.Policy newValue=PushSubscription.Policy.values()[which];
					if(prevValue==newValue)
						return;
					getPushSubscription().policy=newValue;
					updatePolicyItem(prevValue);
					needUpdateNotificationSettings=true;
				})
				.show();
	}

	private void updatePolicyItem(PushSubscription.Policy prevValue){
		policyItem.subtitleRes=switch(getPushSubscription().policy){
			case ALL -> R.string.notifications_policy_anyone;
			case FOLLOWED -> R.string.notifications_policy_followed;
			case FOLLOWER -> R.string.notifications_policy_follower;
			case NONE -> R.string.notifications_policy_no_one;
		};
		rebindItem(policyItem);
		if(pushSubscription.policy==PushSubscription.Policy.NONE || prevValue==PushSubscription.Policy.NONE){
			for(CheckableListItem<Void> item:typeItems){
				item.checked=item.isEnabled=prevValue==PushSubscription.Policy.NONE;
				rebindItem(item);
			}
		}
	}

	private void updatePauseItem(){
		long time=AccountSessionManager.get(accountID).getLocalPreferences().getNotificationsPauseEndTime();
		if(time<System.currentTimeMillis()){
			pauseItem.subtitle=getString(R.string.pause_notifications_off);
			pauseItem.checked=false;
		}else{
			pauseItem.subtitle=getString(R.string.pause_notifications_ends, UiUtils.formatRelativeTimestampAsMinutesAgo(getActivity(), Instant.ofEpochMilli(time), false));
			pauseItem.checked=true;
		}
		rebindItem(pauseItem);
		updateBanner();
	}

	private void updateBanner(){
		if(bannerAdapter==null)
			return;
		long pauseTime=AccountSessionManager.get(accountID).getLocalPreferences().getNotificationsPauseEndTime();
		if(!areNotificationsAllowed()){
			bannerAdapter.setVisible(true);
			bannerIcon.setImageResource(R.drawable.ic_app_badging_24px);
			bannerText.setText(R.string.notifications_disabled_in_system);
			bannerButton.setText(R.string.open_system_notification_settings);
			bannerButton.setOnClickListener(v->openSystemNotificationSettings());
		}else if(pauseTime>System.currentTimeMillis()){
			bannerAdapter.setVisible(true);
			bannerIcon.setImageResource(R.drawable.ic_notifications_paused_24px);
			bannerText.setText(getString(R.string.pause_notifications_banner, UiUtils.formatRelativeTimestampAsMinutesAgo(getActivity(), Instant.ofEpochMilli(pauseTime), false)));
			bannerButton.setText(R.string.resume_notifications_now);
			bannerButton.setOnClickListener(v->resumePausedNotifications());
		}else{
			bannerAdapter.setVisible(false);
		}
	}

	private void setUnifiedPushSwitchSilently(boolean checked){
		suppressUnifiedPushCallback=true;
		unifiedPushItem.checked=checked;
		rebindItem(unifiedPushItem);
		suppressUnifiedPushCallback=false;
	}

	// MAMMUT: port from upstream master
	private void onUnifiedPushClick(){
		if(!unifiedPushItem.checked){
			suppressUnifiedPushCallback=true;
			UnifiedPushHelper.unregisterAllAccounts(getContext());
			suppressUnifiedPushCallback=false;
			return;
		}
		java.util.List<String> distributors=org.unifiedpush.android.connector.UnifiedPush.getDistributors(getContext());
		if(distributors.isEmpty()){
			setUnifiedPushSwitchSilently(false);
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.mo_up_none_title)
					.setMessage(R.string.mo_up_none_body)
					.setPositiveButton(R.string.ok, null)
					.show();
			return;
		}
		// MAMMUT: always ask, even when only one distributor is installed. Without an explicit
		// choice nothing is stored, and the connector silently drops the registration request.
		String[] labels=new String[distributors.size()];
		String saved=org.unifiedpush.android.connector.UnifiedPush.getSavedDistributor(getContext());
		int preselect=0;
		for(int i=0;i<distributors.size();i++){
			labels[i]=UnifiedPushHelper.getDistributorLabel(getContext(), distributors.get(i));
			if(distributors.get(i).equals(saved))
				preselect=i;
		}
		final int[] choice={preselect};
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.mo_up_choose)
				.setSingleChoiceItems(labels, preselect, (dlg, which)->choice[0]=which)
				.setPositiveButton(R.string.ok, (dlg, which)->{
					org.unifiedpush.android.connector.UnifiedPush.saveDistributor(getContext(), distributors.get(choice[0]));
					setUnifiedPushSwitchSilently(true);
					UnifiedPushHelper.registerAllAccounts(getContext());
				})
				.setNegativeButton(R.string.cancel, (dlg, which)->setUnifiedPushSwitchSilently(false))
				.show();
	}
}
