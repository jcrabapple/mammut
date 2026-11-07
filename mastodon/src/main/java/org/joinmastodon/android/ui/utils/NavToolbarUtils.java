package org.joinmastodon.android.ui.utils;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toolbar;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.HomeFragment;
import org.joinmastodon.android.fragments.ProfileFragment;
import org.joinmastodon.android.model.Account;
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
		ImageButton avatar=new ImageButton(activity, null, 0, R.style.Widget_Mastodon_M3_Button_Icon);
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
