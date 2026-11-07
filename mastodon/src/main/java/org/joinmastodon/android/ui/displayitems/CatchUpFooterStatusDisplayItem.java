package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.joinmastodon.android.R;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md §6, §8) — the end footer. A leaf-colored
 * rule, the variant title (also an accessibility heading / jump target), the honest
 * body copy, and the "Load older posts" action. The action row is hidden once the
 * server itself has reached the end of the timeline.
 */
public class CatchUpFooterStatusDisplayItem extends StatusDisplayItem{
	public final CharSequence title, body, actionText;
	public final Runnable onAction;

	public CatchUpFooterStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence title, CharSequence body, CharSequence actionText, Runnable onAction){
		super(parentID, callbacks, context);
		this.title=title;
		this.body=body;
		this.actionText=actionText;
		this.onAction=onAction;
	}

	@Override
	public Type getType(){
		return Type.CATCH_UP_FOOTER;
	}

	public static class Holder extends StatusDisplayItem.Holder<CatchUpFooterStatusDisplayItem>{
		private final TextView title, body;
		private final Button action;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_catch_up_footer, parent);
			title=findViewById(R.id.title);
			body=findViewById(R.id.body);
			action=findViewById(R.id.action_btn);
			action.setOnClickListener(v->{
				if(item.onAction!=null)
					item.onAction.run();
			});
		}

		@Override
		public void onBind(CatchUpFooterStatusDisplayItem item){
			title.setText(item.title);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
				title.setAccessibilityHeading(true);
			if(item.body!=null){
				body.setVisibility(View.VISIBLE);
				body.setText(item.body);
			}else{
				body.setVisibility(View.GONE);
			}
			if(item.onAction!=null){
				action.setVisibility(View.VISIBLE);
				action.setText(item.actionText);
			}else{
				action.setVisibility(View.GONE);
			}
		}

		@Override
		public boolean isEnabled(){
			return false;
		}
	}
}
