package org.joinmastodon.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.joinmastodon.android.model.Notification;
import org.parceler.Parcels;

import androidx.annotation.Nullable;

/**
 * MAMMUT: Wave 2 S-M6 — unexported trampoline for notification shade taps.
 * Only Mammut's own PendingIntents can start this Activity, so Parcelable Notification
 * payloads are verified here and handed off to exported {@link MainActivity} via an
 * in-process one-shot token rather than trusted from external Intent extras.
 */
public class NotificationTapActivity extends Activity{
	private static final String TAG="NotificationTapActivity";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		Intent src=getIntent();
		Intent dst=new Intent(this, MainActivity.class);
		dst.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		if(src!=null && src.getBooleanExtra("fromNotification", false)){
			String accountID=src.getStringExtra("accountID");
			Notification notification=null;
			if(src.hasExtra("notification")){
				try{
					notification=Parcels.unwrap(src.getParcelableExtra("notification"));
				}catch(RuntimeException x){
					Log.w(TAG, "Ignoring unreadable notification parcel", x);
				}
			}
			String token=MainActivity.NotificationHandoff.put(accountID, notification);
			dst.putExtra(MainActivity.EXTRA_NOTIFICATION_HANDOFF, token);
		}
		startActivity(dst);
		finish();
	}
}
