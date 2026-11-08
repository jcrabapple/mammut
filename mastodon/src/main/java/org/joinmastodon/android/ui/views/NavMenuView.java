package org.joinmastodon.android.ui.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.ui.NavDestination;
import org.joinmastodon.android.fragments.discover.DiscoverFragment;
import org.joinmastodon.android.fragments.settings.SettingsMainFragment;
import org.joinmastodon.android.ui.utils.NavToolbarUtils;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.Nav;
import me.grishka.appkit.utils.V;

/**
 * MAMMUT: the navigation FAB with its vertical speed-dial menu and scrim.
 * Implements the Mammut FAB navigation spec: geometry, motion, badge and the
 * accessibility contract. Host fragments drive it through NavMenuController.
 */
public class NavMenuView extends FrameLayout{
	private static final float SCRIM_ALPHA=0.32f;

	// Motion spec durations
	private static final long SCRIM_DURATION=120;
	private static final long GLYPH_DURATION=150;
	private static final long PANEL_OPEN_DURATION=180;
	private static final long ROW_OPEN_DURATION=140;
	private static final long ROW_STAGGER=16;
	private static final long CLOSE_DURATION=140;
	private static final long FAB_HIDE_DURATION=160;
	private static final long FAB_SHOW_DURATION=180;
	private static final long REDUCED_MOTION_DURATION=80;

	// Scroll rule thresholds
	private static final int HIDE_OFFSET_THRESHOLD=V.dp(96);
	private static final int HIDE_DELTA_THRESHOLD=V.dp(24);
	private static final int SHOW_DELTA_THRESHOLD=V.dp(8);
	private static final int HIDE_TRANSLATION=V.dp(112);

	public static int getHideOffsetThreshold(){
		return HIDE_OFFSET_THRESHOLD;
	}

	public static int getHideDeltaThreshold(){
		return HIDE_DELTA_THRESHOLD;
	}

	public static int getShowDeltaThreshold(){
		return SHOW_DELTA_THRESHOLD;
	}

	public interface OnMenuActionListener{
		void onDestinationSelected(NavDestination destination);
		void onComposeSelected();
		void onOpenChanged(boolean open);
	}

	private View scrim;
	private FrameLayout anchor;
	private ScrollView menuScroller;
	private LinearLayout menuPanel;
	private View fab;
	private ImageView fabGlyphLines, fabGlyphClose;
	
	private final Map<NavDestination, View> rows=new EnumMap<>(NavDestination.class);
	private final Map<NavDestination, TextView> rowBadges=new EnumMap<>(NavDestination.class);
	private View composeRow;
	private View currentRow;

	private NavDestination destination=NavDestination.HOME;
	private OnMenuActionListener listener;

	private boolean menuOpen;
	private boolean fabHidden;
	private int unreadCount;
	private boolean unreadMore;

	private final ArrayList<Animator> currentAnims=new ArrayList<>();
	private final Rect fabHitRect=new Rect();

	private final boolean reducedMotion;
	private String accountID;

	public NavMenuView(@NonNull Context context){
		this(context, null);
	}

	public NavMenuView(@NonNull Context context, @Nullable AttributeSet attrs){
		this(context, attrs, 0);
	}

