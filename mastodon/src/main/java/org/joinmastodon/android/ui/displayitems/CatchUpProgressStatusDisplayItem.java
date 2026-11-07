package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.joinmastodon.android.R;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md §6, §7) — the inline row that sits where
 * the next page would be: a progress row while an extension page is in flight, or
 * an error card with "Try again" after a failed page. Used both for the mid-fill
 * partial state and for a failed "Load older posts" extension; existing content is
 * never touched by a failure.
 */
public class CatchUpProgressStatusDisplayItem extends StatusDisplayItem{
	public enum Mode{PROGRESS, ERROR}

	public final Mode mode;
	public final CharSequence text, buttonText;
	public final Runnable onButtonClick;

	public CatchUpProgressStatusDisplayItem(String parentID, Callbacks callbacks, Context context, Mode mode, CharSequence text, CharSequence buttonText, Runnable onButtonClick){
		super(parentID, callbacks, context);
		this.mode=mode;
		this.text=text;
		this.buttonText=buttonText;
		this.onButtonClick=onButtonClick;
	}

	@Override
	public Type getType(){
		return Type.CATCH_UP_PROGRESS;
	}

	public static class Holder extends StatusDisplayItem.Holder<CatchUpProgressStatusDisplayItem>{
		private final ProgressBar progress;
		private final TextView text;
		private final Button button;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_catch_up_progress, parent);
			progress=findViewById(R.id.progress);
			text=findViewById(R.id.text);
			button=findViewById(R.id.button);
			button.setOnClickListener(v->{
				if(item.onButtonClick!=null)
					item.onButtonClick.run();
			});
		}

		@Override
		public void onBind(CatchUpProgressStatusDisplayItem item){
			progress.setVisibility(item.mode==Mode.PROGRESS ? View.VISIBLE : View.GONE);
			text.setText(item.text);
			if(item.mode==Mode.ERROR && item.onButtonClick!=null){
				button.setVisibility(View.VISIBLE);
				button.setText(item.buttonText);
			}else{
				button.setVisibility(View.GONE);
			}
		}

		@Override
		public boolean isEnabled(){
			return false;
		}
	}
}
