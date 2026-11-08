package org.joinmastodon.android.ui.displayitems;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joinmastodon.android.R;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md) — the absolute time a post was written, per post.
 *
 * Catch up renders posts without a header row, so the relative timestamp the ordinary timeline
 * shows ("3h") has nowhere to live, and a relative time is the wrong instrument for a bounded
 * reading view anyway: the reader is looking at a fixed 24 hours and needs to know when in that
 * day something was said. This item carries the real date and time, in the reader's locale.
 */
public class CatchUpTimestampStatusDisplayItem extends StatusDisplayItem{
	private static final DateTimeFormatter FORMATTER=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

	public final Instant timestamp;

	public CatchUpTimestampStatusDisplayItem(String parentID, Callbacks callbacks, Context context, Instant timestamp){
		super(parentID, callbacks, context);
		this.timestamp=timestamp;
	}

	@Override
	public Type getType(){
		return Type.CATCH_UP_TIMESTAMP;
	}

	public static class Holder extends StatusDisplayItem.Holder<CatchUpTimestampStatusDisplayItem>{
		private final TextView time;

		public Holder(Context context, ViewGroup parent){
			super(context, R.layout.display_item_catch_up_timestamp, parent);
			time=findViewById(R.id.time);
		}

		@Override
		public void onBind(CatchUpTimestampStatusDisplayItem item){
			time.setText(FORMATTER.withZone(ZoneId.systemDefault()).format(item.timestamp));
		}

		@Override
		public void onClick(){}

		@Override
		public boolean isEnabled(){
			return false;
		}
	}
}
