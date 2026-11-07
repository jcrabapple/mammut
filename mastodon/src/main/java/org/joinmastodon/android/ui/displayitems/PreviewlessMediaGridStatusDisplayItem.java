package org.joinmastodon.android.ui.displayitems;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.utils.PreviewlessMediaAttachmentViewController;
import org.joinmastodon.android.ui.views.FrameLayoutThatOnlyMeasuresFirstChild;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: port from upstream master — the "show media preview" preference, when off, replaces
 * thumbnails with a compact list of attachment rows. Clicking a row still opens the full viewer.
 */
public class PreviewlessMediaGridStatusDisplayItem extends StatusDisplayItem{
	private final List<Attachment> attachments;
	public final Status status;

	public PreviewlessMediaGridStatusDisplayItem(String parentID, Callbacks callbacks, Context context, List<Attachment> attachments, Status status){
		super(parentID, callbacks, context);
		this.attachments=attachments;
		this.status=status;
	}

	@Override
	public Type getType(){
		return Type.PREVIEWLESS_MEDIA_GRID;
	}

	@Override
	public int getImageCount(){
		return 0;
	}

	public static class Holder extends StatusDisplayItem.Holder<PreviewlessMediaGridStatusDisplayItem>{
		private final FrameLayout wrapper;
		private final LinearLayout layout;
		private final View.OnClickListener clickListener=this::onViewClick;
		private final ArrayList<PreviewlessMediaAttachmentViewController> controllers=new ArrayList<>();

		public Holder(Activity activity, ViewGroup parent){
			super(new FrameLayoutThatOnlyMeasuresFirstChild(activity));
			wrapper=(FrameLayout) itemView;
			layout=new LinearLayout(activity);
			layout.setOrientation(LinearLayout.VERTICAL);
			layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			wrapper.addView(layout);
			wrapper.setClipToPadding(false);
		}

		@Override
		public void onBind(PreviewlessMediaGridStatusDisplayItem item){
			layout.removeAllViews();
			controllers.clear();
			int i=0;
			for(Attachment att:item.attachments){
				MediaGridStatusDisplayItem.GridItemType type=switch(att.type){
					case IMAGE -> MediaGridStatusDisplayItem.GridItemType.PHOTO;
					case VIDEO -> MediaGridStatusDisplayItem.GridItemType.VIDEO;
					case GIFV -> MediaGridStatusDisplayItem.GridItemType.GIFV;
					default -> MediaGridStatusDisplayItem.GridItemType.PHOTO;
				};
				PreviewlessMediaAttachmentViewController c=new PreviewlessMediaAttachmentViewController(itemView.getContext(), type);
				if(c.view.getLayoutParams()==null)
					c.view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				layout.addView(c.view);
				c.view.setOnClickListener(clickListener);
				c.view.setTag(i);
				controllers.add(c);
				c.bind(att, item.status);
				i++;
			}
		}

		private void onViewClick(View v){
			int index=(Integer) v.getTag();
			item.callbacks.openPhotoViewer(item.parentID, item.status, index, null);
		}
	}
}
