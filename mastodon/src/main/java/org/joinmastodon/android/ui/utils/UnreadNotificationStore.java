package org.joinmastodon.android.ui.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MAMMUT: single in-process source of truth for the unread-notification count.
 *
 * Before this existed, each surface asked the server for the count once and rendered it,
 * so the FAB badge and the menu row went stale the moment a notification arrived. Every
 * consumer now subscribes here, and the count is republished when:
 *
 *  - a screen calls {@link #refresh(String)} (API/marker fetch),
 *  - a push arrives while the app is in memory ({@link #onPushReceived()}),
 *  - the user reads the notifications ({@link #markRead()}).
 *
 * Listeners are always invoked on the main thread.
 */
public class UnreadNotificationStore{
	public interface Listener{
		void onUnreadCountChanged(int count, boolean more);
	}

	private static final List<Listener> listeners=new CopyOnWriteArrayList<>();
	private static final Handler mainHandler=new Handler(Looper.getMainLooper());
	private static int count;
	private static boolean more;
	private static boolean known;

	private UnreadNotificationStore(){}

	public static void addListener(Listener listener){
		listeners.add(listener);
		if(known)
			listener.onUnreadCountChanged(count, more);
	}

	public static void removeListener(Listener listener){
		listeners.remove(listener);
	}

	public static void refresh(String accountID){
		UnreadNotificationCounter.load(accountID, UnreadNotificationStore::publish);
	}

	/** Optimistic increment so a push is visible without waiting for a round trip. */
	public static void onPushReceived(){
		publish(count+1, more);
	}

	public static void markRead(){
		publish(0, false);
	}

	public static void publish(int newCount, boolean newMore){
		runnableOnMain(()->{
			count=Math.max(0, newCount);
			more=newMore;
			known=true;
			for(Listener l:listeners)
				l.onUnreadCountChanged(count, more);
		});
	}

	private static void runnableOnMain(Runnable r){
		if(Looper.myLooper()==Looper.getMainLooper())
			r.run();
		else
			mainHandler.post(r);
	}
}
