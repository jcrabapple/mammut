package org.joinmastodon.android.fragments;

import android.app.Fragment;
import android.app.NotificationManager;
import android.app.assist.AssistContent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.E;
import org.joinmastodon.android.PushNotificationReceiver;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.NotificationsMarkerUpdatedEvent;
import org.joinmastodon.android.events.StatusDisplaySettingsChangedEvent;
import org.joinmastodon.android.fragments.discover.FederatedTimelineFragment;
import org.joinmastodon.android.fragments.discover.LocalTimelineFragment;
import org.joinmastodon.android.ui.NavDestination;
import org.joinmastodon.android.ui.NavMenuController;
import org.joinmastodon.android.ui.utils.NavToolbarUtils;
import org.joinmastodon.android.ui.utils.UnreadNotificationCounter;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.FragmentStackActivity;
import me.grishka.appkit.Nav;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.fragments.LoaderFragment;
import me.grishka.appkit.views.FragmentRootLinearLayout;

public class HomeFragment extends AppKitFragment implements AssistContentProviderFragment, HasAccountID, NavMenuController.Host{
	private FragmentRootLinearLayout content;
	private FrameLayout mainContainer;
	private HomeTabFragment homeTabFragment;
	private LocalTimelineFragment localTimelineFragment;
	private FederatedTimelineFragment federatedTimelineFragment;
	private NotificationsListFragment notificationsFragment;
	private NavMenuController navMenuController;
	private NavDestination currentDestination=NavDestination.HOME;
	private boolean backCallbackRegistered;

	private String accountID;

