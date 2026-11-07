package org.joinmastodon.android.ui.displayitems;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.Translation;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.PhotoLayoutHelper;
import org.joinmastodon.android.ui.drawables.SpoilerStripesDrawable;
import org.joinmastodon.android.ui.photoviewer.AltTextSheet;
import org.joinmastodon.android.ui.utils.MediaAttachmentViewController;
import org.joinmastodon.android.ui.views.FrameLayoutThatOnlyMeasuresFirstChild;
import org.joinmastodon.android.ui.views.MaxWidthFrameLayout;
import org.joinmastodon.android.ui.views.MediaGridLayout;
import org.joinmastodon.android.utils.TypedObjectPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import me.grishka.appkit.imageloader.ImageLoaderViewHolder;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

public class MediaGridStatusDisplayItem extends StatusDisplayItem{
	private static final String TAG="MediaGridDisplayItem";

	private final PhotoLayoutHelper.TiledLayoutResult tiledLayout;
	private final TypedObjectPool<GridItemType, MediaAttachmentViewController> viewPool;
	private final List<Attachment> attachments;
	private final Map<String, Pair<String, String>> translatedAttachments = new HashMap<>();
	private final ArrayList<ImageLoaderRequest> requests=new ArrayList<>();
	public final Status status;
	public boolean sensitiveRevealed;
	public String sensitiveTitle;
	// MAMMUT: F2 — true when the cover is a Blur filter cover rather than a sensitive-media cover,
	// so revealing it persists to Status.filterMediaRevealed instead of touching sensitive state
	public boolean filterBlur;

	public MediaGridStatusDisplayItem(String parentID, Callbacks callbacks, Context context, PhotoLayoutHelper.TiledLayoutResult tiledLayout, List<Attachment> attachments, Status status){
		super(parentID, callbacks, context);
		this.tiledLayout=tiledLayout;
		this.viewPool=callbacks.getAttachmentViewsPool();
		this.attachments=attachments;
		this.status=status;
		sensitiveRevealed=!status.sensitive;
		for(Attachment att:attachments){
			requests.add(new UrlImageLoaderRequest(switch(att.type){
				case IMAGE -> att.url;
				case VIDEO, GIFV -> att.previewUrl;
				default -> throw new IllegalStateException("Unexpected value: "+att.type);
			}, 1000, 1000));
		}
	}

	@Override
	public Type getType(){
		return Type.MEDIA_GRID;
	}

	@Override
	public int getImageCount(){
		return requests.size();
	}

	@Override
	public ImageLoaderRequest getImageRequest(int index){
		return requests.get(index);
	}

	public enum GridItemType{
		PHOTO,
		VIDEO,
		GIFV
	}

	public static class Holder extends StatusDisplayItem.Holder<MediaGridStatusDisplayItem> implements ImageLoaderViewHolder{
		private final Activity activity;
		private final ViewGroup recyclerParent;
		private final FrameLayout wrapper;
		private final LinearLayout content;
		// MAMMUT: G14 — the grid + overlays keep their exact original structure, just reparented
		// into this container so the caption track can hang below them as a sibling
		private final FrameLayout gridContainer;
		private final MediaGridLayout layout;
		private final LinearLayout captions;
		private final View.OnClickListener clickListener=this::onViewClick, altTextClickListener=this::onAltTextClick;
		private final ArrayList<MediaAttachmentViewController> controllers=new ArrayList<>();

		private final MaxWidthFrameLayout overlays;

		private final View sensitiveOverlay;
		private final LayerDrawable sensitiveOverlayBG;
		private static final ColorDrawable drawableForWhenThereIsNoBlurhash=new ColorDrawable(0xffffffff);
		private final TextView hideSensitiveButton;
		private final TextView sensitiveText;
		private boolean thereAreFailedImages;

