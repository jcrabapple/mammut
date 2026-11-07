package org.joinmastodon.android.ui;

import android.content.Context;

import org.joinmastodon.android.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * MAMMUT: the four root destinations reachable from the navigation FAB.
 */
public enum NavDestination{
	HOME,
	LOCAL,
	FEDERATED,
	NOTIFICATIONS;

	@StringRes
	public int getTitleRes(){
		return switch(this){
			case HOME -> R.string.nav_destination_home;
			case LOCAL -> R.string.nav_destination_local;
			case FEDERATED -> R.string.nav_destination_federated;
			case NOTIFICATIONS -> R.string.notifications;
		};
	}

	@StringRes
	public int getSubtitleRes(){
		return switch(this){
			case HOME -> R.string.nav_subtitle_home;
			case LOCAL -> R.string.nav_subtitle_local;
			case FEDERATED -> R.string.nav_subtitle_federated;
			case NOTIFICATIONS -> R.string.nav_subtitle_notifications;
		};
	}

	@DrawableRes
	public int getIconRes(){
		return switch(this){
			case HOME -> R.drawable.ic_nav_home_24;
			case LOCAL -> R.drawable.ic_nav_local_24;
			case FEDERATED -> R.drawable.ic_nav_federated_24;
			case NOTIFICATIONS -> R.drawable.ic_nav_notifications_24;
		};
	}

	@StringRes
	public int getContentDescriptionRes(){
		return switch(this){
			case HOME -> R.string.nav_cd_home_timeline;
			case LOCAL -> R.string.nav_cd_local_timeline;
			case FEDERATED -> R.string.nav_cd_federated_timeline;
			case NOTIFICATIONS -> R.string.notifications;
		};
	}

	public String getTitle(Context ctx){
		return ctx.getString(getTitleRes());
	}
}