	private final Runnable backCallback=this::onBackPressedInternal;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		accountID=getArguments().getString("account");
		setTitle(R.string.app_name);

		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N)
			setRetainInstance(true);

		navMenuController=new NavMenuController(this);

		if(savedInstanceState!=null && savedInstanceState.containsKey("selectedDestination")){
			currentDestination=NavDestination.valueOf(savedInstanceState.getString("selectedDestination"));
		}else if("notifications".equals(getArguments().getString("tab"))){
			// MainActivity notification-tap entry point
			currentDestination=NavDestination.NOTIFICATIONS;
		}

		if(savedInstanceState==null){
			Bundle args=new Bundle();
			args.putString("account", accountID);
			homeTabFragment=new HomeTabFragment();
			homeTabFragment.setArguments(args);
			args=new Bundle(args);
			args.putBoolean("noAutoLoad", true);
			localTimelineFragment=new LocalTimelineFragment();
			localTimelineFragment.setArguments(new Bundle(args));
			federatedTimelineFragment=new FederatedTimelineFragment();
			federatedTimelineFragment.setArguments(new Bundle(args));
			notificationsFragment=new NotificationsListFragment();
			notificationsFragment.setArguments(new Bundle(args));
		}

		E.register(this);
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){
		content=new FragmentRootLinearLayout(getActivity());
		content.setOrientation(FragmentRootLinearLayout.VERTICAL);

		mainContainer=new FrameLayout(getActivity());
		content.addView(mainContainer, new FragmentRootLinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		FrameLayout fragmentContainer=new FrameLayout(getActivity());
		fragmentContainer.setId(me.grishka.appkit.R.id.fragment_wrap);
		mainContainer.addView(fragmentContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		if(savedInstanceState==null){
			Fragment current=fragmentForDestination(currentDestination);
			getChildFragmentManager().beginTransaction()
					.add(me.grishka.appkit.R.id.fragment_wrap, homeTabFragment)
					.add(me.grishka.appkit.R.id.fragment_wrap, localTimelineFragment).hide(localTimelineFragment)
					.add(me.grishka.appkit.R.id.fragment_wrap, federatedTimelineFragment).hide(federatedTimelineFragment)
					.add(me.grishka.appkit.R.id.fragment_wrap, notificationsFragment).hide(notificationsFragment)
					.show(current)
					.commit();
			maybeTriggerLoading(current);
		}

		navMenuController.attach(mainContainer);
		navMenuController.setDestination(currentDestination);
		// Re-point the scroll rule whenever the home pager flips to another timeline
		homeTabFragment.addPageChangeListener(this::updateScrollTarget);
		content.post(this::updateScrollTarget);
		updateBackCallback();

		return content;
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState){
		super.onViewStateRestored(savedInstanceState);

		// MOSHIDON: we must restore the child fragments
		if(savedInstanceState==null)
			return;
		homeTabFragment=(HomeTabFragment) getChildFragmentManager().getFragment(savedInstanceState, "homeTabFragment");
		homeTabFragment.addPageChangeListener(this::updateScrollTarget);
		localTimelineFragment=(LocalTimelineFragment) getChildFragmentManager().getFragment(savedInstanceState, "localTimelineFragment");
		federatedTimelineFragment=(FederatedTimelineFragment) getChildFragmentManager().getFragment(savedInstanceState, "federatedTimelineFragment");
		notificationsFragment=(NotificationsListFragment) getChildFragmentManager().getFragment(savedInstanceState, "notificationsFragment");
		Fragment current=fragmentForDestination(currentDestination);
		getChildFragmentManager().beginTransaction()
				.hide(homeTabFragment)
				.hide(localTimelineFragment)
				.hide(federatedTimelineFragment)
				.hide(notificationsFragment)
				.show(current)
				.commit();
		maybeTriggerLoading(current);
		content.post(this::updateScrollTarget);
	}

	@Override
	public void onHiddenChanged(boolean hidden){
		super.onHiddenChanged(hidden);
		fragmentForDestination(currentDestination).onHiddenChanged(hidden);
	}

	@Override
	public boolean wantsLightStatusBar(){
		return !org.joinmastodon.android.ui.utils.UiUtils.isDarkTheme();
	}

	@Override
	public boolean wantsLightNavigationBar(){
		return !org.joinmastodon.android.ui.utils.UiUtils.isDarkTheme();
	}

	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		if(Build.VERSION.SDK_INT>=27){
			// MAMMUT: the tab bar is gone; the timelines extend to the bottom inset and
			// the navigation FAB floats 16dp above it
			navMenuController.setBottomInset(insets.getSystemWindowInsetBottom());
			super.onApplyWindowInsets(insets.replaceSystemWindowInsets(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), 0));
		}else{
			super.onApplyWindowInsets(insets.replaceSystemWindowInsets(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()));
		}
		WindowInsets topOnlyInsets=insets.replaceSystemWindowInsets(0, insets.getSystemWindowInsetTop(), 0, 0);
		homeTabFragment.onApplyWindowInsets(topOnlyInsets);
		localTimelineFragment.onApplyWindowInsets(topOnlyInsets);
		federatedTimelineFragment.onApplyWindowInsets(topOnlyInsets);
		notificationsFragment.onApplyWindowInsets(topOnlyInsets);
	}

	private Fragment fragmentForDestination(NavDestination destination){
		return switch(destination){
			case HOME -> homeTabFragment;
			case LOCAL -> localTimelineFragment;
			case FEDERATED -> federatedTimelineFragment;
			case NOTIFICATIONS -> notificationsFragment;
		};
	}

	/**
	 * Switches the visible root destination. Used by the navigation FAB menu and by
	 * MainActivity/AccountSwitcherSheet entry points.
	 */
	public void setDestination(NavDestination destination){
		if(destination==currentDestination){
			navMenuController.closeMenu();
			Fragment current=fragmentForDestination(currentDestination);
			if(current instanceof ScrollableToTop scrollable)
				scrollable.scrollToTop();
			return;
		}
		navMenuController.closeMenu();
		Fragment current=fragmentForDestination(currentDestination);
		Fragment newFragment=fragmentForDestination(destination);
		getChildFragmentManager().beginTransaction().hide(current).show(newFragment).commit();
		maybeTriggerLoading(newFragment);
		currentDestination=destination;
		navMenuController.setDestination(destination);
		navMenuController.showFab();
		updateScrollTarget();
		updateBackCallback();
		((FragmentStackActivity)getActivity()).invalidateSystemBarColors(this);
	}

	/**
	 * Opens the signed-in user's profile, the entry point that replaced the old
	 * profile tab (the avatar in the top app bar and the account switcher sheet).
	 */
	public void openSelfProfile(){
		NavToolbarUtils.openSelfProfile(getActivity(), accountID);
	}

	private void maybeTriggerLoading(Fragment newFragment){
		if(newFragment instanceof LoaderFragment lf){
			if(!lf.loaded && !lf.dataLoading)
				lf.loadData();
		}
		if(newFragment instanceof NotificationsListFragment){
			NotificationManager nm=getActivity().getSystemService(NotificationManager.class);
			nm.cancel(accountID, PushNotificationReceiver.NOTIFICATION_ID);
		}
	}

	private void updateScrollTarget(){
		if(navMenuController==null || !isAdded())
			return;
		Fragment f=fragmentForDestination(currentDestination);
		if(f==homeTabFragment)
			f=homeTabFragment.getCurrentFragment();
		RecyclerView target=null;
		if(f instanceof MastodonRecyclerFragment<?> mrf)
			target=mrf.getList();
		navMenuController.setScrollTarget(target);
	}

	private void onBackPressedInternal(){
		if(navMenuController.isMenuOpen()){
			navMenuController.closeMenu();
		}else if(currentDestination!=NavDestination.HOME){
			setDestination(NavDestination.HOME);
		}
		updateBackCallback();
	}

	private void updateBackCallback(){
		boolean active=navMenuController!=null && (navMenuController.isMenuOpen() || currentDestination!=NavDestination.HOME);
		if(active && !backCallbackRegistered){
			addBackCallback(backCallback);
			backCallbackRegistered=true;
		}else if(!active && backCallbackRegistered){
			removeBackCallback(backCallback);
			backCallbackRegistered=false;
		}
	}

	@Override
	public void onNavDestinationSelected(NavDestination destination){
		setDestination(destination);
	}

	@Override
	public void onNavComposeSelected(){
		navMenuController.closeMenu();
		Bundle args=new Bundle();
		args.putString("account", accountID);
		Nav.go(getActivity(), ComposeFragment.class, args);
	}

	@Override
	public void onNavMenuOpenChanged(boolean open){
		updateBackCallback();
	}

	@Override
	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putString("selectedDestination", currentDestination.name());

		// MOSHIDON: we use the isAdded because of user themes
		if(homeTabFragment.isAdded()) getChildFragmentManager().putFragment(outState, "homeTabFragment", homeTabFragment);
		if(localTimelineFragment.isAdded()) getChildFragmentManager().putFragment(outState, "localTimelineFragment", localTimelineFragment);
		if(federatedTimelineFragment.isAdded()) getChildFragmentManager().putFragment(outState, "federatedTimelineFragment", federatedTimelineFragment);
		if(notificationsFragment.isAdded()) getChildFragmentManager().putFragment(outState, "notificationsFragment", notificationsFragment);
	}

	@Override
	protected void onShown(){
		super.onShown();
		reloadNotificationsForUnreadCount();
		navMenuController.showFab();
		updateScrollTarget();
	}

	private void reloadNotificationsForUnreadCount(){
		UnreadNotificationCounter.load(accountID, (count, more)->navMenuController.setUnreadCount(count, more));
	}

	@Subscribe
	public void onNotificationsMarkerUpdated(NotificationsMarkerUpdatedEvent ev){
		if(!ev.accountID.equals(accountID))
			return;
		if(ev.clearUnread)
			navMenuController.setUnreadCount(0, false);
	}

	@Subscribe
	public void onStatusDisplaySettingsChanged(StatusDisplaySettingsChangedEvent ev){
		if(!ev.accountID.equals(accountID))
			return;
		if(notificationsFragment.loaded)
			notificationsFragment.rebuildAllDisplayItems();
	}

	@Override
	public String getAccountID(){
		return accountID;
	}

	@Override
	public void onProvideAssistContent(AssistContent content){
		if(fragmentForDestination(currentDestination) instanceof AssistContentProviderFragment provider){
			provider.onProvideAssistContent(content);
		}
	}
}
