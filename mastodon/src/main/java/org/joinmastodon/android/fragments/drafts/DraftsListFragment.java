package org.joinmastodon.android.fragments.drafts;

import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.drafts.ComposeDraft;
import org.joinmastodon.android.drafts.DraftAttachment;
import org.joinmastodon.android.drafts.DraftStore;
import org.joinmastodon.android.drafts.Segment;
import org.joinmastodon.android.fragments.ComposeFragment;
import org.joinmastodon.android.fragments.MastodonToolbarFragment;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.tabs.TabLayout;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.Nav;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.UsableRecyclerView;

/**
 * MAMMUT: Composer 2.0 (§3) — the drafts screen. Two tabs: Drafts, filled by
 * the shared {@link DraftStore}, and Scheduled, an empty placeholder until
 * slice 3. Both the screen and the composer use the one store — there is no
 * second drafts implementation inside the composer (§6).
 */
public class DraftsListFragment extends MastodonToolbarFragment{
	private String accountID;
	private TabLayout tabLayout;
	private DraftsPageFragment draftsPage;
	private ScheduledPageFragment scheduledPage;
	private Fragment visiblePage;

	public DraftsListFragment(){
		super(R.layout.fragment_drafts);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		accountID=getArguments().getString("account");
		setTitle(R.string.drafts);
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view=inflater.inflate(R.layout.fragment_drafts, container, false);
		tabLayout=view.findViewById(R.id.tabbar);
		tabLayout.setTabTextColors(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurfaceVariant), UiUtils.getThemeColor(getActivity(), R.attr.colorM3Primary));
		tabLayout.setTabTextSize(V.dp(14));
		tabLayout.addTab(tabLayout.newTab().setText(R.string.drafts));
		tabLayout.addTab(tabLayout.newTab().setText(R.string.scheduled));
		tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){
			@Override
			public void onTabSelected(TabLayout.Tab tab){
				showPage(tab.getPosition());
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab){}

			@Override
			public void onTabReselected(TabLayout.Tab tab){}
		});
		if(savedInstanceState==null){
			Bundle args=new Bundle();
			args.putString("account", accountID);
			draftsPage=new DraftsPageFragment();
			draftsPage.setArguments(args);
			scheduledPage=new ScheduledPageFragment();
			scheduledPage.setArguments(args);
			getChildFragmentManager().beginTransaction()
					.add(R.id.drafts_page_container, draftsPage, "drafts")
					.add(R.id.drafts_page_container, scheduledPage, "scheduled")
					.hide(scheduledPage)
					.commit();
		}else{
			draftsPage=(DraftsPageFragment) getChildFragmentManager().findFragmentByTag("drafts");
			scheduledPage=(ScheduledPageFragment) getChildFragmentManager().findFragmentByTag("scheduled");
		}
		visiblePage=draftsPage;
		return view;
	}

	private void showPage(int position){
		Fragment target=position==0 ? draftsPage : scheduledPage;
		if(target==null || target==visiblePage)
			return;
		getChildFragmentManager().beginTransaction()
				.hide(visiblePage)
				.show(target)
				.commit();
		visiblePage=target;
	}

	/**
	 * MAMMUT: Composer 2.0 (§3) — the Drafts tab: rows with a text preview, a
	 * factual subtitle, overflow Delete, tap to resume in the composer.
	 */
	public static class DraftsPageFragment extends Fragment{
		private static final int TITLE_MAX_CHARS=120;

		private String accountID;
		private final ArrayList<ComposeDraft> drafts=new ArrayList<>();
		private DraftAdapter adapter;
		private UsableRecyclerView list;
		private View emptyView;
		private boolean loaded;

		@Override
		public void onCreate(Bundle savedInstanceState){
			super.onCreate(savedInstanceState);
			accountID=getArguments().getString("account");
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
			View view=inflater.inflate(R.layout.fragment_drafts_page, container, false);
			list=view.findViewById(R.id.list);
			emptyView=view.findViewById(R.id.empty);
			list.setLayoutManager(new LinearLayoutManager(getActivity()));
			adapter=new DraftAdapter();
			list.setAdapter(adapter);
			if(savedInstanceState==null || !loaded)
				loadDrafts();
			else
				updateEmpty();
			return view;
		}

		private void loadDrafts(){
			DraftStore store=new DraftStore(MastodonApp.context, accountID);
			MastodonAPIController.runInBackground(()->{
				List<ComposeDraft> result=store.list();
				if(getActivity()==null)
					return;
				UiUtils.runOnUiThread(()->{
					drafts.clear();
					drafts.addAll(result);
					loaded=true;
					adapter.notifyDataSetChanged();
					updateEmpty();
				});
			});
		}

		private void updateEmpty(){
			boolean empty=drafts.isEmpty();
			emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
			list.setVisibility(empty ? View.GONE : View.VISIBLE);
		}

		private void openDraft(int position){
			ComposeDraft d=drafts.get(position);
			Bundle args=new Bundle();
			args.putString("account", accountID);
			args.putString("draftId", d.id);
			Nav.go(getActivity(), ComposeFragment.class, args);
		}

		private void confirmDeleteDraft(int position){
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.delete_draft)
					.setMessage(R.string.delete_draft_message)
					.setPositiveButton(R.string.delete, (dlg, which)->deleteDraft(position))
					.setNegativeButton(R.string.cancel, null)
					.show();
		}

		private void deleteDraft(int position){
			ComposeDraft d=drafts.remove(position);
			adapter.notifyItemRemoved(position);
			updateEmpty();
			// The store removes the directory with the media copies inside it
			MastodonAPIController.runInBackground(()->new DraftStore(MastodonApp.context, accountID).delete(d.id));
		}

		private String draftTitle(ComposeDraft d){
			String title=null;
			for(Segment seg:d.segments){
				if(!TextUtils.isEmpty(seg.text)){
					title=seg.text;
					break;
				}
			}
			if(title==null)
				return getString(R.string.draft_media_only);
			return title.length()>TITLE_MAX_CHARS ? title.substring(0, TITLE_MAX_CHARS) : title;
		}

		private String draftSubtitle(ComposeDraft d){
			List<String> parts=new ArrayList<>();
			int posts=d.segments.size();
			parts.add(getResources().getQuantityString(R.plurals.draft_x_posts, posts, posts));
			int images=0, media=0;
			for(Segment seg:d.segments){
				for(DraftAttachment att:seg.attachments){
					if(att.mimeType!=null && att.mimeType.startsWith("image/"))
						images++;
					else
						media++;
				}
			}
			if(images>0)
				parts.add(getResources().getQuantityString(R.plurals.draft_x_images, images, images));
			if(media>0)
				parts.add(getResources().getQuantityString(R.plurals.draft_x_media, media, media));
			parts.add(getString(R.string.draft_edited_ago,
					UiUtils.formatRelativeTimestampAsMinutesAgo(getActivity(), Instant.ofEpochMilli(d.updatedAt), true)));
			return TextUtils.join(" · ", parts);
		}

		private class DraftAdapter extends RecyclerView.Adapter<DraftHolder>{
			@NonNull
			@Override
			public DraftHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
				return new DraftHolder(getActivity().getLayoutInflater().inflate(R.layout.item_draft, parent, false));
			}

			@Override
			public void onBindViewHolder(@NonNull DraftHolder holder, int position){
				ComposeDraft d=drafts.get(position);
				holder.title.setText(draftTitle(d));
				holder.subtitle.setText(draftSubtitle(d));
				holder.itemView.setOnClickListener(v->openDraft(holder.getAdapterPosition()));
				holder.overflow.setOnClickListener(v->{
					PopupMenu menu=new PopupMenu(getActivity(), holder.overflow);
					menu.inflate(R.menu.draft_row);
					menu.setOnMenuItemClickListener(item->{
						int pos=holder.getAdapterPosition();
						if(pos!=RecyclerView.NO_POSITION)
							confirmDeleteDraft(pos);
						return true;
					});
					menu.show();
				});
			}

			@Override
			public int getItemCount(){
				return drafts.size();
			}
		}

		private static class DraftHolder extends RecyclerView.ViewHolder{
			final TextView title, subtitle;
			final ImageButton overflow;

			DraftHolder(View itemView){
				super(itemView);
				title=itemView.findViewById(R.id.draft_title);
				subtitle=itemView.findViewById(R.id.draft_subtitle);
				overflow=itemView.findViewById(R.id.draft_overflow);
			}
		}
	}

	/**
	 * MAMMUT: Composer 2.0 (§3) — the Scheduled tab is an empty placeholder
	 * until slice 3 wires it to the server's scheduled statuses.
	 */
	public static class ScheduledPageFragment extends Fragment{
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
			return inflater.inflate(R.layout.fragment_drafts_scheduled, container, false);
		}
	}
}
