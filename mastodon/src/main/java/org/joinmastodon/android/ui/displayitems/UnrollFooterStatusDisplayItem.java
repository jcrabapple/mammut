package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joinmastodon.android.R;

/**
 * MAMMUT: Reading Room (docs/READING-ROOM-SPEC.md §5.7) — the end-of-story footer:
 * "End of Mara's thread", plus — only when side branches exist — the factual second
 * line "The other 10 posts are side replies, not part of this story."
 */
public class UnrollFooterStatusDisplayItem extends StatusDisplayItem{
	public final CharSequence title, body;

	public UnrollFooterStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence title, CharSequence body){
		super(parentID, callbacks, context);
		this.title=title;
		this.body=body;
	}

	@Override
	public Type getType(){
		return Type.UNROLL_FOOTER;
	}

	public static class Holder extends StatusDisplayItem.Holder<UnrollFooterStatusDisplayItem>{
		private final TextView title, body;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_unroll_footer, parent);
			title=findViewById(R.id.title);
			body=findViewById(R.id.body);
		}

		@Override
		public void onBind(UnrollFooterStatusDisplayItem item){
			title.setText(item.title);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
				title.setAccessibilityHeading(true);
			if(item.body!=null){
				body.setVisibility(View.VISIBLE);
				body.setText(item.body);
			}else{
				body.setVisibility(View.GONE);
			}
		}

		@Override
		public boolean isEnabled(){
			return false;
		}
	}
}
