package org.joinmastodon.android.ui.utils;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toolbar;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.HomeFragment;
import org.joinmastodon.android.fragments.ProfileFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.ui.NavDestination;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.sheets.AccountSwitcherSheet;

import me.grishka.appkit.Nav;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

/**
 * MAMMUT: helpers for the destination toolbars of the FAB navigation. The avatar
 * sits at the start of the top app bar (tap: own profile; long-press: account
 * switcher) and the search entry point lives at its end, per the approved design.
 */
public class NavToolbarUtils{
	private NavToolbarUtils(){}

	/**
	 * Adds the self-profile avatar as a leading control of the given toolbar.
	 * Call only for fragments hosted as root destinations (i.e. children of
	 * HomeFragment), never for pushed fragments that need the back arrow.
	 */
	public static void addAvatarButton(Activity activity, Toolbar toolbar, Fragment hostFragment, String accountID){
		if(toolbar.findViewById(R.id.nav_toolbar_avatar)!=null)
			return;
		// MAMMUT: no M3 Icon style here — it paints a persistent tonal tile behind the avatar,
		// which reads as a stuck ripple and does not match the borderless controls beside it
		ImageButton avatar=new ImageButton(activity);
		avatar.setBackground(activity.getDrawable(R.drawable.bg_round_ripple));
		avatar.setId(R.id.nav_toolbar_avatar);
		int size=V.dp(40);
		Toolbar.LayoutParams lp=new Toolbar.LayoutParams(size, size, Gravity.START|Gravity.CENTER_VERTICAL);
		lp.setMargins(V.dp(4), 0, 0, 0);
		avatar.setLayoutParams(lp);
		avatar.setContentDescription(activity.getString(R.string.my_profile));
		avatar.setOutlineProvider(OutlineProviders.OVAL);
		avatar.setClipToOutline(true);
		toolbar.addView(avatar, 0);
		Account self=AccountSessionManager.getInstance().getAccount(accountID).self;
		ViewImageLoader.loadWithoutAnimation(avatar, null, new UrlImageLoaderRequest(self.avatar, V.dp(40), V.dp(40)));
		avatar.setOnClickListener(v->openSelfProfile(activity, accountID));
		avatar.setOnLongClickListener(v->{
			if(hostFragment instanceof HomeFragment hf){
				new AccountSwitcherSheet(activity, hf).show();
				return true;
			}
			return false;
		});
	}

	/**
	 * Adds the persistent unread-notification chip to the end of a root destination's toolbar.
	 * It exists because the bottom tab bar used to carry that count: with the bar gone, the FAB
	 * badge is the only place it survives, and the FAB hides on scroll.
	 */
	public static void addUnreadChip(Activity activity, Toolbar toolbar, Fragment host, String accountID){
		if(toolbar.findViewById(R.id.nav_unread_chip)!=null)
			return;
		View chip=LayoutInflater.from(activity).inflate(R.layout.nav_unread_chip, toolbar, false);
		Toolbar.LayoutParams lp=new Toolbar.LayoutParams(V.dp(48), V.dp(48), Gravity.END|Gravity.CENTER_VERTICAL);
		chip.setLayoutParams(lp);
		toolbar.addView(chip);
		bindUnreadChip(chip, host, accountID);
	}

