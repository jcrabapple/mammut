package org.joinmastodon.android.ui.displayitems;

import static org.joinmastodon.android.MastodonApp.context;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.BaseStatusListFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Emoji;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.StatusPrivacy;
import org.joinmastodon.android.ui.text.AvatarSpan;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.text.SpacerSpan;
import org.joinmastodon.android.ui.utils.CustomEmojiHelper;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class ReblogOrReplyLineCustomStatusDisplayItem extends StatusDisplayItem{
	private CharSequence text;
	@DrawableRes
	private int icon;
	private StatusPrivacy visibility;
	@DrawableRes
	private int iconEnd;
	private CustomEmojiHelper emojiHelper=new CustomEmojiHelper();
	private View.OnClickListener handleClick;
	public boolean needBottomPadding;
	ReblogOrReplyLineCustomStatusDisplayItem extra;
	CharSequence fullText;
	// MAMMUT: Address line (docs/ADDRESS-LINE-SPEC.md §2, §4, §7) — the six-state
	// thread grammar renders in this row type rather than a look-alike. The inset
	// and colour are decided when the display items are built, never at draw time.
	/** True for the Conversation address row, so ThreadFragment can re-resolve it. */
	public boolean addressLine;
	/** The un-spanned visible wording, for change detection on re-resolve. */
	public String addressVisible;
	/** The accessibility wording, for change detection on re-resolve. */
	public String addressAccessibility;
	/** Left itemView padding in px, -1 keeps the layout's own padding (timeline rows). */
	public int leadingInsetPx=-1;
	/** Theme attr for the text colour, resolved at bind time; 0 keeps the layout's ?colorM3OnSurface. */
	public int textColorAttr;

	public ReblogOrReplyLineCustomStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence text, List<Emoji> emojis, @DrawableRes int icon) {
		this(parentID, callbacks, context, text, emojis, icon, null, null, text, null);
	}

	public ReblogOrReplyLineCustomStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence text, List<Emoji> emojis, @DrawableRes int icon, StatusPrivacy visibility, @Nullable View.OnClickListener handleClick) {
		this(parentID, callbacks, context, text, emojis, icon, visibility, handleClick, text, null);
	}

		public ReblogOrReplyLineCustomStatusDisplayItem(String parentID, Callbacks callbacks, Context context, CharSequence text, List<Emoji> emojis, @DrawableRes int icon, StatusPrivacy visibility, @Nullable View.OnClickListener handleClick, CharSequence fullText, Account account) {
		super(parentID, callbacks, context);
		this.text=buildSpanned(text, emojis, account);
		emojiHelper.setText(this.text);
		this.fullText=fullText;
		this.icon=icon;
//		this.status=status;
		this.handleClick=handleClick;
		if(context!=null){ // the thread builds its first items before the activity exists
			TypedValue outValue = new TypedValue();
			context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
		}
		updateVisibility(visibility);
	}

	/**
	 * MAMMUT address line: rebuild the row's text in place when the conversation
	 * changed under it (parent resolved, chain became eligible). Called with the
	 * display items, never from a draw or scroll callback (§7).
	 */
	public void updateAddressLine(CharSequence text, List<Emoji> emojis, CharSequence fullText){
		this.text=buildSpanned(text, emojis, null);
		emojiHelper.setText(this.text);
		this.fullText=fullText;
		this.addressVisible=text.toString();
		this.addressAccessibility=fullText.toString();
	}

	private static CharSequence buildSpanned(CharSequence text, List<Emoji> emojis, @Nullable Account account){
		SpannableStringBuilder ssb=new SpannableStringBuilder(text);
		if(GlobalUserPreferences.customEmojiInNames && emojis != null)
			HtmlParser.parseCustomEmoji(ssb, emojis);
		if(account!=null){
			//this is fine, since the display name is surround by '\u2068' and '\u2069'
			int nameLoc=text.toString().indexOf(account.getDisplayName());
			if(nameLoc!=-1&&ssb.length()>=nameLoc){
				//add temp chars for span replacement, length should be the same as the amount of spans replacing below
				ssb.insert(nameLoc, "   ");
				ssb.setSpan(new SpacerSpan(15, 20), nameLoc+1, nameLoc+2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				ssb.setSpan(new AvatarSpan(account), nameLoc+1, nameLoc+2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				ssb.setSpan(new SpacerSpan(15, 20), nameLoc+2, nameLoc+3, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
			}
		}
		return ssb;
	}

	public void updateVisibility(StatusPrivacy visibility) {
		this.visibility = visibility;
		this.iconEnd = visibility != null ? switch (visibility) {
			case PUBLIC -> R.drawable.ic_fluent_earth_20sp_regular;
			case UNLISTED -> R.drawable.ic_fluent_lock_open_20sp_regular;
			case PRIVATE -> R.drawable.ic_fluent_lock_closed_20sp_filled;
			default -> 0;
		} : 0;
	}

	@Override
	public Type getType(){
		return Type.REBLOG_OR_REPLY_LINE;
	}

	@Override
	public int getImageCount(){
		return emojiHelper.getImageCount() + (extra!=null ? extra.emojiHelper.getImageCount() : 0);
	}

	@Override
	public ImageLoaderRequest getImageRequest(int index){
		int firstHelperCount=emojiHelper.getImageCount();
		CustomEmojiHelper helper=index<firstHelperCount ? emojiHelper : extra.emojiHelper;
		return helper.getImageRequest(firstHelperCount>0 ? index%firstHelperCount : index);
	}

	public static class Holder extends StatusDisplayItem.Holder<ReblogOrReplyLineCustomStatusDisplayItem> implements ImageLoaderViewHolder{
		private final TextView text, extraText;
		private final View separator;

		public Holder(Activity activity, ViewGroup parent){
			super(activity, R.layout.display_item_reblog_or_reply_line_custom, parent);
			text=findViewById(R.id.text);
			extraText=findViewById(R.id.extra_text);
			separator=findViewById(R.id.separator);
		}

		private void bindLine(ReblogOrReplyLineCustomStatusDisplayItem item, TextView text) {
			text.setText(item.text);
			Context ctx = itemView.getContext();
			// Fully deterministic under recycling: address rows carry an attr,
			// everything else returns to the layout's ?colorM3OnSurface.
			text.setTextColor(UiUtils.getThemeColor(ctx, item.textColorAttr!=0 ? item.textColorAttr : R.attr.colorM3OnSurface));
			text.setCompoundDrawablesRelativeWithIntrinsicBounds(item.icon, 0, item.iconEnd, 0);
			text.setOnClickListener(item.handleClick);
			text.setEnabled(!item.inset && item.handleClick != null);
			text.setClickable(!item.inset && item.handleClick != null);
			int visibilityText = item.visibility != null ? switch (item.visibility) {
				case PUBLIC -> R.string.visibility_public;
				case UNLISTED -> R.string.sk_visibility_unlisted;
				case PRIVATE -> R.string.visibility_followers_only;
				case LOCAL -> R.string.sk_local_only;
				default -> 0;
			} : 0;
			String visibilityDescription=visibilityText!=0 ? " (" + ctx.getString(visibilityText) + ")" : null;
			text.setContentDescription(item.fullText==null && visibilityDescription==null ? null :
					(item.fullText!=null ? item.fullText : item.text)
							+ (visibilityDescription!=null ? visibilityDescription : ""));
			if(Build.VERSION.SDK_INT<Build.VERSION_CODES.N)
				UiUtils.fixCompoundDrawableTintOnAndroid6(text);
			text.setCompoundDrawableTintList(text.getTextColors());
		}

		@Override
		public void onBind(ReblogOrReplyLineCustomStatusDisplayItem item){
			// MAMMUT address line (§2): the row inset is the layout's own content
			// inset — 16 dp from the edge in full width, 16 dp inside the item
			// otherwise — decided at bind time from the precomputed leadingInsetPx,
			// never by insetting the body or reserving an outRect (§7). Other rows
			// keep the layout's own 16 dp paddingHorizontal.
			itemView.setPaddingRelative(item.leadingInsetPx>=0 ? item.leadingInsetPx : V.dp(16), itemView.getPaddingTop(), itemView.getPaddingEnd(), itemView.getPaddingBottom());
			bindLine(item, text);
			if (item.extra != null) bindLine(item.extra, extraText);
			extraText.setVisibility(item.extra == null ? View.GONE : View.VISIBLE);
			separator.setVisibility(item.extra == null ? View.GONE : View.VISIBLE);
			itemView.setPadding(itemView.getPaddingLeft(), itemView.getPaddingTop(), itemView.getPaddingRight(), item.needBottomPadding ? V.dp(16) : 0);
		}

		@Override
		public void setImage(int index, Drawable image){
			int firstHelperCount=item.emojiHelper.getImageCount();
			CustomEmojiHelper helper=index<firstHelperCount ? item.emojiHelper : item.extra.emojiHelper;
			helper.setImageDrawable(firstHelperCount>0 ? index%firstHelperCount : index, image);
			text.setText(text.getText());
			extraText.setText(extraText.getText());
		}

		@Override
		public void clearImage(int index){
			setImage(index, null);
		}
	}
}
