package org.joinmastodon.android.ui.viewcontrollers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.api.MastodonErrorResponse;
import org.joinmastodon.android.api.UploadStaging;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.ProgressListener;
import org.joinmastodon.android.api.requests.statuses.CreateStatus;
import org.joinmastodon.android.api.requests.statuses.GetAttachmentByID;
import org.joinmastodon.android.api.requests.statuses.UpdateAttachment;
import org.joinmastodon.android.api.requests.statuses.UploadAttachment;
import org.joinmastodon.android.fragments.ComposeFragment;
import org.joinmastodon.android.fragments.ComposeImageDescriptionFragment;
import org.joinmastodon.android.drafts.DraftAttachment;
import org.joinmastodon.android.drafts.DraftStore;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.drawables.EmptyDrawable;
import org.joinmastodon.android.ui.utils.ImageDescriptionMetadata;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.ui.views.ReorderableLinearLayout;
import org.joinmastodon.android.utils.TransferSpeedTracker;
import org.parceler.Parcel;
import org.parceler.Parcels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class ComposeMediaViewController{
	private static final int MAX_ATTACHMENTS=4;
	private static final String TAG="ComposeMediaViewControl";
	
	private final ComposeFragment fragment;
	
	private ReorderableLinearLayout attachmentsView;
	private HorizontalScrollView attachmentsScroller;

	// MAMMUT: The Caption Track (§3/§4) — inline description rows beneath the strip,
	// keyed by the DraftMediaAttachment they edit so removal/reorder never mispairs text.
	private LinearLayout attachmentDescriptions;
	private final LinkedHashMap<DraftMediaAttachment, DescriptionEditor> descriptionEditors=new LinkedHashMap<>();
	private boolean descriptionBlockHiddenByPoll;

	private ArrayList<DraftMediaAttachment> attachments=new ArrayList<>();
	private boolean attachmentsErrorShowing;

	public ComposeMediaViewController(ComposeFragment fragment){
		this.fragment=fragment;
	}

	public void setView(View view, Bundle savedInstanceState){
		attachmentsView=view.findViewById(R.id.attachments);
		attachmentsScroller=view.findViewById(R.id.attachments_scroller);
		attachmentDescriptions=view.findViewById(R.id.attachment_descriptions);
		attachmentsView.setDividerDrawable(new EmptyDrawable(V.dp(8), 0));
		attachmentsView.setDragListener(new AttachmentDragListener());
		attachmentsView.setMoveInBothDimensions(true);

		if(!fragment.getWasDetached() && savedInstanceState!=null && savedInstanceState.containsKey("attachments")){
			ArrayList<Parcelable> serializedAttachments=savedInstanceState.getParcelableArrayList("attachments");
			for(Parcelable a:serializedAttachments){
				DraftMediaAttachment att=Parcels.unwrap(a);
				attachmentsView.addView(createMediaAttachmentView(att));
				attachments.add(att);
			}
			attachmentsScroller.setVisibility(View.VISIBLE);
			updateMediaAttachmentsLayout();
		}else if(!attachments.isEmpty()){
			attachmentsScroller.setVisibility(View.VISIBLE);
			for(DraftMediaAttachment att:attachments){
				attachmentsView.addView(createMediaAttachmentView(att));
			}
			updateMediaAttachmentsLayout();
		}
		rebuildDescriptionEditors();
	}

	public void onViewCreated(Bundle savedInstanceState){
		if(savedInstanceState==null && !fragment.editingStatus.mediaAttachments.isEmpty()){
			attachmentsScroller.setVisibility(View.VISIBLE);
			for(Attachment att:fragment.editingStatus.mediaAttachments){
				DraftMediaAttachment da=new DraftMediaAttachment();
				da.serverAttachment=att;
				da.description=att.description;
				da.uri=att.previewUrl!=null ? Uri.parse(att.previewUrl) : null;
				da.state=AttachmentUploadState.DONE;
				attachmentsView.addView(createMediaAttachmentView(da));
				attachments.add(da);
			}
			updateMediaAttachmentsLayout();
			rebuildDescriptionEditors();
		}
	}

	public boolean addMediaAttachment(Uri uri, String description){
		if(getMediaAttachmentsCount()==MAX_ATTACHMENTS){
			showMediaAttachmentError(fragment.getResources().getQuantityString(R.plurals.cant_add_more_than_x_attachments, MAX_ATTACHMENTS, MAX_ATTACHMENTS));
			return false;
		}
		String type=fragment.getActivity().getContentResolver().getType(uri);
		int size;
		try(Cursor cursor=MastodonApp.context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)){
			cursor.moveToFirst();
			size=cursor.getInt(0);
		}catch(Exception x){
			Log.w("ComposeFragment", x);
			return false;
		}
		Instance instance=fragment.instance;
		if(instance!=null && instance.configuration!=null && instance.configuration.mediaAttachments!=null){
			if(instance.configuration.mediaAttachments.supportedMimeTypes!=null && !instance.configuration.mediaAttachments.supportedMimeTypes.contains(type)){
				showMediaAttachmentError(fragment.getString(R.string.media_attachment_unsupported_type, UiUtils.getFileName(uri)));
				return false;
			}
			if(!type.startsWith("image/")){
				int sizeLimit=instance.configuration.mediaAttachments.videoSizeLimit;
				if(size>sizeLimit){
					float mb=sizeLimit/(float) (1024*1024);
					String sMb=String.format(Locale.getDefault(), mb%1f==0f ? "%.0f" : "%.2f", mb);
					showMediaAttachmentError(fragment.getString(R.string.media_attachment_too_big, UiUtils.getFileName(uri), sMb));
					return false;
				}
			}
		}
		DraftMediaAttachment draft=new DraftMediaAttachment();
		draft.uri=uri;
		draft.mimeType=type;
		draft.description=description;
		draft.fileSize=size;

		UiUtils.beginLayoutTransition(attachmentsScroller);
		attachmentsView.addView(createMediaAttachmentView(draft), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
		attachments.add(draft);
		attachmentsScroller.setVisibility(View.VISIBLE);
		updateMediaAttachmentsLayout();
		rebuildDescriptionEditors();
//		draft.setOverlayVisible(true, false);

		// MAMMUT: The Caption Track (§2) — prefill an empty image description from the
		// file's own metadata. The read is async; if the author has typed by the time it
		// finishes, the prefill is dropped silently.
		if(TextUtils.isEmpty(draft.description) && isImage(draft)){
			prefillDescriptionFromMetadata(draft);
		}

		if(!areThereAnyUploadingAttachments()){
			uploadNextQueuedAttachment();
		}
		fragment.updatePublishButtonState();
		fragment.updateMediaPollStates();
		return true;
	}

	private void updateMediaAttachmentsLayout(){
		int newWidth=attachments.size()>2 ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
		if(newWidth!=attachmentsView.getLayoutParams().width){
			attachmentsView.getLayoutParams().width=newWidth;
			attachmentsScroller.requestLayout();
		}
		for(DraftMediaAttachment att:attachments){
			LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams) att.view.getLayoutParams();
			if(attachments.size()<3){
				lp.width=0;
				lp.weight=1f;
			}else{
				lp.width=V.dp(200);
				lp.weight=0f;
			}
		}
	}

	private void showMediaAttachmentError(String text){
		if(!attachmentsErrorShowing){
			Toast.makeText(fragment.getActivity(), text, Toast.LENGTH_SHORT).show();
			attachmentsErrorShowing=true;
			attachmentsView.postDelayed(()->attachmentsErrorShowing=false, 2000);
		}
	}

	private View createMediaAttachmentView(DraftMediaAttachment draft){
		View thumb=fragment.getActivity().getLayoutInflater().inflate(R.layout.compose_media_thumb, attachmentsView, false);
		ImageView img=thumb.findViewById(R.id.thumb);
		if(draft.serverAttachment!=null){
			if(draft.serverAttachment.previewUrl!=null)
				ViewImageLoader.load(img, draft.serverAttachment.blurhashPlaceholder, new UrlImageLoaderRequest(draft.serverAttachment.previewUrl, V.dp(250), V.dp(250)));
		}else{
			if(draft.mimeType.startsWith("image/")){
				ViewImageLoader.load(img, null, new UrlImageLoaderRequest(draft.uri, V.dp(250), V.dp(250)));
			}else if(draft.mimeType.startsWith("video/")){
				loadVideoThumbIntoView(img, draft.uri);
			}
		}

		draft.view=thumb;
		draft.imageView=img;
		draft.progressBar=thumb.findViewById(R.id.progress);
		draft.titleView=thumb.findViewById(R.id.title);
		draft.subtitleView=thumb.findViewById(R.id.subtitle);
		draft.removeButton=thumb.findViewById(R.id.delete);
		draft.editButton=thumb.findViewById(R.id.edit);
		draft.dragLayer=thumb.findViewById(R.id.drag_layer);

		draft.removeButton.setTag(draft);
		draft.removeButton.setOnClickListener(this::onRemoveMediaAttachmentClick);
		draft.editButton.setTag(draft);

		thumb.setOutlineProvider(OutlineProviders.roundedRect(12));
		thumb.setClipToOutline(true);
		img.setOutlineProvider(OutlineProviders.roundedRect(12));
		img.setClipToOutline(true);

		thumb.setBackgroundColor(UiUtils.getThemeColor(fragment.getActivity(), R.attr.colorM3Surface));
		thumb.setOnLongClickListener(v->{
			if(!v.hasTransientState() && attachments.size()>1){
				attachmentsView.startDragging(v);
				return true;
			}
			return false;
		});
		thumb.setTag(draft);


		if(draft.fileSize>0){
			int subtitleRes=switch(Objects.requireNonNullElse(draft.mimeType, "").split("/")[0]){
				case "image" -> R.string.attachment_description_image;
				case "video" -> R.string.attachment_description_video;
				case "audio" -> R.string.attachment_description_audio;
				default -> R.string.attachment_description_unknown;
			};
			draft.subtitleView.setText(fragment.getString(subtitleRes, UiUtils.formatFileSize(fragment.getActivity(), draft.fileSize, true)));
		}else if(draft.serverAttachment!=null){
			int subtitleRes=switch(draft.serverAttachment.type){
				case IMAGE -> R.string.attachment_type_image;
				case VIDEO -> R.string.attachment_type_video;
				case GIFV -> R.string.attachment_type_gif;
				case AUDIO -> R.string.attachment_type_audio;
				case UNKNOWN -> R.string.attachment_type_unknown;
			};
			draft.subtitleView.setText(subtitleRes);
		}
		draft.titleView.setText(fragment.getString(R.string.attachment_x_percent_uploaded, 0));
		draft.removeButton.setImageResource(R.drawable.ic_baseline_close_24);

		if(draft.state==AttachmentUploadState.ERROR){
			draft.titleView.setText(R.string.upload_failed);
			draft.editButton.setImageResource(R.drawable.ic_restart_alt_24px);
			draft.editButton.setOnClickListener(this::onRetryOrCancelMediaUploadClick);
			draft.progressBar.setVisibility(View.GONE);
			draft.setUseErrorColors(true);
		}else if(draft.state==AttachmentUploadState.DONE){
			draft.setDescriptionToTitle();
			draft.progressBar.setVisibility(View.GONE);
			if(isImage(draft)){
				// MAMMUT: The Caption Track (§3) — images have one editor: the inline field.
				// The edit button is hidden; the ALT chip focuses the field. Video/audio
				// keep the edit button and the description dialog.
				draft.editButton.setVisibility(View.GONE);
			}else{
				draft.editButton.setOnClickListener(this::onEditMediaDescriptionClick);
			}
		}else{
			draft.editButton.setVisibility(View.GONE);
			draft.removeButton.setImageResource(R.drawable.ic_baseline_close_24);
			if(draft.state==AttachmentUploadState.PROCESSING){
				draft.titleView.setText(R.string.upload_processing);
			}else{
				draft.titleView.setText(fragment.getString(R.string.attachment_x_percent_uploaded, 0));
			}
		}

		if(isImage(draft)){
			// MAMMUT: The Caption Track (§3, §5) — the ALT status chip focuses the inline field
			draft.titleView.setOnClickListener(v->focusDescriptionEditor(draft));
		}

		return thumb;
	}

	public void addFakeMediaAttachment(Uri uri, String description){
		DraftMediaAttachment draft=new DraftMediaAttachment();
		draft.uri=uri;
		draft.description=description;
		draft.mimeType="image/jpeg";
		draft.fileSize=2473276;
		draft.state=AttachmentUploadState.PROCESSING;
		attachmentsView.addView(createMediaAttachmentView(draft));
		attachments.add(draft);
		attachmentsScroller.setVisibility(View.VISIBLE);
		updateMediaAttachmentsLayout();
		rebuildDescriptionEditors();
		finishMediaAttachmentUpload(draft);
	}

	private void uploadMediaAttachment(DraftMediaAttachment attachment){
		if(areThereAnyUploadingAttachments()){
			throw new IllegalStateException("there is already an attachment being uploaded");
		}
		attachment.state=AttachmentUploadState.UPLOADING;
		attachment.progressBar.setVisibility(View.VISIBLE);
		int maxSize=0;
		String contentType=fragment.getActivity().getContentResolver().getType(attachment.uri);
		if(contentType!=null && contentType.startsWith("image/")){
			Instance instance=fragment.instance;
			if(instance.configuration!=null && instance.configuration.mediaAttachments!=null && instance.configuration.mediaAttachments.imageMatrixLimit>0){
				maxSize=instance.configuration.mediaAttachments.imageMatrixLimit;
			}else{
				maxSize=2_073_600;
			}
		}
		attachment.progressBar.setProgress(0);
		attachment.speedTracker.reset();
		attachment.speedTracker.addSample(0);
		stageAndUpload(attachment, maxSize, contentType);
	}

	private void onRemoveMediaAttachmentClick(View v){
		DraftMediaAttachment att=(DraftMediaAttachment) v.getTag();
		if(att.isUploadingOrProcessing())
			att.cancelUpload();
		attachments.remove(att);
		releaseStagedUpload(att);
		if(!areThereAnyUploadingAttachments())
			uploadNextQueuedAttachment();
		if(!attachments.isEmpty())
			UiUtils.beginLayoutTransition(attachmentsScroller);
		attachmentsView.removeView(att.view);
		rebuildDescriptionEditors();
		if(getMediaAttachmentsCount()==0){
			attachmentsScroller.setVisibility(View.GONE);
		}else{
			updateMediaAttachmentsLayout();
		}
		fragment.updatePublishButtonState();
		fragment.updateMediaPollStates();
	}

	private void onRetryOrCancelMediaUploadClick(View v){
		DraftMediaAttachment att=(DraftMediaAttachment) v.getTag();
		if(att.state==AttachmentUploadState.ERROR){
//			att.retryButton.setImageResource(R.drawable.ic_fluent_dismiss_24_filled);
//			att.retryButton.setContentDescription(fragment.getString(R.string.cancel));
			V.setVisibilityAnimated(att.progressBar, View.VISIBLE);
			V.setVisibilityAnimated(att.editButton, View.GONE);
			att.titleView.setText(fragment.getString(R.string.attachment_x_percent_uploaded, 0));
			att.state=AttachmentUploadState.QUEUED;
			att.setUseErrorColors(false);
			if(!areThereAnyUploadingAttachments()){
				uploadNextQueuedAttachment();
			}
		}else{
			onRemoveMediaAttachmentClick(v);
		}
	}

	private void loadVideoThumbIntoView(ImageView target, Uri uri){
		MastodonAPIController.runInBackground(()->{
			Context context=fragment.getActivity();
			if(context==null)
				return;
			try{
				MediaMetadataRetriever mmr=new MediaMetadataRetriever();
				mmr.setDataSource(context, uri);
				Bitmap frame=mmr.getFrameAtTime(3_000_000);
				mmr.release();
				int size=Math.max(frame.getWidth(), frame.getHeight());
				int maxSize=V.dp(250);
				if(size>maxSize){
					float factor=maxSize/(float)size;
					frame=Bitmap.createScaledBitmap(frame, Math.round(frame.getWidth()*factor), Math.round(frame.getHeight()*factor), true);
				}
				Bitmap finalFrame=frame;
				target.post(()->target.setImageBitmap(finalFrame));
			}catch(Exception x){
				Log.w(TAG, "loadVideoThumbIntoView: error getting video frame", x);
			}
		});
	}

	private void pollForMediaAttachmentProcessing(DraftMediaAttachment attachment){
		attachment.processingPollingRequest=(GetAttachmentByID) new GetAttachmentByID(attachment.serverAttachment.id)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Attachment result){
						attachment.processingPollingRequest=null;
						if(!TextUtils.isEmpty(result.url)){
							attachment.processingPollingRunnable=null;
							attachment.serverAttachment=result;
							finishMediaAttachmentUpload(attachment);
						}else if(fragment.getActivity()!=null){
							UiUtils.runOnUiThread(attachment.processingPollingRunnable, 1000);
						}
					}

					@Override
					public void onError(ErrorResponse error){
						attachment.processingPollingRequest=null;
						if(fragment.getActivity()!=null)
							UiUtils.runOnUiThread(attachment.processingPollingRunnable, 1000);
					}
				})
				.exec(fragment.getAccountID());
	}

	private void finishMediaAttachmentUpload(DraftMediaAttachment attachment){
		if(attachment.state!=AttachmentUploadState.PROCESSING && attachment.state!=AttachmentUploadState.UPLOADING)
			throw new IllegalStateException("Unexpected state "+attachment.state);
		attachment.uploadRequest=null;
		attachment.state=AttachmentUploadState.DONE;
		attachment.editButton.setImageResource(R.drawable.ic_edit_24px);
		attachment.removeButton.setImageResource(R.drawable.ic_delete_24px);
		if(isImage(attachment)){
			// MAMMUT: The Caption Track (§3) — one editor for images: the inline field
			attachment.editButton.setVisibility(View.GONE);
		}else{
			attachment.editButton.setOnClickListener(this::onEditMediaDescriptionClick);
			V.setVisibilityAnimated(attachment.editButton, View.VISIBLE);
		}
		V.setVisibilityAnimated(attachment.progressBar, View.GONE);
		attachment.setDescriptionToTitle();
		if(!areThereAnyUploadingAttachments())
			uploadNextQueuedAttachment();
		fragment.updatePublishButtonState();
	}

	private void uploadNextQueuedAttachment(){
		for(DraftMediaAttachment att:attachments){
			if(att.state==AttachmentUploadState.QUEUED){
				uploadMediaAttachment(att);
				return;
			}
		}
	}

	public boolean areThereAnyUploadingAttachments(){
		for(DraftMediaAttachment att:attachments){
			if(att.state==AttachmentUploadState.UPLOADING)
				return true;
		}
		return false;
	}

	private void onEditMediaDescriptionClick(View v){
		DraftMediaAttachment att=(DraftMediaAttachment) v.getTag();
		// MAMMUT: The Caption Track (§3) — the ALT dialog is removed for images in the
		// composer; the inline field is the one editor. Video/audio keep this path.
		if(isImage(att)){
			focusDescriptionEditor(att);
			return;
		}
		if(att.serverAttachment==null)
			return;
		Bundle args=new Bundle();
		args.putString("account", fragment.getAccountID());
		args.putString("attachment", att.serverAttachment.id);
		args.putParcelable("uri", att.uri);
		args.putString("existingDescription", att.description);
		args.putString("attachmentType", att.serverAttachment.type.toString());
		Drawable img=att.imageView.getDrawable();
		if(img!=null){
			args.putInt("width", img.getIntrinsicWidth());
			args.putInt("height", img.getIntrinsicHeight());
		}
		Nav.goForResult(fragment.getActivity(), ComposeImageDescriptionFragment.class, args, ComposeFragment.IMAGE_DESCRIPTION_RESULT, fragment);
	}

	// MAMMUT: The Caption Track (§2-§4) — metadata prefill and the inline description block.

	private static boolean isImage(DraftMediaAttachment att){
		if(att.mimeType!=null)
			return att.mimeType.startsWith("image/");
		return att.serverAttachment!=null && att.serverAttachment.type==Attachment.Type.IMAGE;
	}

	/**
	 * Reads the image's own metadata off the main thread and, if the field is still
	 * empty when the read finishes, fills it as a draft. The callback is keyed by the
	 * DraftMediaAttachment object itself, never by position, so several images added in
	 * quick succession each receive only their own text.
	 */
	private void prefillDescriptionFromMetadata(DraftMediaAttachment draft){
		Uri uri=draft.uri;
		// Local, not-yet-uploaded attachments only — never read remote or uploaded files
		if(uri==null || draft.serverAttachment!=null || !"content".equalsIgnoreCase(uri.getScheme()))
			return;
		MastodonAPIController.runInBackground(()->{
			String raw;
			try{
				raw=ImageDescriptionMetadata.read(fragment.getActivity(), uri);
			}catch(Exception x){
				Log.w(TAG, "prefillDescriptionFromMetadata: metadata read failed", x);
				raw=null;
			}
			if(raw==null)
				return;
			// Existing composer sanitizer (Gboard paste guard), then collapse newlines,
			// trim, and truncate to 1500 on a word boundary.
			String prefill=ImageDescriptionMetadata.normalizePrefill(fragment.sanitizeMediaDescription(raw));
			if(TextUtils.isEmpty(prefill))
				return;
			String result=prefill;
			UiUtils.runOnUiThread(()->{
				// Drop silently if the attachment was removed, the author typed while the
				// read was in flight, or a description already exists — never overwrite.
				if(fragment.getActivity()==null || !attachments.contains(draft) || !TextUtils.isEmpty(draft.description))
					return;
				draft.description=result;
				draft.descriptionSaved=false; // make saveAltTextsBeforePublishing send exactly this
				DescriptionEditor editor=descriptionEditors.get(draft);
				if(editor!=null)
					editor.setText(result);
				draft.setDescriptionToTitle(); // filled ALT chip
			});
		});
	}

	/**
	 * Rebuilds the description block beneath the media strip: one row per image, in
	 * attachment order. Rows are keyed by the attachment object in descriptionEditors,
	 * so removing or reordering attachments never mispairs text with an image.
	 */
	private void rebuildDescriptionEditors(){
		if(attachmentDescriptions==null)
			return;
		attachmentDescriptions.removeAllViews();
		descriptionEditors.clear();
		List<DraftMediaAttachment> images=attachments.stream().filter(ComposeMediaViewController::isImage).collect(Collectors.toList());
		int count=images.size();
		for(int i=0;i<count;i++){
			DraftMediaAttachment att=images.get(i);
			DescriptionEditor editor=createDescriptionEditor(att);
			editor.bind(i+1, count);
			descriptionEditors.put(att, editor);
			attachmentDescriptions.addView(editor.view);
		}
		updateDescriptionBlockVisibility();
	}

	private DescriptionEditor createDescriptionEditor(DraftMediaAttachment att){
		View row=fragment.getActivity().getLayoutInflater().inflate(R.layout.compose_attachment_description_row, attachmentDescriptions, false);
		DescriptionEditor editor=new DescriptionEditor(att, row);
		editor.thumb.setOutlineProvider(OutlineProviders.roundedRect(8));
		editor.thumb.setClipToOutline(true);
		if(att.serverAttachment!=null && att.serverAttachment.previewUrl!=null){
			ViewImageLoader.load(editor.thumb, att.serverAttachment.blurhashPlaceholder, new UrlImageLoaderRequest(att.serverAttachment.previewUrl, V.dp(32), V.dp(32)));
		}else if(att.uri!=null){
			ViewImageLoader.load(editor.thumb, null, new UrlImageLoaderRequest(att.uri, V.dp(32), V.dp(32)));
		}
		// Hard 1500 cap; InputFilter truncates paste beyond the limit instead of rejecting it
		editor.field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ImageDescriptionMetadata.MAX_DESCRIPTION_LENGTH)});
		editor.field.addTextChangedListener(new TextWatcher(){
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count){}

			@Override
			public void afterTextChanged(Editable s){
				if(editor.updating)
					return;
				// Write through to the attachment model on every change: rotation and
				// process death preserve the text, and saveAltTextsBeforePublishing
				// sends exactly what the field shows.
				att.description=s.toString();
				att.descriptionSaved=false;
				editor.updateHelperAndCounter();
				att.setDescriptionToTitle(); // ALT chip state follows the field immediately
			}
		});
		return editor;
	}

	public void setDescriptionBlockHiddenByPoll(boolean hidden){
		descriptionBlockHiddenByPoll=hidden;
		updateDescriptionBlockVisibility();
	}

	private void updateDescriptionBlockVisibility(){
		if(attachmentDescriptions==null)
			return;
		boolean show=!descriptionEditors.isEmpty() && !descriptionBlockHiddenByPoll;
		attachmentDescriptions.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/**
	 * Scrolls the image's row into view and focuses its field, showing the keyboard.
	 * Used by the ALT status chip (and, defensively, the hidden edit button path).
	 */
	private void focusDescriptionEditor(DraftMediaAttachment att){
		DescriptionEditor editor=descriptionEditors.get(att);
		if(editor==null || fragment.getActivity()==null)
			return;
		updateDescriptionBlockVisibility();
		editor.view.post(()->{
			editor.view.requestRectangleOnScreen(new Rect(0, 0, editor.view.getWidth(), editor.view.getHeight()), false);
			editor.field.requestFocus();
			InputMethodManager imm=(InputMethodManager)fragment.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			if(imm!=null)
				imm.showSoftInput(editor.field, InputMethodManager.SHOW_IMPLICIT);
		});
	}

	/**
	 * One row of the description block: 32dp thumbnail, "Image N" label, multiline field,
	 * and a shared helper/counter slot (§3, §5).
	 */
	private class DescriptionEditor{
		final DraftMediaAttachment attachment;
		final View view;
		final ImageView thumb;
		final TextView label, helper;
		final EditText field;
		boolean updating;

		DescriptionEditor(DraftMediaAttachment attachment, View view){
			this.attachment=attachment;
			this.view=view;
			thumb=view.findViewById(R.id.thumb);
			label=view.findViewById(R.id.label);
			field=view.findViewById(R.id.edit);
			helper=view.findViewById(R.id.helper);
		}

		void bind(int imageIndex, int imageCount){
			label.setText(fragment.getString(R.string.image_x, imageIndex));
			field.setContentDescription(fragment.getString(R.string.image_description_a11y, imageIndex, imageCount));
			setText(attachment.description);
		}

		void setText(String text){
			if(Objects.equals(text==null ? "" : text, field.getText().toString())){
				updateHelperAndCounter();
				return;
			}
			updating=true;
			field.setText(text);
			updating=false;
			updateHelperAndCounter();
		}

		/**
		 * Helper line when the field is empty; "N/1500" counter only in the last 200
		 * characters (§3); hidden otherwise. The slot is a polite live region so the
		 * counter is announced only while visible (§5).
		 */
		void updateHelperAndCounter(){
			int len=field.getText().length();
			if(len>ImageDescriptionMetadata.MAX_DESCRIPTION_LENGTH-200){
				helper.setVisibility(View.VISIBLE);
				helper.setText(fragment.getString(R.string.image_description_counter, len, ImageDescriptionMetadata.MAX_DESCRIPTION_LENGTH));
			}else if(len==0){
				helper.setVisibility(View.VISIBLE);
				helper.setText(R.string.image_description_helper);
			}else{
				helper.setVisibility(View.GONE);
			}
		}
	}

	public int getMediaAttachmentsCount(){
		return attachments.size();
	}
	
	public void cancelAllUploads(){
		for(DraftMediaAttachment att:attachments){
			if(att.isUploadingOrProcessing())
				att.cancelUpload();
		}
	}
	
	public void setAltTextByID(String attID, String text){
		for(DraftMediaAttachment att:attachments){
			if(att.serverAttachment.id.equals(attID)){
				att.descriptionSaved=false;
				att.description=text;
				att.setDescriptionToTitle();
				break;
			}
		}
	}
	
	public List<String> getAttachmentIDs(){
		return attachments.stream().filter(a->a.serverAttachment!=null).map(a->a.serverAttachment.id).collect(Collectors.toList());
	}

	public List<CreateStatus.Request.MediaAttribute> getAttachmentAttributes(){
		List<CreateStatus.Request.MediaAttribute> mediaAttributes = new ArrayList<>();
		for (DraftMediaAttachment att:attachments){
			 mediaAttributes.add(new CreateStatus.Request.MediaAttribute(att.serverAttachment.id, att.description, null));
		}
		return mediaAttributes;
	}
	
	public boolean isEmpty(){
		return attachments.isEmpty();
	}

	public boolean canAddMoreAttachments(){
		return attachments.size()<MAX_ATTACHMENTS;
	}

	public int getMissingAltTextAttachmentCount(){
		int count=0;
		for(DraftMediaAttachment att:attachments){
			if(TextUtils.isEmpty(att.description))
				count++;
		}
		return count;
	}

	public boolean areAllAttachmentsImages(){
		for(DraftMediaAttachment att:attachments){
			if((att.mimeType==null && att.serverAttachment.type==Attachment.Type.IMAGE) || (att.mimeType!=null && !att.mimeType.startsWith("image/")))
				return false;
		}
		return true;
	}

	public int getMaxAttachments(){
		return MAX_ATTACHMENTS;
	}

	// MAMMUT: Composer 2.0 (§2/§3) — draft snapshot and restore. Restored media
	// re-enters through addRestoredAttachment below, which never invokes the
	// Caption Track metadata prefill (prefill is only called from
	// addMediaAttachment), so a restored description is never overwritten by
	// a metadata suggestion.

	/**
	 * MAMMUT: Composer 2.0 (M5) — UI-thread snapshot of the attachment list.
	 * The copy is made here, on the thread that owns the list, and passed
	 * into the draft-save runnable; the worker thread then only touches the
	 * snapshot, so it can never iterate the live list while the UI thread
	 * adds or removes attachments.
	 */
	public List<DraftMediaAttachment> snapshotAttachments(){
		return new ArrayList<>(attachments);
	}

	/**
	 * Builds the durable attachment list for a draft save from a UI-thread
	 * snapshot (see {@link #snapshotAttachments()}). Attachments already
	 * copied into this draft directory on a previous save are reused; fresh
	 * picker attachments are copied in via the shared store (§2: media is
	 * copied in, never referenced).
	 * <p>
	 * MAMMUT (M4): an attachment that cannot be persisted — no mime type, no
	 * source to copy from, or a failed copy — fails the whole save with an
	 * IOException. The draft is never saved with silently missing media, and
	 * ComposeFragment surfaces the failure to the author (§6).
	 * Must run on a background thread.
	 */
	public List<DraftAttachment> buildDraftAttachments(List<DraftMediaAttachment> snapshot, DraftStore store, String draftId) throws IOException{
		List<DraftAttachment> result=new ArrayList<>();
		for(DraftMediaAttachment att:snapshot){
			if(att.mimeType==null)
				throw new IOException("attachment has no mime type to persist");
			DraftAttachment da=new DraftAttachment();
			da.mimeType=att.mimeType;
			da.description=att.description;
			String existing=att.draftFilePath;
			if(existing!=null && new File(existing).exists()){
				da.filePath=existing;
			}else if(att.uri!=null){
				da.filePath=store.importMedia(draftId, att.uri, att.mimeType);
			}else{
				throw new IOException("attachment has no source to copy from");
			}
			result.add(da);
		}
		return result;
	}

	/**
	 * Re-adds an attachment from a resumed draft, queued for upload. The stored
	 * description is the author's text, not a suggestion — this method never
	 * runs the Caption Track metadata prefill. The file stays a {@code file://}
	 * URI: the upload pipeline reads it through the app's own resolver, so no
	 * FileProvider (and no cross-account exposure of the drafts directory) is
	 * needed — the request bodies stat {@code file://} URIs directly, and
	 * ContentResolver.openInputStream serves them like any content URI.
	 */
	public void addRestoredAttachment(DraftAttachment da){
		if(da.filePath==null)
			return;
		File f=new File(da.filePath);
		if(!f.exists())
			return;
		Uri uri=Uri.fromFile(f);
		DraftMediaAttachment draft=new DraftMediaAttachment();
		draft.uri=uri;
		draft.draftFilePath=da.filePath;
		draft.mimeType=da.mimeType;
		draft.description=da.description;
		draft.fileSize=(int)Math.min(f.length(), Integer.MAX_VALUE);
		draft.state=AttachmentUploadState.QUEUED;
		attachmentsView.addView(createMediaAttachmentView(draft), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
		attachments.add(draft);
		attachmentsScroller.setVisibility(View.VISIBLE);
		updateMediaAttachmentsLayout();
		rebuildDescriptionEditors();
		if(!areThereAnyUploadingAttachments())
			uploadNextQueuedAttachment();
		fragment.updatePublishButtonState();
		fragment.updateMediaPollStates();
	}

	public int getNonDoneAttachmentCount(){
		int nonDoneAttachmentCount=0;
		for(DraftMediaAttachment att:attachments){
			if(att.state!=AttachmentUploadState.DONE)
				nonDoneAttachmentCount++;
		}
		return nonDoneAttachmentCount;
	}

	// MAMMUT: Composer 2.0 (§4) — thread support. Each thread segment owns one
	// ComposeMediaViewController; these three methods let ComposeFragment
	// move attachment lists between segments (reorder), drop a published
	// prefix without touching the server, and rebind views after either.

	/**
	 * Drops every attachment from this segment: in-flight uploads are
	 * cancelled, the strip is emptied and hidden. Used when the segment the
	 * attachments belonged to was already published — the copies stay on the
	 * server, attached to the published post.
	 */
	public void clearAllAttachments(){
		for(DraftMediaAttachment att:new ArrayList<>(attachments)){
			if(att.isUploadingOrProcessing())
				att.cancelUpload();
		}
		attachments.clear();
		if(attachmentsView!=null)
			attachmentsView.removeAllViews();
		if(attachmentsScroller!=null)
			attachmentsScroller.setVisibility(View.GONE);
		rebuildDescriptionEditors();
		fragment.updatePublishButtonState();
		fragment.updateMediaPollStates();
	}

	/**
	 * Rebuilds the strip from the retained attachment list, re-inflating one
	 * tile per attachment. Used when a card is re-created after its view was
	 * destroyed (rotation) or when two segments swapped their lists.
	 */
	public void reloadViewsFromState(){
		if(attachmentsView==null)
			return;
		attachmentsView.removeAllViews();
		for(DraftMediaAttachment att:attachments){
			attachmentsView.addView(createMediaAttachmentView(att), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		attachmentsScroller.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
		updateMediaAttachmentsLayout();
		rebuildDescriptionEditors();
		fragment.updatePublishButtonState();
		fragment.updateMediaPollStates();
	}

	/**
	 * Swaps the attachment lists of two segments. Private state is
	 * accessible across instances of this class, so the exchange never
	 * copies DraftMediaAttachment objects — the same objects keep their
	 * upload state and descriptions, only their segment changes.
	 */
	public void swapAttachmentsWith(ComposeMediaViewController other){
		ArrayList<DraftMediaAttachment> tmp=other.attachments;
		other.attachments=this.attachments;
		this.attachments=tmp;
		reloadViewsFromState();
		other.reloadViewsFromState();
	}

	public void saveAltTextsBeforePublishing(Runnable onSuccess, Consumer<ErrorResponse> onError){
		ArrayList<UpdateAttachment> updateAltTextRequests=new ArrayList<>();
		for(DraftMediaAttachment att:attachments){
			if(!att.descriptionSaved && (fragment.editingStatus==null || !fragment.editingStatus.mediaAttachments.contains(att.serverAttachment))){
				UpdateAttachment req=new UpdateAttachment(att.serverAttachment.id, att.description);
				req.setCallback(new Callback<>(){
							@Override
							public void onSuccess(Attachment result){
								att.descriptionSaved=true;
								att.serverAttachment=result;
								updateAltTextRequests.remove(req);
								if(updateAltTextRequests.isEmpty())
									onSuccess.run();
							}

							@Override
							public void onError(ErrorResponse error){
								onError.accept(error);
							}
						})
						.exec(fragment.getAccountID());
				updateAltTextRequests.add(req);
			}
		}
		if(updateAltTextRequests.isEmpty())
			onSuccess.run();
	}

	public void onSaveInstanceState(Bundle outState){
		if(!attachments.isEmpty()){
			ArrayList<Parcelable> serializedAttachments=new ArrayList<>(attachments.size());
			for(DraftMediaAttachment att:attachments){
				serializedAttachments.add(Parcels.wrap(att));
			}
			outState.putParcelableArrayList("attachments", serializedAttachments);
		}
	}

	@Parcel
	public static class DraftMediaAttachment{
		public Attachment serverAttachment;
		public Uri uri;
		public transient UploadAttachment uploadRequest;
		public transient GetAttachmentByID processingPollingRequest;
		public String description;
		public String mimeType;
		public AttachmentUploadState state=AttachmentUploadState.QUEUED;
		public int fileSize;
		public boolean descriptionSaved=true;
		/** MAMMUT: Composer 2.0 — path of this file's private copy in the draft directory, once saved */
		public transient String draftFilePath;
		/** t_54414ac0: the verified local copy + transcode, kept so a retry reuses it instead of
		 *  re-opening a picker URI whose read grant may already have expired. */
		public transient UploadStaging.StagedUpload stagedUpload;

		public transient View view;
		public transient ProgressBar progressBar;
		public transient ImageButton removeButton, editButton;
		public transient Runnable processingPollingRunnable;
		public transient ImageView imageView;
		public transient TextView titleView, subtitleView;
		public transient TransferSpeedTracker speedTracker=new TransferSpeedTracker();
		private transient boolean errorColors;
		private transient Animator errorTransitionAnimator;
		public transient View dragLayer;

		public void cancelUpload(){
			switch(state){
				case UPLOADING -> {
					if(uploadRequest!=null){
						uploadRequest.cancel();
						uploadRequest=null;
					}
				}
				case PROCESSING -> {
					if(processingPollingRunnable!=null){
						UiUtils.removeCallbacks(processingPollingRunnable);
						processingPollingRunnable=null;
					}
					if(processingPollingRequest!=null){
						processingPollingRequest.cancel();
						processingPollingRequest=null;
					}
				}
				default -> throw new IllegalStateException("Unexpected state "+state);
			}
		}

		public boolean isUploadingOrProcessing(){
			return state==AttachmentUploadState.UPLOADING || state==AttachmentUploadState.PROCESSING;
		}

		public void setDescriptionToTitle(){
			// MAMMUT: The Caption Track (§3) — for images the tile's label is a status chip:
			// filled when a description exists, outlined when not, and tapping it focuses
			// the matching inline field. Video/audio keep the old label behavior.
			if(ComposeMediaViewController.isImage(this)){
				updateAltChip();
				return;
			}
			if(TextUtils.isEmpty(description)){
				titleView.setText(R.string.add_alt_text);
				titleView.setTextColor(UiUtils.getThemeColor(titleView.getContext(), R.attr.colorM3Error));
			}else{
				titleView.setText(description);
				titleView.setTextColor(UiUtils.getThemeColor(titleView.getContext(), R.attr.colorM3OnSurface));
			}
		}

		/**
		 * MAMMUT: The Caption Track (§3, §5) — the ALT chip. Only applies once the upload
		 * is done; while uploading, the title keeps showing progress.
		 */
		private void updateAltChip(){
			if(titleView==null || state!=AttachmentUploadState.DONE)
				return;
			boolean described=!TextUtils.isEmpty(description);
			Context ctx=titleView.getContext();
			GradientDrawable bg=new GradientDrawable();
			bg.setCornerRadius(V.dp(12));
			if(described){
				bg.setColor(UiUtils.getThemeColor(ctx, R.attr.colorM3Primary));
				titleView.setTextColor(UiUtils.getThemeColor(ctx, R.attr.colorM3OnPrimary));
			}else{
				bg.setColor(0x00000000);
				bg.setStroke(V.dp(1), UiUtils.getThemeColor(ctx, R.attr.colorM3Outline));
				titleView.setTextColor(UiUtils.getThemeColor(ctx, R.attr.colorM3OnSurfaceVariant));
			}
			titleView.setBackground(bg);
			titleView.setText(R.string.alt_chip);
			titleView.setPadding(V.dp(8), 0, V.dp(8), 0);
			ViewGroup.LayoutParams lp=titleView.getLayoutParams();
			if(lp instanceof RelativeLayout.LayoutParams){
				RelativeLayout.LayoutParams rlp=(RelativeLayout.LayoutParams) lp;
				rlp.width=ViewGroup.LayoutParams.WRAP_CONTENT;
				rlp.height=V.dp(24);
				rlp.setMarginStart(V.dp(8));
				rlp.setMarginEnd(0);
				titleView.setLayoutParams(rlp);
			}
		}

		public void setUseErrorColors(boolean use){
			if(errorColors==use)
				return;
			errorColors=use;
			if(errorTransitionAnimator!=null)
				errorTransitionAnimator.cancel();
			AnimatorSet set=new AnimatorSet();
			int color1, color2, color3;
			if(use){
				color1=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3ErrorContainer);
				color2=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3Error);
				color3=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3OnErrorContainer);
			}else{
				color1=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3Surface);
				color2=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3OnSurface);
				color3=UiUtils.getThemeColor(view.getContext(), R.attr.colorM3OnSurfaceVariant);
			}
			set.playTogether(
					ObjectAnimator.ofArgb(view, "backgroundColor", ((ColorDrawable)view.getBackground()).getColor(), color1),
					ObjectAnimator.ofArgb(titleView, "textColor", titleView.getCurrentTextColor(), color2),
					ObjectAnimator.ofArgb(subtitleView, "textColor", subtitleView.getCurrentTextColor(), color3),
					ObjectAnimator.ofArgb(removeButton.getDrawable(), "tint", subtitleView.getCurrentTextColor(), color3)
			);
			editButton.getDrawable().setTint(color3);
			set.setDuration(250);
			set.setInterpolator(CubicBezierInterpolator.DEFAULT);
			set.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					errorTransitionAnimator=null;
				}
			});
			set.start();
			errorTransitionAnimator=set;
		}
	}

	enum AttachmentUploadState{
		QUEUED,
		UPLOADING,
		PROCESSING,
		ERROR,
		DONE
	}

	private class AttachmentDragListener implements ReorderableLinearLayout.OnDragListener{
		private final HashMap<View, Animator> currentAnimations=new HashMap<>();

		@Override
		public void onSwapItems(int oldIndex, int newIndex){
			attachments.add(newIndex, attachments.remove(oldIndex));
		}

		@Override
		public void onDragStart(View view){
			if(currentAnimations.containsKey(view))
				currentAnimations.get(view).cancel();
			fragment.mainLayout.setClipChildren(false);
			AnimatorSet set=new AnimatorSet();
			DraftMediaAttachment att=(DraftMediaAttachment) view.getTag();
			att.dragLayer.setVisibility(View.VISIBLE);
			set.playTogether(
					ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, V.dp(3)),
					ObjectAnimator.ofFloat(att.dragLayer, View.ALPHA, 0.16f)
			);
			set.setDuration(150);
			set.setInterpolator(CubicBezierInterpolator.DEFAULT);
			set.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					currentAnimations.remove(view);
				}
			});
			currentAnimations.put(view, set);
			set.start();
		}

		@Override
		public void onDragEnd(View view){
			if(currentAnimations.containsKey(view))
				currentAnimations.get(view).cancel();
			// MAMMUT: The Caption Track (§4) — rows follow the new attachment order
			rebuildDescriptionEditors();
			AnimatorSet set=new AnimatorSet();
			DraftMediaAttachment att=(DraftMediaAttachment) view.getTag();
			set.playTogether(
					ObjectAnimator.ofFloat(att.dragLayer, View.ALPHA, 0),
					ObjectAnimator.ofFloat(view, View.TRANSLATION_Z, 0),
					ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0),
					ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0)
			);
			set.setDuration(200);
			set.setInterpolator(CubicBezierInterpolator.DEFAULT);
			set.addListener(new AnimatorListenerAdapter(){
				@Override
				public void onAnimationEnd(Animator animation){
					if(currentAnimations.isEmpty())
						fragment.mainLayout.setClipChildren(true);
					att.dragLayer.setVisibility(View.GONE);
					currentAnimations.remove(view);
				}
			});
			currentAnimations.put(view, set);
			set.start();
		}
	}

	/**
	 * t_54414ac0: stage the file once (verified local copy, HEIC→JPEG) off the main thread, then
	 * upload from that copy. The provider's size column is never consulted and a retry reuses the
	 * staged file, so an expired picker grant cannot turn into a second, different failure.
	 */
	private void stageAndUpload(DraftMediaAttachment attachment, int maxSize, String mime){
		final String fileName=UiUtils.getFileName(attachment.uri);
		if(attachment.stagedUpload!=null){
			performUpload(attachment, attachment.stagedUpload, maxSize, fileName);
			return;
		}
		new Thread(()->{
			UploadStaging.StagedUpload staged;
			try{
				staged=UploadStaging.stage(MastodonApp.context, attachment.uri, UploadStaging.sourceFor(attachment.uri, mime));
			}catch(Throwable t){
				UiUtils.runOnUiThread(()->onLocalUploadError(attachment, t));
				return;
			}
			attachment.stagedUpload=staged;
			UiUtils.runOnUiThread(()->performUpload(attachment, staged, maxSize, fileName));
		}, "mammut-upload-stage").start();
	}

	private void performUpload(DraftMediaAttachment attachment, UploadStaging.StagedUpload staged, int maxSize, String fileName){
		attachment.uploadRequest=(UploadAttachment) new UploadAttachment(staged, maxSize, attachment.description, staged.transcodedFromHeic ? withJpegExtension(fileName) : fileName)
				.setProgressListener(new ProgressListener(){
					@Override
					public void onProgress(long transferred, long total){
						if(fragment.getActivity()==null)
							return;
						float progressFraction=transferred/(float)total;
						int progress=Math.round(progressFraction*attachment.progressBar.getMax());
						if(Build.VERSION.SDK_INT>=24)
							attachment.progressBar.setProgress(progress, true);
						else
							attachment.progressBar.setProgress(progress);

						attachment.titleView.setText(fragment.getString(R.string.attachment_x_percent_uploaded, Math.round(progressFraction*100f)));

						attachment.speedTracker.setTotalBytes(total);
//						attachment.uploadStateTitle.setText(fragment.getString(R.string.file_upload_progress, UiUtils.formatFileSize(fragment.getActivity(), transferred, true), UiUtils.formatFileSize(fragment.getActivity(), total, true)));
						attachment.speedTracker.addSample(transferred);
					}
				})
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Attachment result){
						attachment.serverAttachment=result;
						if(TextUtils.isEmpty(result.url)){
							attachment.state=AttachmentUploadState.PROCESSING;
							attachment.processingPollingRunnable=()->pollForMediaAttachmentProcessing(attachment);
							if(fragment.getActivity()==null)
								return;
							attachment.titleView.setText(R.string.upload_processing);
							UiUtils.runOnUiThread(attachment.processingPollingRunnable, 1000);
							if(!areThereAnyUploadingAttachments())
								uploadNextQueuedAttachment();
						}else{
							finishMediaAttachmentUpload(attachment);
						}
					}

					@Override
					public void onError(ErrorResponse error){
						attachment.uploadRequest=null;
						attachment.state=AttachmentUploadState.ERROR;
						// t_54414ac0 req 4: a 5xx from the media endpoint is the server failing on the bytes it
						// got, not a network error and not a local read failure — say so instead of "upload failed".
						if(error instanceof MastodonErrorResponse mer && mer.httpStatus>=500)
							attachment.titleView.setText(R.string.upload_error_server);
						else
							attachment.titleView.setText(R.string.upload_failed);
//						if(error instanceof MastodonErrorResponse er){
//							if(er.underlyingException instanceof SocketException || er.underlyingException instanceof UnknownHostException || er.underlyingException instanceof InterruptedIOException)
//								attachment.uploadStateText.setText(R.string.upload_error_connection_lost);
//							else
//								attachment.uploadStateText.setText(er.error);
//						}else{
//							attachment.uploadStateText.setText("");
//						}
//						attachment.retryButton.setImageResource(R.drawable.ic_fluent_arrow_clockwise_24_filled);
//						attachment.retryButton.setContentDescription(fragment.getString(R.string.retry_upload));

						V.setVisibilityAnimated(attachment.editButton, View.VISIBLE);
						attachment.editButton.setImageResource(R.drawable.ic_restart_alt_24px);
						attachment.editButton.setOnClickListener(ComposeMediaViewController.this::onRetryOrCancelMediaUploadClick);
						attachment.setUseErrorColors(true);
						V.setVisibilityAnimated(attachment.progressBar, View.GONE);

						if(!areThereAnyUploadingAttachments())
							uploadNextQueuedAttachment();
					}
				})
				.exec(fragment.getAccountID());
	}

	/** A failure before the request existed: nothing was sent, so say what we could not read. */
	private void onLocalUploadError(DraftMediaAttachment attachment, Throwable t){
		Log.w("ComposeMediaViewController", "Could not stage media for upload", t);
		attachment.uploadRequest=null;
		attachment.state=AttachmentUploadState.ERROR;
		attachment.titleView.setText(R.string.upload_error_unreadable);
		V.setVisibilityAnimated(attachment.editButton, View.VISIBLE);
		attachment.editButton.setImageResource(R.drawable.ic_restart_alt_24px);
		attachment.editButton.setOnClickListener(ComposeMediaViewController.this::onRetryOrCancelMediaUploadClick);
		attachment.setUseErrorColors(true);
		V.setVisibilityAnimated(attachment.progressBar, View.GONE);
		if(!areThereAnyUploadingAttachments())
			uploadNextQueuedAttachment();
	}

	private static void releaseStagedUpload(DraftMediaAttachment att){
		if(att.stagedUpload!=null && att.stagedUpload.deleteOnRelease && att.stagedUpload.file!=null)
			att.stagedUpload.file.delete();
		att.stagedUpload=null;
	}

	private static String withJpegExtension(String name){
		if(name==null || name.isEmpty())
			return "upload.jpg";
		int dot=name.lastIndexOf('.');
		return (dot>0 ? name.substring(0,dot) : name)+".jpg";
	}
}