	/**
	 * Wires an already-inflated chip (the home toolbar carries one in its own layout) to the
	 * shared unread store and to the Notifications destination.
	 */
	public static void bindUnreadChip(View chip, Fragment host, String accountID){
		TextView badge=chip.findViewById(R.id.nav_unread_badge);
		ImageButton button=chip.findViewById(R.id.nav_unread_button);
		if(badge==null || button==null)
			return;

		Activity activity=(Activity) chip.getContext();
		UnreadNotificationStore.Listener listener=(count, more)->{
			if(count<=0){
				badge.setVisibility(View.GONE);
				button.setContentDescription(activity.getString(R.string.nav_destination_notifications));
			}else{
				boolean capped=more || count>99;
				badge.setText(capped ? (Math.min(count, 99)+"+") : String.valueOf(count));
				badge.setVisibility(View.VISIBLE);
				button.setContentDescription(activity.getResources().getQuantityString(R.plurals.nav_cd_notifications_unread, count, count));
			}
		};

		final boolean[] registered={false};
		Runnable register=()->{
			if(!registered[0]){
				registered[0]=true;
				UnreadNotificationStore.addListener(listener);
			}
		};
		Runnable unregister=()->{
			if(registered[0]){
				registered[0]=false;
				UnreadNotificationStore.removeListener(listener);
			}
		};
		chip.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
			@Override public void onViewAttachedToWindow(View v){ register.run(); }
			@Override public void onViewDetachedFromWindow(View v){ unregister.run(); }
		});
		if(chip.isAttachedToWindow())
			register.run();

		button.setOnClickListener(v->{
			HomeFragment home=host instanceof HomeFragment hf ? hf
					: host.getParentFragment() instanceof HomeFragment hf2 ? hf2 : null;
			if(home!=null)
				home.setDestination(NavDestination.NOTIFICATIONS);
		});
		button.setOnLongClickListener(v->{
			markAllNotificationsRead(activity, accountID);
			return true;
		});
	}

	/**
	 * Long-press on the bell: write the notifications marker at the newest
	 * notification, then clear the shared unread store so every badge and chip
	 * drops at once. Reading the marker back is not enough on its own — the
	 * count is computed from it, so the store is only cleared once the server
	 * has accepted the new position.
	 */
	private static void markAllNotificationsRead(Activity activity, String accountID){
		new org.joinmastodon.android.api.requests.notifications.GetNotificationsV1(null, 1, java.util.EnumSet.allOf(org.joinmastodon.android.model.NotificationType.class))
				.setCallback(new me.grishka.appkit.api.Callback<java.util.List<org.joinmastodon.android.model.Notification>>(){
					@Override
					public void onSuccess(java.util.List<org.joinmastodon.android.model.Notification> result){
						if(result.isEmpty()){
							UnreadNotificationStore.markRead();
							android.widget.Toast.makeText(activity, R.string.nav_notifications_marked_read, android.widget.Toast.LENGTH_SHORT).show();
							return;
						}
						new org.joinmastodon.android.api.requests.markers.SaveMarkers(null, result.get(0).id)
								.setCallback(new me.grishka.appkit.api.Callback<org.joinmastodon.android.model.TimelineMarkers>(){
									@Override
									public void onSuccess(org.joinmastodon.android.model.TimelineMarkers r){
										UnreadNotificationStore.markRead();
										android.widget.Toast.makeText(activity, R.string.nav_notifications_marked_read, android.widget.Toast.LENGTH_SHORT).show();
									}

									@Override
									public void onError(me.grishka.appkit.api.ErrorResponse error){
										android.widget.Toast.makeText(activity, R.string.nav_notifications_mark_read_failed, android.widget.Toast.LENGTH_SHORT).show();
									}
								})
								.exec(accountID);
					}

					@Override
					public void onError(me.grishka.appkit.api.ErrorResponse error){
						android.widget.Toast.makeText(activity, R.string.nav_notifications_mark_read_failed, android.widget.Toast.LENGTH_SHORT).show();
					}
				})
				.exec(accountID);
	}

	public static void openSelfProfile(Activity activity, String accountID){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		args.putParcelable("profileAccount", org.parceler.Parcels.wrap(AccountSessionManager.getInstance().getAccount(accountID).self));
		Nav.go(activity, ProfileFragment.class, args);
	}

	/**
	 * The search entry point: opens Discover the same way the old search tab did.
	 */
	public static void openDiscover(Activity activity, String accountID){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		Nav.go(activity, org.joinmastodon.android.fragments.discover.DiscoverFragment.class, args);
	}
}
