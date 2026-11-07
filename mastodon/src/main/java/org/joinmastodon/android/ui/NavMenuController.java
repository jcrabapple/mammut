package org.joinmastodon.android.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.joinmastodon.android.ui.views.NavMenuView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.utils.V;

/**
 * MAMMUT: glues the navigation FAB (NavMenuView) into a host fragment: mounting,
 * the spec's scroll hide/show rule, content bottom padding and window insets.
 * The host fragment owns destination behaviour, back dispatch and the unread
 * count source; this class owns everything visual/positional.
 */
public class NavMenuController implements NavMenuView.OnMenuActionListener{
	public interface Host{
		void onNavDestinationSelected(NavDestination destination);
		void onNavComposeSelected();
		void onNavMenuOpenChanged(boolean open);
	}

	private Host host;
	private NavMenuView view;
	private RecyclerView scrollTarget;
	private RecyclerView.OnScrollListener scrollListener;
	private int bottomInset;
	private RecyclerView paddedTarget;
	private int savedPaddingBottom=-1;
	private String accountID;

	public NavMenuController(Host host){
		this.host=host;
	}

	public void setAccountID(String accountID){
		this.accountID=accountID;
		if(view!=null)
			view.setAccountID(accountID);
	}

	/**
	 * Adds the navigation overlay to the given container (must be a FrameLayout-like
	 * parent where match_parent children overlay each other).
	 */
	public NavMenuView attach(ViewGroup overlayContainer){
		view=new NavMenuView(overlayContainer.getContext());
		view.setListener(this);
		view.setAccountID(accountID);
		overlayContainer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		// FAB bottom margin: 16dp above the navigation-bar inset (the previous tab bar
		// enforced a 24dp minimum when any inset was present; keep that so the FAB
		// clears the gesture pill)
		view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
			@Override
			public boolean onPreDraw(){
				applyFabMargin();
				return true;
			}
		});
		applyFabMargin();
		return view;
	}

	public void detach(ViewGroup overlayContainer){
		setScrollTarget(null);
		overlayContainer.removeView(view);
		view=null;
	}

	public boolean isMenuOpen(){
		return view!=null && view.isMenuOpen();
	}

	public void closeMenu(){
		if(view!=null)
			view.closeMenu();
	}

	public void showFab(){
		if(view!=null)
			view.showFab();
	}

	public void setDestination(NavDestination destination){
		if(view!=null)
			view.setDestination(destination);
	}

	public void setUnreadCount(int count, boolean more){
		if(view!=null)
			view.setUnreadCount(count, more);
	}

	/**
	 * Points the scroll rule at the currently visible list. Re-called by the host
	 * whenever the visible destination (or pager page) changes. Passing null disables
	 * the rule until a target is set again.
	 */
	public void setScrollTarget(@Nullable RecyclerView newTarget){
		if(scrollTarget==newTarget)
			return;
		if(scrollTarget!=null && scrollListener!=null)
			scrollTarget.removeOnScrollListener(scrollListener);
		restorePadding();
		scrollTarget=newTarget;
		if(newTarget==null)
			return;
		if(scrollListener==null)
			scrollListener=new NavScrollListener();
		newTarget.addOnScrollListener(scrollListener);
		applyContentPadding(newTarget);
		// Start from a known state: visible at the top of the list
		if(newTarget.computeVerticalScrollOffset()==0)
			showFab();
	}

	public void setBottomInset(int inset){
		if(bottomInset==inset)
			return;
		bottomInset=inset;
		applyFabMargin();
		if(scrollTarget!=null)
			applyContentPadding(scrollTarget);
	}

	private void applyFabMargin(){
		if(view==null)
			return;
		int margin=V.dp(16)+(bottomInset>0 ? Math.max(bottomInset, V.dp(24)) : 0);
		View anchor=view.findViewById(org.joinmastodon.android.R.id.anchor);
		ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)anchor.getLayoutParams();
		if(lp.bottomMargin!=margin){
			lp.bottomMargin=margin;
			anchor.setLayoutParams(lp);
		}
	}

	// The scrollable content's bottom padding must be 88dp + navigation-bar inset so
	// the last item can scroll completely above the FAB
	private void applyContentPadding(RecyclerView target){
		int padding=V.dp(88)+bottomInset;
		if(paddedTarget!=target){
			restorePadding();
			paddedTarget=target;
			savedPaddingBottom=target.getPaddingBottom();
		}
		if(target.getPaddingBottom()!=padding){
			target.setPadding(target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(), padding);
			target.setClipToPadding(false);
		}
	}

	private void restorePadding(){
		if(paddedTarget!=null && savedPaddingBottom>=0 && paddedTarget.getPaddingBottom()!=savedPaddingBottom)
			paddedTarget.setPadding(paddedTarget.getPaddingLeft(), paddedTarget.getPaddingTop(), paddedTarget.getPaddingRight(), savedPaddingBottom);
		paddedTarget=null;
		savedPaddingBottom=-1;
	}

	@Override
	public void onDestinationSelected(NavDestination destination){
		host.onNavDestinationSelected(destination);
	}

	@Override
	public void onComposeSelected(){
		host.onNavComposeSelected();
	}

	@Override
	public void onOpenChanged(boolean open){
		if(open && scrollTarget!=null)
			scrollTarget.stopScroll(); // an open menu locks scrolling
		host.onNavMenuOpenChanged(open);
	}

	/**
	 * Implements the spec's hide/show rule:
	 * past 96dp of scroll offset, 24dp of cumulative downward travel hides the FAB;
	 * any 8dp of cumulative upward travel restores it. Direction change resets the
	 * opposite accumulator.
	 */
	private class NavScrollListener extends RecyclerView.OnScrollListener{
		private int downAccum;
		private int upAccum;

		@Override
		public void onScrolled(RecyclerView recyclerView, int dx, int dy){
			if(view==null)
				return;
			int offset=recyclerView.computeVerticalScrollOffset();
			if(offset==0){
				downAccum=0;
				upAccum=0;
				view.showFab();
				return;
			}
			if(dy>0){
				upAccum=0;
				downAccum+=dy;
				if(offset>NavMenuView.getHideOffsetThreshold() && downAccum>=NavMenuView.getHideDeltaThreshold())
					view.hideFab();
			}else if(dy<0){
				downAccum=0;
				upAccum-=dy;
				if(upAccum>=NavMenuView.getShowDeltaThreshold())
					view.showFab();
			}
		}
	}
}