		public Holder(Activity activity, ViewGroup parent){
			super(new FrameLayoutThatOnlyMeasuresFirstChild(activity));
			this.activity=activity;
			this.recyclerParent=parent;
			wrapper=(FrameLayout)itemView;
			content=new LinearLayout(activity);
			content.setOrientation(LinearLayout.VERTICAL);
			wrapper.addView(content);
			wrapper.setClipToPadding(false);

			gridContainer=new FrameLayout(activity);
			content.addView(gridContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			layout=new MediaGridLayout(activity);
			gridContainer.addView(layout);

			overlays=new MaxWidthFrameLayout(activity);
			overlays.setMaxWidth(V.dp(MediaGridLayout.MAX_WIDTH));
			gridContainer.addView(overlays, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

			// MAMMUT: alt_text_badge.xml is a container now, so this pill has its own layout
			hideSensitiveButton=(TextView) activity.getLayoutInflater().inflate(R.layout.media_grid_badge, overlays, false);
			hideSensitiveButton.setText(R.string.hide);
			FrameLayout.LayoutParams lp;
			overlays.addView(hideSensitiveButton, lp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, V.dp(24), Gravity.END | Gravity.BOTTOM));
			int margin=V.dp(8);
			lp.setMargins(margin, margin, margin, margin);

			activity.getLayoutInflater().inflate(R.layout.overlay_image_sensitive, overlays);
			sensitiveOverlay=findViewById(R.id.sensitive_overlay);
			sensitiveOverlayBG=(LayerDrawable) sensitiveOverlay.getBackground().mutate();
			sensitiveOverlayBG.setDrawableByLayerId(R.id.left_drawable, new SpoilerStripesDrawable(false));
			sensitiveOverlayBG.setDrawableByLayerId(R.id.right_drawable, new SpoilerStripesDrawable(true));
			sensitiveOverlay.setBackground(sensitiveOverlayBG);
			sensitiveOverlay.setOnClickListener(v->revealSensitive());
			sensitiveOverlay.setOutlineProvider(OutlineProviders.roundedRect(8));
			sensitiveOverlay.setClipToOutline(true);
			hideSensitiveButton.setOnClickListener(v->hideSensitive());

			sensitiveText=findViewById(R.id.sensitive_text);

			// MAMMUT: G14 — caption track container; stays GONE unless inline alt text is on and
			// at least one attachment has a description, so toggle-off is byte-identical
			captions=new LinearLayout(activity);
			captions.setOrientation(LinearLayout.VERTICAL);
			captions.setVisibility(View.GONE);
			content.addView(captions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}

		@Override
		public void onBind(MediaGridStatusDisplayItem item){
			thereAreFailedImages=false;
			wrapper.setPaddingRelative(V.dp(item.fullWidth ? 16 : 64), 0, V.dp(16), V.dp(8));

			layout.setTiledLayout(item.tiledLayout);
			for(MediaAttachmentViewController c:controllers){
				item.viewPool.reuse(c.type, c);
			}
			layout.removeAllViews();
			controllers.clear();
			int i=0;
			for(Attachment att:item.attachments){
				MediaAttachmentViewController c=item.viewPool.obtain(switch(att.type){
					case IMAGE -> GridItemType.PHOTO;
					case VIDEO -> GridItemType.VIDEO;
					case GIFV -> GridItemType.GIFV;
					default -> throw new IllegalStateException("Unexpected value: "+att.type);
				});
				if(c.view.getLayoutParams()==null)
					c.view.setLayoutParams(new MediaGridLayout.LayoutParams(item.tiledLayout.tiles[i]));
				else
					((MediaGridLayout.LayoutParams) c.view.getLayoutParams()).tile=item.tiledLayout.tiles[i];
				layout.addView(c.view);
				c.view.setOnClickListener(clickListener);
				c.view.setTag(i);
				if(c.altButton!=null){
					c.altButton.setOnClickListener(altTextClickListener);
					c.altButton.setTag(i);
					c.altButton.setAlpha(1f);
				}
				// MAMMUT: phase 2 port from upstream master
				if(c.noAltButton!=null){
					c.noAltButton.setOnClickListener(altTextClickListener);
					c.noAltButton.setTag(i);
					c.noAltButton.setAlpha(1f);
				}
				controllers.add(c);

				if (item.status.translation!=null && item.status.translation.mediaAttachments!=null){
					if(item.status.translationState==Status.TranslationState.SHOWN){
						if(!item.translatedAttachments.containsKey(att.id)){
							Optional<Translation.MediaAttachment> translatedAttachment=Arrays.stream(item.status.translation.mediaAttachments).filter(mediaAttachment->mediaAttachment.id.equals(att.id)).findFirst();
							translatedAttachment.ifPresent(mediaAttachment->{
								item.translatedAttachments.put(mediaAttachment.id, new Pair<>(att.description, mediaAttachment.description));
								att.description=mediaAttachment.description;
							});
						}else{
							//SAFETY: must be non-null, as we check if the map contains the attachment before
							att.description=Objects.requireNonNull(item.translatedAttachments.get(att.id)).second;
						}
					}else{
						if (item.translatedAttachments.containsKey(att.id)) {
							att.description=Objects.requireNonNull(item.translatedAttachments.get(att.id)).first;
						}
					}
				}
				c.bind(att, item.status, GlobalUserPreferences.showInlineAltText);
				i++;
			}

			if(!item.sensitiveRevealed){
				sensitiveOverlay.setVisibility(View.VISIBLE);
				layout.setVisibility(View.INVISIBLE);
				updateBlurhashInSensitiveOverlay();
			}else{
				sensitiveOverlay.setVisibility(View.GONE);
				layout.setVisibility(View.VISIBLE);
			}
			hideSensitiveButton.setVisibility(item.status.sensitive ? View.VISIBLE : View.GONE);
			if(!TextUtils.isEmpty(item.sensitiveTitle))
				sensitiveText.setText(item.sensitiveTitle);
			else
				sensitiveText.setText(R.string.sensitive_content_explain);
			bindCaptionTrack();
		}

		@Override
		public void setImage(int index, Drawable drawable){
			if(index<controllers.size())
				controllers.get(index).setImage(drawable);
		}

		@Override
		public void clearImage(int index){
			if(index<controllers.size())
				controllers.get(index).clearImage();
		}

		@Override
		public void onImageLoadingFailed(int index, Throwable error){
			if(index<controllers.size()){
				controllers.get(index).showFailedOverlay();
				thereAreFailedImages=true;
			}
		}

		private void onViewClick(View v){
			int index=(Integer)v.getTag();
			item.callbacks.openPhotoViewer(item.parentID, item.status, index, this);
			if(thereAreFailedImages){
				for(MediaAttachmentViewController controller:controllers){
					if(controller.isFailedOverlayShown()){
						controller.clearImage();
					}
				}
				item.callbacks.retryFailedImages();
			}
		}

		private void onAltTextClick(View v){
			int index=(Integer)v.getTag();
			Attachment att=item.attachments.get(index);
			new AltTextSheet(v.getContext(), att).show();
		}

		public MediaAttachmentViewController getViewController(int index){
			return controllers.get(index);
		}

		public void setClipChildren(boolean clip){
			layout.setClipChildren(clip);
			gridContainer.setClipChildren(clip);
			wrapper.setClipChildren(clip);
		}

		private void updateBlurhashInSensitiveOverlay(){
			Drawable d=item.attachments.get(0).blurhashPlaceholder;
			sensitiveOverlayBG.setDrawableByLayerId(R.id.blurhash, d==null ? drawableForWhenThereIsNoBlurhash : d.mutate());
		}

		private void revealSensitive(){
			if(item.sensitiveRevealed)
				return;
			item.sensitiveRevealed=true;
			if(item.filterBlur)
				item.status.filterMediaRevealed=true;
			V.setVisibilityAnimated(sensitiveOverlay, View.GONE);
			layout.setVisibility(View.VISIBLE);
			bindCaptionTrack();
		}

		private void hideSensitive(){
			if(!item.sensitiveRevealed)
				return;
			updateBlurhashInSensitiveOverlay();
			item.sensitiveRevealed=false;
			captions.setVisibility(View.GONE);
			V.setVisibilityAnimated(sensitiveOverlay, View.VISIBLE, ()->layout.setVisibility(View.INVISIBLE));
		}

		// MAMMUT: G14 — "The Caption Track": one full-width caption band per described attachment,
		// clamped to two lines with an in-place expand affordance. Rebuilt wholesale on every bind
		// so a recycled row can never keep a caption from a previous post.
		private void bindCaptionTrack(){
			captions.removeAllViews();
			if(GlobalUserPreferences.showInlineAltText && item.sensitiveRevealed){
				for(Attachment att:item.attachments){
					if(TextUtils.isEmpty(att.description))
						continue;
					View band=activity.getLayoutInflater().inflate(R.layout.media_caption_band, captions, false);
					TextView captionText=band.findViewById(R.id.caption_text);
					TextView toggle=band.findViewById(R.id.caption_toggle);
					String attachmentID=att.id;
					boolean expanded=attachmentID!=null && item.status.expandedInlineAltAttachments.contains(attachmentID);
					captionText.setText(att.description);
					applyCaptionExpansion(captionText, toggle, expanded);
					boolean truncated=isCaptionTruncated(captionText, att.description, 2);
					// Never truncate silently: the affordance shows whenever more text exists
					toggle.setVisibility(expanded || truncated ? View.VISIBLE : View.GONE);
					toggle.setOnClickListener(v->{
						boolean nowExpanded=attachmentID==null || !item.status.expandedInlineAltAttachments.contains(attachmentID);
						if(attachmentID!=null){
							if(nowExpanded)
								item.status.expandedInlineAltAttachments.add(attachmentID);
							else
								item.status.expandedInlineAltAttachments.remove(attachmentID);
						}
						applyCaptionExpansion(captionText, toggle, nowExpanded);
						toggle.setVisibility(nowExpanded || isCaptionTruncated(captionText, att.description, 2) ? View.VISIBLE : View.GONE);
					});
					captions.addView(band);
				}
			}
			captions.setVisibility(captions.getChildCount()>0 ? View.VISIBLE : View.GONE);
		}

		private void applyCaptionExpansion(TextView captionText, TextView toggle, boolean expanded){
			captionText.setMaxLines(expanded ? Integer.MAX_VALUE : 2);
			captionText.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
			toggle.setText(expanded ? R.string.sk_collapse : R.string.sk_read_full_description);
		}

		// Bind-time measure, same approach as TextStatusDisplayItem's collapseLongPosts check:
		// lay the text out at the caption's real width and compare line counts. Wraps (never
		// clips) under font scaling because the band is wrap_content height in both states.
		private boolean isCaptionTruncated(TextView captionText, String text, int maxLines){
			int width=recyclerParent.getWidth()!=0 ? recyclerParent.getWidth() : item.fullWidth ? 4096 : 3072;
			width-=wrapper.getPaddingStart()+wrapper.getPaddingEnd();
			if(width<=0)
				return false;
			StaticLayout layout=StaticLayout.Builder.obtain(text, 0, text.length(), captionText.getPaint(), width)
					.setAlignment(Layout.Alignment.ALIGN_NORMAL)
					.setLineSpacing(captionText.getLineSpacingExtra(), captionText.getLineSpacingMultiplier())
					.setIncludePad(false)
					.build();
			return layout.getLineCount()>maxLines;
		}

		public MediaGridLayout getLayout(){
			return layout;
		}

		public View getSensitiveOverlay(){
			return sensitiveOverlay;
		}
	}
}
