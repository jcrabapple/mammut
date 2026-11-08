package org.joinmastodon.android.ui.displayitems;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.model.Poll;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.utils.UiUtils;

import me.grishka.appkit.utils.V;

public class PollFooterStatusDisplayItem extends StatusDisplayItem{
	public final Poll poll;
	public final Status status;

	public PollFooterStatusDisplayItem(String parentID, Callbacks callbacks, Context context, Poll poll, Status status){
		super(parentID, callbacks, context);
		this.poll=poll;
		this.status=status;
	}

	@Override
	public Type getType(){
		return Type.POLL_FOOTER;
	}

	public static class Holder extends StatusDisplayItem.Holder<PollFooterStatusDisplayItem>{
		private final TextView text;
		private final Button voteButton, toggleResultsButton;

		public Holder(Activity activity, ViewGroup parent){
			super(activity, R.layout.display_item_poll_footer, parent);
			text=findViewById(R.id.text);
			voteButton=findViewById(R.id.vote_btn);
			toggleResultsButton=findViewById(R.id.show_results_btn);
			voteButton.setOnClickListener(v->item.callbacks.onPollVoteButtonClick(this));
			toggleResultsButton.setOnClickListener(v->{
				item.callbacks.onPollToggleResultsClick(this);
				rebind();
			});
		}

		@Override
		public void onBind(PollFooterStatusDisplayItem item){
			itemView.setPaddingRelative(V.dp(item.fullWidth ? 16 : 64), itemView.getPaddingTop(), itemView.getPaddingEnd(), itemView.getPaddingBottom());
			String text=item.context.getResources().getQuantityString(R.plurals.x_votes, item.poll.votesCount, item.poll.votesCount);
			if(item.poll.expiresAt!=null && !item.poll.isExpired()){
				text+=" · "+UiUtils.formatTimeLeft(itemView.getContext(), item.poll.expiresAt);
				if(item.poll.multiple)
					text+=" · "+item.context.getString(R.string.poll_multiple_choice);
			}else if(item.poll.isExpired()){
				text+=" · "+item.context.getString(R.string.poll_closed);
			}
			this.text.setText(text);
			// MAMMUT: voting is one-way, and the results are already on screen once the server
			// says you voted. Leaving a filled, inert Vote button there reads as "press me" and
			// does nothing — so it goes. Before that, a button that cannot act (nothing selected)
			// is dimmed rather than merely disabled, since the filled style showed no difference.
			boolean voted=item.poll.voted;
			boolean expired=item.poll.isExpired();
			boolean hasSelection=item.poll.selectedOptions!=null && !item.poll.selectedOptions.isEmpty();
			boolean canVote=hasSelection && !expired && !voted;
			voteButton.setVisibility(voted || expired ? View.GONE : View.VISIBLE);
			voteButton.setEnabled(canVote);
			voteButton.setAlpha(canVote ? 1f : 0.38f);
			toggleResultsButton.setVisibility(expired || voted ? View.GONE : View.VISIBLE);
			toggleResultsButton.setText(item.poll.showResults ? R.string.poll_hide_results : R.string.poll_see_results);
		}
	}
}
