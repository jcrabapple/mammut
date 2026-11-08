package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joinmastodon.android.R;

/**
 * MAMMUT: Reading Room (docs/READING-ROOM-SPEC.md §5.4) — the inline marker after a
 * chain post that has fetched off-chain replies: "2 replies to this post · Show in
 * full conversation." Tapping it returns to the Conversation scrolled to that post.
 * Side-branch content is never interleaved into the story.
 */
public class UnrollBranchMarkerStatusDisplayItem extends StatusDisplayItem{
	public final CharSequence label;
	public final Runnable onShowInConversation;

	public UnrollBranchMarkerStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence label, Runnable onShowInConversation){
		super(parentID, callbacks, context);
		this.label=label;
		this.onShowInConversation=onShowInConversation;
	}

	@Override
	public Type getType(){
		return Type.UNROLL_MARKER;
	}

	public static class Holder extends StatusDisplayItem.Holder<UnrollBranchMarkerStatusDisplayItem>{
		private final TextView label;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_unroll_marker, parent);
			label=findViewById(R.id.label);
			itemView.setOnClickListener(v->{
				if(item.onShowInConversation!=null)
					item.onShowInConversation.run();
			});
		}

		@Override
		public void onBind(UnrollBranchMarkerStatusDisplayItem item){
			label.setText(item.label);
		}

		@Override
		public boolean isEnabled(){
			// The marker runs its own action; the row must not also navigate to the post.
			return false;
		}
	}
}
