package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.joinmastodon.android.R;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md §5, §8) — one header per author group
 * ("Mara · 4 posts") and the collapsible boosts section header. Counts are part of
 * the announced heading text. The boosts header is a single actionable element with
 * Expanded/Collapsed stateDescription; author headers are plain headings. Nothing
 * here is single-line or ellipsized so 200% font scaling wraps instead of clipping.
 */
public class CatchUpHeaderStatusDisplayItem extends StatusDisplayItem{
	public final CharSequence title;
	public final boolean collapsible;
	public final boolean collapsed;
	public final Runnable onToggle;

	public CatchUpHeaderStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence title, boolean collapsible, boolean collapsed, Runnable onToggle){
		super(parentID, callbacks, context);
		this.title=title;
		this.collapsible=collapsible;
		this.collapsed=collapsed;
		this.onToggle=onToggle;
	}

	@Override
	public Type getType(){
		return Type.CATCH_UP_HEADER;
	}

	public static class Holder extends StatusDisplayItem.Holder<CatchUpHeaderStatusDisplayItem>{
		private final TextView title;
		private final ImageView chevron;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_catch_up_header, parent);
			title=findViewById(R.id.title);
			chevron=findViewById(R.id.chevron);
		}

		@Override
		public void onBind(CatchUpHeaderStatusDisplayItem item){
			title.setText(item.title);
			// Spec §8: every group header is an accessibility heading. The framework
			// API exists from API 28; on older releases headings navigation simply
			// degrades to plain text.
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
				title.setAccessibilityHeading(true);
			if(item.collapsible){
				chevron.setVisibility(View.VISIBLE);
				chevron.setRotation(item.collapsed ? -90f : 0f);
				// stateDescription is API 30+; pre-30 the visible text is still
				// announced, the expanded/collapsed state just has no annotation.
				if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)
					itemView.setStateDescription(itemView.getContext().getString(item.collapsed ? R.string.catch_up_state_collapsed : R.string.catch_up_state_expanded));
			}else{
				chevron.setVisibility(View.GONE);
				if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)
					itemView.setStateDescription(null);
			}
		}

		@Override
		public void onClick(){
			if(item.collapsible && item.onToggle!=null)
				item.onToggle.run();
		}

		@Override
		public boolean isEnabled(){
			return item.collapsible;
		}
	}
}
