package org.joinmastodon.android.ui.viewholders;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.joinmastodon.android.R;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.ui.views.M3Switch;

import me.grishka.appkit.utils.V;

public class SwitchListItemViewHolder extends CheckableListItemViewHolder{
	private final M3Switch sw;
	private boolean ignoreListener;

	public SwitchListItemViewHolder(Context context, ViewGroup parent, boolean separated){
		super(context, parent);
		if(separated){
			View separator=new View(context);
			separator.setBackgroundColor(UiUtils.getThemeColor(context, R.attr.colorM3OutlineVariant));
			LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(V.dp(1), V.dp(32));
			lp.gravity=Gravity.TOP;
			lp.setMarginStart(V.dp(16));
			lp.setMarginEnd(V.dp(-1));
			checkableLayout.addView(separator, lp);
		}
		sw=new M3Switch(context);
		LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(V.dp(52), V.dp(32));
		lp.gravity=Gravity.TOP;
		lp.setMarginStart(V.dp(16));
		checkableLayout.addView(sw, lp);
		sw.setOnCheckedChangeListener((buttonView, isChecked)->{
			if(ignoreListener)
				return;
			if(item.checkedChangeListener!=null){
				item.checkedChangeListener.accept(isChecked);
			}else{
				// The ROW's click handler owns the preference: it toggles the item, saves
				// and rebinds. Writing isChecked here and stopping meant a tap on the
				// switch itself never saved anything, so the setting reverted the next
				// time the screen opened (the enhance-text-size switch that would not
				// stay off). Undo our own write and go through the same path as a row
				// tap; rebind sets the switch with ignoreListener held.
				item.checked=!isChecked;
				item.performClick();
			}
		});
		sw.setClickable(true);
	}

	@Override
	public void onBind(CheckableListItem<?> item){
		super.onBind(item);
		ignoreListener=true;
		sw.setChecked(item.checked);
		sw.setEnabled(item.isEnabled);
		ignoreListener=false;
	}
}