	public NavMenuView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr){
		super(context, attrs, defStyleAttr);
		float animatorScale=1f;
		try{
			animatorScale=Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE);
		}catch(Settings.SettingNotFoundException ignored){}
		reducedMotion=GlobalUserPreferences.reduceMotion || animatorScale==0f;

		inflate(context, R.layout.nav_menu, this);
		scrim=findViewById(R.id.scrim);
		anchor=findViewById(R.id.anchor);
		menuScroller=findViewById(R.id.menu_scroller);
		menuPanel=findViewById(R.id.menu_panel);
		fab=findViewById(R.id.fab);
		fabGlyphLines=findViewById(R.id.fab_glyph_lines);
		fabGlyphClose=findViewById(R.id.fab_glyph_close);

		fab.setOnClickListener(v->{
			if(menuOpen)
				closeMenu();
			else
				openMenu();
		});
		scrim.setOnClickListener(v->closeMenu());

		LayoutInflater inflater=LayoutInflater.from(context);
		for(NavDestination dest:NavDestination.values()){
			View row=createRow(inflater, dest);
			rows.put(dest, row);
			menuPanel.addView(row);
		}

		menuPanel.addView(createActionRow(inflater, R.drawable.ic_tab_search, R.string.tab_search,
				R.string.tab_search, v->openDiscover()));
		menuPanel.addView(createActionRow(inflater, R.drawable.ic_fluent_person_24_regular, R.string.tab_profile,
				R.string.my_profile, v->openProfile()));

		View divider=new View(context);
		divider.setBackgroundColor(UiUtils.getThemeColor(context, R.attr.colorM3OutlineVariant));
		LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(1));
		dividerParams.topMargin=V.dp(6);
		dividerParams.bottomMargin=V.dp(6);
		dividerParams.leftMargin=V.dp(8);
		dividerParams.rightMargin=V.dp(8);
		menuPanel.addView(divider, dividerParams);

		// MAMMUT: Explore and Profile were bottom-bar destinations; they belong in the menu too
		composeRow=createRow(inflater, null);
		menuPanel.addView(composeRow);

		// MAMMUT: Composer 2.0 (§3) — Drafts sits directly under Compose
		menuPanel.addView(createDraftsRow(inflater));

		// MAMMUT: Settings moved out of the overflow menu and into the navigation menu
		menuPanel.addView(createDivider(context));
		menuPanel.addView(createSettingsRow(inflater));

		menuPanel.setPivotX(menuPanel.getWidth()); // set properly once laid out
		menuPanel.setPivotX(V.dp(216));
		menuPanel.setPivotY(menuPanel.getHeight());

		updateBadge();
		updateRowBadges();
		updateFabDescription();

		getViewTreeObserver().addOnGlobalLayoutListener(this::updateMenuMaxHeight);
	}

	private View createDivider(Context context){
		View divider=new View(context);
		divider.setBackgroundColor(UiUtils.getThemeColor(context, R.attr.colorM3OutlineVariant));
		LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(1));
		lp.topMargin=V.dp(6);
		lp.bottomMargin=V.dp(6);
		lp.leftMargin=V.dp(8);
		lp.rightMargin=V.dp(8);
		divider.setLayoutParams(lp);
		return divider;
	}

	private View createActionRow(LayoutInflater inflater, int iconRes, int labelRes, int descRes, View.OnClickListener onClick){
		View row=inflater.inflate(R.layout.nav_menu_row, menuPanel, false);
		ImageView icon=row.findViewById(R.id.icon);
		TextView label=row.findViewById(R.id.label);
		icon.setImageResource(iconRes);
		label.setText(labelRes);
		row.setContentDescription(getContext().getString(descRes));
		row.setOnClickListener(v->{
			closeMenu();
			onClick.onClick(v);
		});
		return row;
	}

	private void openDiscover(){
		if(accountID==null || !(getContext() instanceof android.app.Activity activity))
			return;
		Bundle args=new Bundle();
		args.putString("account", accountID);
		Nav.go(activity, DiscoverFragment.class, args);
	}

	private void openProfile(){
		if(accountID==null || !(getContext() instanceof android.app.Activity activity))
			return;
		NavToolbarUtils.openSelfProfile(activity, accountID);
	}

	private View createDraftsRow(LayoutInflater inflater){
		View row=inflater.inflate(R.layout.nav_menu_row, menuPanel, false);
		ImageView icon=row.findViewById(R.id.icon);
		TextView label=row.findViewById(R.id.label);
		icon.setImageResource(R.drawable.ic_fluent_calendar_note_24_regular);
		label.setText(R.string.drafts);
		row.setContentDescription(getContext().getString(R.string.drafts));
		row.setOnClickListener(v->{
			closeMenu();
			if(accountID==null || !(getContext() instanceof android.app.Activity activity))
				return;
			Bundle args=new Bundle();
			args.putString("account", accountID);
			Nav.go(activity, org.joinmastodon.android.fragments.drafts.DraftsListFragment.class, args);
		});
		return row;
	}

	private View createSettingsRow(LayoutInflater inflater){
		View row=inflater.inflate(R.layout.nav_menu_row, menuPanel, false);
		ImageView icon=row.findViewById(R.id.icon);
		TextView label=row.findViewById(R.id.label);
		icon.setImageResource(R.drawable.ic_fluent_settings_24_regular);
		label.setText(R.string.settings);
		row.setContentDescription(getContext().getString(R.string.settings));
		row.setOnClickListener(v->{
			closeMenu();
			if(accountID==null)
				return;
			Bundle args=new Bundle();
			args.putString("account", accountID);
			if(getContext() instanceof android.app.Activity activity)
				Nav.go(activity, SettingsMainFragment.class, args);
		});
		return row;
	}

	public void setAccountID(String accountID){
		this.accountID=accountID;
	}

	private View createRow(LayoutInflater inflater, @Nullable NavDestination dest){
		View row=inflater.inflate(R.layout.nav_menu_row, menuPanel, false);
		ImageView icon=row.findViewById(R.id.icon);
		TextView label=row.findViewById(R.id.label);
		if(dest==null){
			icon.setImageResource(R.drawable.ic_nav_compose_24);
			label.setText(R.string.new_post);
			row.setContentDescription(getContext().getString(R.string.nav_cd_compose));
			row.setOnClickListener(v->{
				if(listener!=null)
					listener.onComposeSelected();
			});
		}else{
			icon.setImageResource(dest.getIconRes());
			label.setText(dest.getTitleRes());
			row.setContentDescription(getContext().getString(dest.getContentDescriptionRes()));
			rowBadges.put(dest, row.findViewById(R.id.badge));
			row.setOnClickListener(v->{
				if(listener!=null)
					listener.onDestinationSelected(dest);
			});
		}
		return row;
	}

	public void setListener(OnMenuActionListener listener){
		this.listener=listener;
	}

	public boolean isMenuOpen(){
		return menuOpen;
	}

	public boolean isFabHidden(){
		return fabHidden;
	}

	public void setDestination(NavDestination destination){
		this.destination=destination;
		for(Map.Entry<NavDestination, View> e:rows.entrySet()){
			boolean selected=e.getKey()==destination;
			View row=e.getValue();
			row.setSelected(selected);
			row.findViewById(R.id.selection_bar).setVisibility(selected ? View.VISIBLE : View.GONE);
			if(selected)
				currentRow=row;
		}
		if(menuOpen)
			focusCurrentRow();
	}

	public void setUnreadCount(int count, boolean more){
		unreadCount=count;
		unreadMore=more;
		updateBadge();
		updateRowBadges();
		updateFabDescription();
	}

	/**
	 * Shows the FAB again, e.g. on destination change or when returning from the composer.
	 */
	public void showFab(){
		if(!fabHidden)
			return;
		fabHidden=false;
		cancelAnims();
		fab.setVisibility(VISIBLE);
		fab.setFocusable(true);
		fab.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
		if(reducedMotion){
			fab.setAlpha(1f);
			fab.setTranslationY(0);
		}else{
			AnimatorSet set=new AnimatorSet();
			set.playTogether(
					ObjectAnimator.ofFloat(fab, ALPHA, 1f),
					ObjectAnimator.ofFloat(fab, TRANSLATION_Y, 0f)
			);
			set.setDuration(FAB_SHOW_DURATION);
			set.setInterpolator(CubicBezierInterpolator.DEFAULT);
			runAnims(set);
		}
		updateBadge();
	}

	/**
	 * Scroll-hides the FAB past the spec thresholds. Never hides an open menu.
	 */
	public void hideFab(){
		if(fabHidden || menuOpen)
			return;
		fabHidden=true;
		cancelAnims();
		if(reducedMotion){
			fab.setAlpha(0f);
			onFabHidden();
		}else{
			AnimatorSet set=new AnimatorSet();
			set.playTogether(
					ObjectAnimator.ofFloat(fab, ALPHA, 0f),
					ObjectAnimator.ofFloat(fab, TRANSLATION_Y, HIDE_TRANSLATION)
			);
			set.setDuration(FAB_HIDE_DURATION);
			set.setInterpolator(CubicBezierInterpolator.DEFAULT);
			set.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					if(fabHidden)
						onFabHidden();
				}
			});
			runAnims(set);
		}
	}

	private void onFabHidden(){
		fab.setVisibility(INVISIBLE);
		fab.setFocusable(false);
		fab.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
	}

	public void openMenu(){
		if(menuOpen)
			return;
		menuOpen=true;
		if(fabHidden)
			showFab();
		cancelAnims();

		scrim.setVisibility(VISIBLE);
		menuScroller.setVisibility(VISIBLE);

		AnimatorSet scrimAnim=new AnimatorSet();
		scrimAnim.play(ObjectAnimator.ofFloat(scrim, ALPHA, SCRIM_ALPHA));
		scrimAnim.setDuration(reducedMotion ? REDUCED_MOTION_DURATION : SCRIM_DURATION);
		runAnims(scrimAnim);

		animateGlyph(true);

		if(reducedMotion){
			menuPanel.setTranslationY(0);
			menuPanel.setScaleX(1f);
			menuPanel.setScaleY(1f);
			for(int i=0;i<menuPanel.getChildCount();i++){
				View child=menuPanel.getChildAt(i);
				child.setAlpha(1f);
				child.setTranslationY(0);
			}
			// Reduced motion still fades, just at the shorter duration
			AnimatorSet panelAnim=new AnimatorSet();
			panelAnim.play(ObjectAnimator.ofFloat(menuPanel, ALPHA, 1f));
			panelAnim.setDuration(REDUCED_MOTION_DURATION);
			runAnims(panelAnim);
		}else{
			menuPanel.setPivotX(menuPanel.getWidth());
			menuPanel.setPivotY(menuPanel.getHeight());
			AnimatorSet panelAnim=new AnimatorSet();
			panelAnim.playTogether(
					ObjectAnimator.ofFloat(menuPanel, ALPHA, 1f),
					ObjectAnimator.ofFloat(menuPanel, TRANSLATION_Y, 0f),
					ObjectAnimator.ofFloat(menuPanel, SCALE_X, 1f),
					ObjectAnimator.ofFloat(menuPanel, SCALE_Y, 1f)
			);
			panelAnim.setDuration(PANEL_OPEN_DURATION);
			panelAnim.setInterpolator(CubicBezierInterpolator.DEFAULT);
			runAnims(panelAnim);

			// Rows open with a 16ms stagger; the last row finishes at 204ms
			for(int i=0;i<menuPanel.getChildCount();i++){
				View child=menuPanel.getChildAt(i);
				child.setAlpha(0f);
				ObjectAnimator rowAnim=ObjectAnimator.ofFloat(child, ALPHA, 1f);
				rowAnim.setDuration(ROW_OPEN_DURATION);
				rowAnim.setStartDelay(i*ROW_STAGGER);
				rowAnim.setInterpolator(CubicBezierInterpolator.DEFAULT);
				if(!reducedMotion){
					child.setTranslationY(V.dp(8));
					ObjectAnimator rowTranslate=ObjectAnimator.ofFloat(child, TRANSLATION_Y, 0f);
					rowTranslate.setDuration(ROW_OPEN_DURATION);
					rowTranslate.setStartDelay(i*ROW_STAGGER);
					rowTranslate.setInterpolator(CubicBezierInterpolator.DEFAULT);
					rowTranslate.start();
					currentAnims.add(rowTranslate);
				}
				rowAnim.start();
				currentAnims.add(rowAnim);
			}
		}

		// Opening the menu must not duplicate the unread count: keep it on the row only
		updateFabDescription();
		updateRowBadges();
		bringSelectedRowIntoView();
		focusCurrentRow();
		if(listener!=null)
			listener.onOpenChanged(true);
	}

	public void closeMenu(){
		if(!menuOpen)
			return;
		menuOpen=false;
		cancelAnims();

		AnimatorSet scrimAnim=new AnimatorSet();
		scrimAnim.play(ObjectAnimator.ofFloat(scrim, ALPHA, 0f));
		scrimAnim.setDuration(reducedMotion ? REDUCED_MOTION_DURATION : SCRIM_DURATION);
		scrimAnim.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				if(!menuOpen){
					scrim.setVisibility(GONE);
					menuScroller.setVisibility(GONE);
				}
			}
		});
		runAnims(scrimAnim);

		animateGlyph(false);

		if(reducedMotion){
			// Reduced motion still fades, just at the shorter duration
			AnimatorSet panelAnim=new AnimatorSet();
			panelAnim.play(ObjectAnimator.ofFloat(menuPanel, ALPHA, 0f));
			panelAnim.setDuration(REDUCED_MOTION_DURATION);
			runAnims(panelAnim);
		}else{
			AnimatorSet panelAnim=new AnimatorSet();
			panelAnim.playTogether(
					ObjectAnimator.ofFloat(menuPanel, ALPHA, 0f),
					ObjectAnimator.ofFloat(menuPanel, TRANSLATION_Y, V.dp(8))
			);
			panelAnim.setDuration(CLOSE_DURATION);
			panelAnim.setInterpolator(CubicBezierInterpolator.DEFAULT);
			runAnims(panelAnim);
		}

		updateBadge();
		updateFabDescription();
		if(listener!=null)
			listener.onOpenChanged(false);
		// Return focus to the FAB for keyboard/screen-reader users
		fab.post(()->fab.requestFocus());
	}

	private void animateGlyph(boolean open){
		if(reducedMotion){
			fabGlyphLines.setAlpha(open ? 0f : 1f);
			fabGlyphClose.setAlpha(open ? 1f : 0f);
			fab.setRotation(0f);
			return;
		}
		AnimatorSet set=new AnimatorSet();
		if(open){
			set.playTogether(
					ObjectAnimator.ofFloat(fab, ROTATION, 90f),
					ObjectAnimator.ofFloat(fabGlyphLines, ALPHA, 0f),
					ObjectAnimator.ofFloat(fabGlyphClose, ALPHA, 1f)
			);
		}else{
			set.playTogether(
					ObjectAnimator.ofFloat(fab, ROTATION, 0f),
					ObjectAnimator.ofFloat(fabGlyphLines, ALPHA, 1f),
					ObjectAnimator.ofFloat(fabGlyphClose, ALPHA, 0f)
			);
		}
		set.setDuration(GLYPH_DURATION);
		set.setInterpolator(CubicBezierInterpolator.DEFAULT);
		runAnims(set);
	}

	/**
	 * MAMMUT: the FAB no longer carries the count. The top app bar chip is the single
	 * always-visible indicator — the FAB hides on scroll, and showing the same number in two
	 * places at once was redundant. The count is still announced on the FAB for screen readers.
	 */
	private void updateBadge(){
		updateFabDescription();
	}

	@SuppressLint("SetTextI18n")
	private void updateRowBadges(){
		TextView badge=rowBadges.get(NavDestination.NOTIFICATIONS);
		View row=rows.get(NavDestination.NOTIFICATIONS);
		if(badge==null || row==null)
			return;
		if(unreadCount<=0){
			badge.setVisibility(GONE);
			row.setContentDescription(getContext().getString(NavDestination.NOTIFICATIONS.getContentDescriptionRes()));
		}else{
			boolean more=unreadMore || unreadCount>99;
			badge.setText(more ? (Math.min(unreadCount, 99)+"+") : String.valueOf(unreadCount));
			badge.setVisibility(VISIBLE);
			row.setContentDescription(getContext().getResources().getQuantityString(R.plurals.nav_cd_notifications_unread, unreadCount, unreadCount));
		}
	}

	private void updateFabDescription(){
		String state=getContext().getString(menuOpen ? R.string.nav_state_open : R.string.nav_state_closed);
		if(unreadCount>0 && !menuOpen){
			String unread=getContext().getResources().getQuantityString(R.plurals.nav_unread_notifications, unreadCount, unreadCount);
			state=state+"; "+unread;
		}
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)
			fab.setStateDescription(state);
		else
			fab.setContentDescription(state);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev){
		// While the FAB is scroll-hidden it is neither hit-testable nor focusable
		if(fabHidden)
			return false;
		if(!menuOpen){
			// Closed: only the FAB receives touches; everything else passes through
			// to the content underneath
			fab.getHitRect(fabHitRect);
			fabHitRect.offset(anchor.getLeft(), anchor.getTop());
			if(!fabHitRect.contains((int)ev.getX(), (int)ev.getY()))
				return false;
		}
		return super.dispatchTouchEvent(ev);
	}

	private void focusCurrentRow(){
		if(currentRow!=null)
			currentRow.post(()->currentRow.requestFocus());
	}

	private void bringSelectedRowIntoView(){
		if(currentRow==null)
			return;
		menuScroller.post(()->{
			int rowTop=currentRow.getTop();
			int rowBottom=currentRow.getBottom();
			if(rowTop<menuScroller.getScrollY() || rowBottom>menuScroller.getScrollY()+menuScroller.getHeight())
				menuScroller.smoothScrollTo(0, rowTop-(menuScroller.getHeight()-currentRow.getHeight())/2);
		});
	}

	private void updateMenuMaxHeight(){
		if(getHeight()==0)
			return;
		int bottomReserve=((MarginLayoutParams)anchor.getLayoutParams()).bottomMargin+V.dp(56)+V.dp(12);
		// Space below the top app bar; the 88dp allowance covers status bar + toolbar
		int maxHeight=getHeight()-bottomReserve-V.dp(88)-V.dp(16);
		int minHeight=V.dp(48)*3+V.dp(16);
		if(maxHeight<minHeight)
			maxHeight=minHeight;
		ViewGroup.LayoutParams params=menuScroller.getLayoutParams();
		int desired=menuPanel.getMeasuredHeight();
		int newHeight=desired>maxHeight ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
		if(params.height!=newHeight){
			params.height=newHeight;
			menuScroller.setLayoutParams(params);
		}
	}

	@Override
	public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event){
		updateRowBadges();
		return super.dispatchPopulateAccessibilityEvent(event);
	}

	private void cancelAnims(){
		for(Animator a:currentAnims)
			a.cancel();
		currentAnims.clear();
	}

	private void runAnims(AnimatorSet set){
		currentAnims.add(set);
		set.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(Animator animation){
				currentAnims.remove(animation);
			}
		});
		set.start();
	}
}
