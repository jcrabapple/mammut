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
import org.joinmastodon.android.model.TimelineDefinition;
import org.joinmastodon.android.ui.NavDestination;
import org.joinmastodon.android.ui.NavMenuController;
import org.joinmastodon.android.ui.utils.NavToolbarUtils;
import org.joinmastodon.android.ui.utils.UnreadNotificationCounter;
import org.joinmastodon.android.ui.utils.UnreadNotificationStore;

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
	private NotificationsListFragment notificationsFragment;
	private NavMenuController navMenuController;
	private NavDestination currentDestination=NavDestination.HOME;
	private boolean backCallbackRegistered;

	private String accountID;

	private final Runnable backCallback=this::onBackPressedInternal;
	// R4 F1: one listener instance per HomeFragment, registered once per child-fragment
	// instance (creation or restoration) — a fresh lambda per view creation accumulated
	// one callback per recreation.
	private final Runnable homePageChangeListener=this::onHomePageChanged;

	@Override
	public void onCreate(Bundle savedInstanceState){
		UnreadNotificationStore.addListener(unreadListener);
		super.onCreate(savedInstanceState);
		accountID=getArguments().getString("account");
		setTitle(R.string.app_name);

		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N)
			setRetainInstance(true);

		navMenuController=new NavMenuController(this);
		navMenuController.setAccountID(accountID);

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
			notificationsFragment=new NotificationsListFragment();
			notificationsFragment.setArguments(new Bundle(args));
			homeTabFragment.addPageChangeListener(homePageChangeListener);
		}

		E.register(this);
	}

	@Override
	public void onDestroy(){
		UnreadNotificationStore.removeListener(unreadListener);
		super.onDestroy();
		E.unregister(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){
		// R4 F1: the child fragments exist here only on first launch (created in
		// onCreate). Process-death recreation restores them later, in
		// onViewStateRestored — this method must not dereference them when the plan
		// says they are not back yet.
		ChildFragmentPlan plan=ChildFragmentPlan.decide(savedInstanceState!=null, homeTabFragment!=null);
		content=new FragmentRootLinearLayout(getActivity());
		content.setOrientation(FragmentRootLinearLayout.VERTICAL);

		mainContainer=new FrameLayout(getActivity());
		content.addView(mainContainer, new FragmentRootLinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		FrameLayout fragmentContainer=new FrameLayout(getActivity());
		fragmentContainer.setId(me.grishka.appkit.R.id.fragment_wrap);
		mainContainer.addView(fragmentContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		if(plan.childrenUsable()){
			Fragment current=fragmentForDestination(currentDestination);
			// R4 F1 follow-up: HomeFragment is retained, so on a recreation (KEEP) the
			// children are still added to the child FragmentManager. Re-adding them throws
			// IllegalStateException "Fragment already added" and crashes the relaunch.
			// Only add children that are not yet added; KEEP just re-shows the current one.
			android.app.FragmentTransaction tx=getChildFragmentManager().beginTransaction();
			if(!homeTabFragment.isAdded())
				tx.add(me.grishka.appkit.R.id.fragment_wrap, homeTabFragment);
			if(!notificationsFragment.isAdded())
				tx.add(me.grishka.appkit.R.id.fragment_wrap, notificationsFragment);
			tx.hide(notificationsFragment).show(current).commit();
			maybeTriggerLoading(current);
		}

		navMenuController.attach(mainContainer);
		navMenuController.setDestination(currentDestination);
		// R4 F1: the page listener is registered when the child fragment is obtained
		// (onCreate or onViewStateRestored), not here — on recreation this method runs
		// before the restoration and homeTabFragment is still null.
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
		notificationsFragment=(NotificationsListFragment) getChildFragmentManager().getFragment(savedInstanceState, "notificationsFragment");
		// R4 F1: register the page listener on the restored instance — the deduping
		// registry keeps this to exactly one callback even if the view is recreated
		// again without re-running onCreate.
		if(homeTabFragment!=null)
			homeTabFragment.addPageChangeListener(homePageChangeListener);
		Fragment current=fragmentForDestination(currentDestination);
		getChildFragmentManager().beginTransaction()
				.hide(homeTabFragment)
				.hide(notificationsFragment)
				.show(current)
				.commit();
		maybeTriggerLoading(current);
		content.post(this::updateScrollTarget);
	}

	@Override
	public void onHiddenChanged(boolean hidden){
		super.onHiddenChanged(hidden);
		// R4 F1: the children can be absent only before restoration; never forward the
		// call into null.
		Fragment f=fragmentForDestination(currentDestination);
		if(f!=null)
			f.onHiddenChanged(hidden);
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
		// R4 F1: both children are consulted here; on recreation this can run before
		// they are restored.
		if(homeTabFragment!=null)
			homeTabFragment.onApplyWindowInsets(topOnlyInsets);
		if(notificationsFragment!=null)
			notificationsFragment.onApplyWindowInsets(topOnlyInsets);
	}

	private Fragment fragmentForDestination(NavDestination destination){
		// HOME, LOCAL and FEDERATED are pages of the home pager — one surface, one
		// current timeline. Only notifications is a separate screen.
		return destination==NavDestination.NOTIFICATIONS ? notificationsFragment : homeTabFragment;
	}

	private static TimelineDefinition.TimelineType timelineTypeFor(NavDestination destination){
		return switch(destination){
			case LOCAL -> TimelineDefinition.TimelineType.LOCAL;
			case FEDERATED -> TimelineDefinition.TimelineType.FEDERATED;
			default -> TimelineDefinition.TimelineType.HOME;
		};
	}

	private static NavDestination destinationFor(TimelineDefinition.TimelineType type){
		return switch(type){
			case LOCAL -> NavDestination.LOCAL;
			case FEDERATED -> NavDestination.FEDERATED;
			default -> NavDestination.HOME;
		};
	}

	/** MAMMUT: a swipe changed the visible timeline, so the menu must follow it. */
	private void onHomePageChanged(){
		updateScrollTarget();
		if(navMenuController==null || homeTabFragment==null || currentDestination==NavDestination.NOTIFICATIONS)
			return;
		NavDestination now=destinationFor(homeTabFragment.getCurrentTimelineType());
		if(now!=currentDestination){
			currentDestination=now;
			navMenuController.setDestination(now);
			updateBackCallback();
		}
	}

	/**
	 * Switches the visible root destination. Used by the navigation FAB menu and by
	 * MainActivity/AccountSwitcherSheet entry points.
	 */
	public void setDestination(NavDestination destination){
		if(destination==currentDestination){
			navMenuController.closeMenu();
			Fragment current=destination==NavDestination.NOTIFICATIONS
					? notificationsFragment
					: homeTabFragment.getCurrentFragment();
			if(current instanceof ScrollableToTop scrollable)
				scrollable.scrollToTop();
			return;
		}
		navMenuController.closeMenu();
		Fragment current=fragmentForDestination(currentDestination);
		Fragment newFragment=fragmentForDestination(destination);
		if(destination==NavDestination.NOTIFICATIONS){
			getChildFragmentManager().beginTransaction().hide(current).show(newFragment).commit();
		}else{
			// Show the pager, then select the page this destination names. An account
			// without that timeline falls back to Home rather than to a blank screen.
			if(current!=newFragment)
				getChildFragmentManager().beginTransaction().hide(current).show(newFragment).commit();
			if(!homeTabFragment.selectTimelineType(timelineTypeFor(destination))){
				if(!homeTabFragment.selectTimelineType(TimelineDefinition.TimelineType.HOME))
					return;
				destination=NavDestination.HOME;
			}
		}
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
		if(navMenuController==null || !isAdded() || homeTabFragment==null || notificationsFragment==null)
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
		// R4 F1: on recreation the children may not have been restored yet when the
		// save runs — only persist what actually exists.
		if(homeTabFragment!=null && homeTabFragment.isAdded()) getChildFragmentManager().putFragment(outState, "homeTabFragment", homeTabFragment);
		if(notificationsFragment!=null && notificationsFragment.isAdded()) getChildFragmentManager().putFragment(outState, "notificationsFragment", notificationsFragment);
	}

	@Override
	protected void onShown(){
		super.onShown();
		reloadNotificationsForUnreadCount();
		navMenuController.showFab();
		updateScrollTarget();
	}

	private void reloadNotificationsForUnreadCount(){
		UnreadNotificationStore.refresh(accountID);
	}

	// MAMMUT: one place feeds the FAB badge and the top-bar chip, so both stay live
	private final UnreadNotificationStore.Listener unreadListener=(count, more)->{
		if(navMenuController!=null)
			navMenuController.setUnreadCount(count, more);
	};

	@Subscribe
	public void onNotificationsMarkerUpdated(NotificationsMarkerUpdatedEvent ev){
		if(!ev.accountID.equals(accountID))
			return;
		if(ev.clearUnread)
			UnreadNotificationStore.markRead();
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

	/**
	 * R4 F1 — pure plan for the child-fragment lifecycle across recreation.
	 *
	 * Process death recreates HomeFragment with a saved state but without its children:
	 * the children are re-obtained from the child FragmentManager, which has not run by
	 * onCreateView time. The pre-fix fragment restored them only in onViewStateRestored
	 * but dereferenced them in onCreateView (recreation NPE on Home/Local/Notifications),
	 * and registered a fresh page-change listener on every view creation, so N
	 * recreations fired N callbacks for one swipe. The plan makes the decisions
	 * explicit: children are created only on first launch, restored whenever a saved
	 * state exists and they are absent, and they must not be touched while the plan
	 * says they are not back yet.
	 */
	static final class ChildFragmentPlan{
		enum Action{CREATE, RESTORE, KEEP}

		final Action action;

		private ChildFragmentPlan(Action action){
			this.action=action;
		}

		static ChildFragmentPlan decide(boolean hasSavedState, boolean childrenPresent){
			if(childrenPresent)
				return new ChildFragmentPlan(Action.KEEP);
			return new ChildFragmentPlan(hasSavedState ? Action.RESTORE : Action.CREATE);
		}

		/**
		 * Children may be dereferenced only once they exist: CREATE makes them, KEEP
		 * retained them, RESTORE must wait for onViewStateRestored.
		 */
		boolean childrenUsable(){
			return action!=Action.RESTORE;
		}
	}
}
