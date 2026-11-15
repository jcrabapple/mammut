package org.joinmastodon.android.ui.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MAMMUT: single in-process source of truth for the unread-notification count.
 *
 * Before this existed, each surface asked the server for the count once and rendered it,
 * so the FAB badge and the menu row went stale the moment a notification arrived. Every
 * consumer now subscribes here, and the count is republished when:
 *
 *  - a screen calls {@link #refresh(String)} (API/marker fetch),
 *  - a push arrives while the app is in memory ({@link #onPushReceived(String)}),
 *  - the user reads the notifications ({@link #markRead(String)}).
 *
 * MAMMUT: audit R3 F3 — state is keyed by account and every load carries a generation:
 * a push or a newer refresh invalidates the in-flight one, so a slow response for account
 * A can never overwrite account B's badge, and a push that arrives mid-refresh is not
 * erased by the older server snapshot.
 *
 * Listeners are always invoked on the main thread.
 */
public class UnreadNotificationStore{
	public interface Listener{
		void onUnreadCountChanged(int count, boolean more);
	}

	private static final Map<String, List<Listener>> accountListeners=new HashMap<>();
	private static final List<Listener> activeAccountListeners=new CopyOnWriteArrayList<>();
	private static final Handler mainHandler=new Handler(Looper.getMainLooper());
	private static final Tracker tracker=new Tracker();
	private static volatile String activeAccountID;

	private UnreadNotificationStore(){}

	/**
	 * Legacy registration (HomeFragment): the listener follows whichever account was
	 * refreshed most recently — the activity restarts on account switch, so there is only
	 * one active Home surface at a time.
	 */
	public static void addListener(Listener listener){
		activeAccountListeners.add(listener);
		Tracker.State snapshot=activeAccountID==null ? null : tracker.snapshot(activeAccountID);
		if(snapshot!=null && snapshot.known)
			listener.onUnreadCountChanged(snapshot.count, snapshot.more);
	}

	/** Account-scoped registration: the listener only ever sees this account's counts. */
	public static void addListener(Listener listener, String accountID){
		synchronized(accountListeners){
			accountListeners.computeIfAbsent(accountID, k->new CopyOnWriteArrayList<>()).add(listener);
		}
		Tracker.State snapshot=tracker.snapshot(accountID);
		if(snapshot.known)
			listener.onUnreadCountChanged(snapshot.count, snapshot.more);
	}

	public static void removeListener(Listener listener){
		activeAccountListeners.remove(listener);
		synchronized(accountListeners){
			for(List<Listener> l:accountListeners.values())
				l.remove(listener);
		}
	}

	public static void refresh(String accountID){
		activeAccountID=accountID;
		long generation=tracker.beginEpoch(accountID);
		UnreadNotificationCounter.load(accountID, (count, more)->{
			if(tracker.complete(accountID, generation, count, more))
				dispatch(accountID);
		});
	}

	/** Optimistic increment so a push is visible without waiting for a round trip. */
	public static void onPushReceived(String accountID){
		tracker.push(accountID);
		dispatch(accountID);
	}

	public static void onPushReceived(){
		String account=activeAccountID;
		if(account!=null)
			onPushReceived(account);
	}

	public static void markRead(String accountID){
		tracker.markRead(accountID);
		dispatch(accountID);
	}

	public static void markRead(){
		String account=activeAccountID;
		if(account!=null)
			markRead(account);
	}

	/** Authoritative publish (server count); supersedes any in-flight refresh. */
	public static void publish(String accountID, int newCount, boolean newMore){
		long generation=tracker.beginEpoch(accountID);
		if(tracker.complete(accountID, generation, newCount, newMore))
			dispatch(accountID);
	}

	public static void publish(int newCount, boolean newMore){
		String account=activeAccountID;
		if(account!=null)
			publish(account, newCount, newMore);
	}

	private static void dispatch(String accountID){
		Tracker.State snapshot=tracker.snapshot(accountID);
		List<Listener> scoped;
		synchronized(accountListeners){
			scoped=accountListeners.containsKey(accountID) ? new ArrayList<>(accountListeners.get(accountID)) : null;
		}
		boolean active=accountID.equals(activeAccountID);
		if(scoped==null && !active)
			return;
		runnableOnMain(()->{
			if(scoped!=null)
				for(Listener l:scoped)
					l.onUnreadCountChanged(snapshot.count, snapshot.more);
			if(active)
				for(Listener l:activeAccountListeners)
					l.onUnreadCountChanged(snapshot.count, snapshot.more);
		});
	}

	private static void runnableOnMain(Runnable r){
		if(Looper.myLooper()==Looper.getMainLooper())
			r.run();
		else
			mainHandler.post(r);
	}

	/**
	 * MAMMUT: audit R3 F3 — per-account unread state with a generation counter. Pure
	 * (no Android types) so cross-account interleavings are unit testable; the store
	 * above owns threading and listener fan-out.
	 */
	public static class Tracker{
		public static class State{
			public long generation;
			public int count;
			public boolean more;
			public boolean known;
		}

		private final HashMap<String, State> states=new HashMap<>();

		public synchronized long beginEpoch(String accountID){
			State s=state(accountID);
			s.generation++;
			return s.generation;
		}

		/**
		 * Applies a load result only if its epoch is still current; a push, a mark-read,
		 * or a newer refresh that arrived while the request was in flight makes this stale.
		 */
		public synchronized boolean complete(String accountID, long generation, int count, boolean more){
			State s=state(accountID);
			if(s.generation!=generation)
				return false;
			s.count=Math.max(0, count);
			s.more=more;
			s.known=true;
			return true;
		}

		/** Optimistic increment; invalidates an in-flight refresh so it cannot clobber this. */
		public synchronized int push(String accountID){
			State s=state(accountID);
			s.generation++;
			s.count=Math.max(0, s.count+1);
			s.known=true;
			return s.count;
		}

		public synchronized void markRead(String accountID){
			State s=state(accountID);
			s.generation++;
			s.count=0;
			s.more=false;
			s.known=true;
		}

		public synchronized State snapshot(String accountID){
			return new StateSnapshot(state(accountID));
		}

		private State state(String accountID){
			return states.computeIfAbsent(accountID, k->new State());
		}

		private static class StateSnapshot extends State{
			StateSnapshot(State s){
				generation=s.generation;
				count=s.count;
				more=s.more;
				known=s.known;
			}
		}
	}
}
