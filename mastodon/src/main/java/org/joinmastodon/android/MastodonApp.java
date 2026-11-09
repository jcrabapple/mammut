package org.joinmastodon.android;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.webkit.WebView;

import org.joinmastodon.android.api.PushSubscriptionManager;
import org.joinmastodon.android.ui.text.MammutTypeface;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.utils.UnifiedPushHelper;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.utils.NetworkUtils;
import me.grishka.appkit.utils.V;

public class MastodonApp extends Application{

	@SuppressLint("StaticFieldLeak") // it's not a leak
	public static Context context;

	@Override
	public void onCreate(){
		super.onCreate();
		context=getApplicationContext();
		V.setApplicationContext(context);
		ImageCache.Parameters params=new ImageCache.Parameters();
		params.diskCacheSize=100*1024*1024;
		params.maxMemoryCacheSize=Integer.MAX_VALUE;
		ImageCache.setParams(params);
		NetworkUtils.setUserAgent("MastodonAndroid/"+BuildConfig.VERSION_NAME);
		UiUtils.updateLocalizedDateFormatters(context);

		// MAMMUT: port from upstream master — prefer UnifiedPush when a distributor is in use
		if(UnifiedPushHelper.isUnifiedPushEnabled(this)){
			UnifiedPushHelper.registerAllAccounts(this);
		}else{
			PushSubscriptionManager.tryRegisterFCM();
		}
		GlobalUserPreferences.load();
		// MAMMUT: FONT-SPEC §4.1 — app-wide typeface application, one factory per Activity
		registerActivityLifecycleCallbacks(new MammutTypeface.LifecycleCallbacks());
		if(BuildConfig.DEBUG){
			WebView.setWebContentsDebuggingEnabled(true);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig){
		super.onConfigurationChanged(newConfig);
		UiUtils.updateLocalizedDateFormatters(context);
	}
}
