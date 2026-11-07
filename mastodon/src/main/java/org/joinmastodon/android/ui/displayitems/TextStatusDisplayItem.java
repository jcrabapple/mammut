package org.joinmastodon.android.ui.displayitems;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.CustomEmojiHelper;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.ui.views.LinkedTextView;

import java.util.Locale;

import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.MovieDrawable;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class TextStatusDisplayItem extends StatusDisplayItem{
	private CharSequence text;
	private CustomEmojiHelper emojiHelper=new CustomEmojiHelper();
	private CharSequence translatedText;
	private CustomEmojiHelper translationEmojiHelper=new CustomEmojiHelper();
	public boolean textSelectable;
	public boolean largerFont;
	public final Status status;
	private final String accountID;

	public TextStatusDisplayItem(String parentID, CharSequence text, Callbacks callbacks, Context context, Status status, String accountID){
		super(parentID, callbacks, context);
		this.text=text;
		this.status=status;
		this.accountID=accountID;
		emojiHelper.setText(text);
	}

	@Override
	public Type getType(){
		return Type.TEXT;
	}

	@Override
	public int getImageCount(){
		return getCurrentEmojiHelper().getImageCount();
	}

	@Override
	public ImageLoaderRequest getImageRequest(int index){
		return getCurrentEmojiHelper().getImageRequest(index);
	}

	public void setTranslatedText(String text){
		Status statusForContent=status.getContentStatus();
		translatedText=HtmlParser.parse(text, statusForContent.emojis, statusForContent.mentions, statusForContent.tags, accountID, statusForContent, context);
		translationEmojiHelper.setText(translatedText);
	}

	private CustomEmojiHelper getCurrentEmojiHelper(){
		return status.translationState==Status.TranslationState.SHOWN ? translationEmojiHelper : emojiHelper;
	}

	public static class Holder extends StatusDisplayItem.Holder<TextStatusDisplayItem> implements ImageLoaderViewHolder{
		private final LinkedTextView text;
		private final ViewStub translationFooterStub;
		private View translationFooter;
		private TextView translationInfo;
		private Button translationShowOriginal;
		private ProgressBar translationProgress;

		// MAMMUT: phase 2 port from upstream master — collapsible long posts
		private final float textMaxHeight;
		private final LinearLayout.LayoutParams collapseParams, wrapParams;
		private final ViewGroup parent;
		private final TextView readMore;
		private final ScrollView textScrollView;

		public Holder(Activity activity, ViewGroup parent){
			super(activity, R.layout.display_item_text, parent);
			this.parent=parent;
			text=findViewById(R.id.text);
			translationFooterStub=findViewById(R.id.translation_info);
			textScrollView=findViewById(R.id.text_scroll_view);
			readMore=findViewById(R.id.read_more);
			textMaxHeight=activity.getResources().getDimension(R.dimen.text_max_height);
			float textCollapsedHeight=activity.getResources().getDimension(R.dimen.text_collapsed_height);
			collapseParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) textCollapsedHeight);
			wrapParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			readMore.setOnClickListener(v -> item.callbacks.onToggleExpanded(item.status, getItemID()));
		}

		@Override
		public void onBind(TextStatusDisplayItem item){
			if(item.status.translationState==Status.TranslationState.SHOWN){
				if(item.translatedText==null){
					item.setTranslatedText(item.status.translation.content);
				}
				text.setText(item.translatedText);
			}else{
				text.setText(item.text);
			}
			text.setTextIsSelectable(item.textSelectable);
			text.setInvalidateOnEveryFrame(false);
			itemView.setClickable(false);
			itemView.setPaddingRelative(V.dp(item.fullWidth ? 0 : 48), 0, 0, 0);
			text.setTextColor(UiUtils.getThemeColor(text.getContext(), R.attr.colorM3OnSurface));
			text.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.largerFont ? 18 : 16);
			updateTranslation(false);

			// MAMMUT: phase 2 port from upstream master
			readMore.setText(item.status.textExpanded ? R.string.sk_collapse : R.string.sk_expand);

			if(!GlobalUserPreferences.collapseLongPosts){
				textScrollView.setLayoutParams(wrapParams);
				readMore.setVisibility(View.GONE);
			}

			// incredibly ugly workaround for https://github.com/sk22/megalodon/issues/520
			// attempts to use OnPreDrawListener, OnGlobalLayoutListener and .post have failed -
			// the view didn't want to reliably update after calling .setVisibility etc :(
			int width=parent.getWidth()!=0 ? parent.getWidth() : item.fullWidth ? 4096 : 3072;

			text.measure(
					View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

			if(GlobalUserPreferences.collapseLongPosts && !item.status.textExpandable){
				boolean tooBig=text.getMeasuredHeight()>textMaxHeight;
				boolean expandable=tooBig && TextUtils.isEmpty(item.status.spoilerText);
				item.callbacks.onEnableExpandable(Holder.this, expandable);
			}

			boolean expandButtonShown=item.status.textExpandable && !item.status.textExpanded;
			readMore.setVisibility(expandButtonShown ? View.VISIBLE : View.GONE);
			textScrollView.setLayoutParams(item.status.textExpandable && !item.status.textExpanded ? collapseParams : wrapParams);
		}

		@Override
		public void setImage(int index, Drawable image){
			getEmojiHelper().setImageDrawable(index, image);
			text.invalidate();
			if(image instanceof Animatable){
				((Animatable) image).start();
				if(image instanceof MovieDrawable)
					text.setInvalidateOnEveryFrame(true);
			}
		}

		@Override
		public void clearImage(int index){
			getEmojiHelper().setImageDrawable(index, null);
			text.invalidate();
		}

		private CustomEmojiHelper getEmojiHelper(){
			return item.emojiHelper;
		}

		public void updateTranslation(boolean updateText){
			if(item.status==null)
				return;
			if(item.status.translationState==Status.TranslationState.HIDDEN){
				if(translationFooter!=null)
					translationFooter.setVisibility(View.GONE);
				if(updateText){
					text.setText(item.text);
				}
			}else{
				if(translationFooter==null){
					translationFooter=translationFooterStub.inflate();
					translationInfo=findViewById(R.id.translation_info_text);
					translationShowOriginal=findViewById(R.id.translation_show_original);
					translationProgress=findViewById(R.id.translation_progress);
					translationShowOriginal.setOnClickListener(v->item.callbacks.togglePostTranslation(item.status, item.parentID));
				}else{
					translationFooter.setVisibility(View.VISIBLE);
				}
				if(item.status.translationState==Status.TranslationState.SHOWN){
					translationProgress.setVisibility(View.GONE);
					translationInfo.setVisibility(View.VISIBLE);
					translationShowOriginal.setVisibility(View.VISIBLE);
					translationInfo.setText(translationInfo.getContext().getString(R.string.post_translated, Locale.forLanguageTag(item.status.translation.detectedSourceLanguage).getDisplayLanguage(), item.status.translation.provider));
					if(updateText){
						if(item.translatedText==null){
							item.setTranslatedText(item.status.translation.content);
						}
						text.setText(item.translatedText);
					}
				}else{ // LOADING
					translationProgress.setVisibility(View.VISIBLE);
					translationInfo.setVisibility(View.INVISIBLE);
					translationShowOriginal.setVisibility(View.INVISIBLE);
				}
			}
		}
	}
}
