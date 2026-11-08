package org.joinmastodon.android.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.assist.AssistContent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Property;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toast;

import org.joinmastodon.android.BuildConfig;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.statuses.GetStatusContext;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.AsyncRefresh;
import org.joinmastodon.android.model.FilterContext;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.StatusContext;
import org.joinmastodon.android.ui.ConversationChain;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.NavDestination;
import org.joinmastodon.android.ui.NavMenuController;
import org.joinmastodon.android.ui.Snackbar;
import org.joinmastodon.android.ui.ThreadAddressRule;
import org.joinmastodon.android.ui.displayitems.ExtendedFooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.FooterStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.HeaderStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.ReblogOrReplyLineCustomStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.SpoilerStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.TextStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.UnrollBranchMarkerStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.UnrollFooterStatusDisplayItem;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.ui.utils.UnreadNotificationCounter;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.Nav;
import me.grishka.appkit.FragmentStackActivity;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.MergeRecyclerAdapter;
import me.grishka.appkit.utils.SingleViewRecyclerAdapter;
import me.grishka.appkit.utils.V;

public class ThreadFragment extends StatusListFragment implements AssistContentProviderFragment, NavMenuController.Host{
	private Status mainStatus;
	private NavMenuController navMenuController;
	private TextView endLine;
	private FrameLayout replyContainer;
	private LinearLayout replyButton;
	private ImageView replyButtonAva;
	private TextView replyButtonText;
	private String asyncRefreshID;
	private Consumer<AsyncRefresh> asyncRefreshCallback=this::onAsyncRefreshFinished;
	private Snackbar moreRepliesSnackbar;
	private Runnable asyncRefreshPartialRunnable=this::checkAsyncRefreshAndMaybeShowSnackbar;
	private int prevNewRepliesCount;
	private HashSet<String> newReplyIDs=new HashSet<>();
	// MAMMUT: Reading Room (docs/READING-ROOM-SPEC.md) — the Unroll reading mode is a
	// flag on this fragment, never a second destination. The chain is recomputed from
	// the fetched set every time the conversation changes.
	private boolean unrollMode=false;
	private ConversationChain chain;
	private MenuItem unrollMenuItem;
	private int savedFirstVisiblePosition=-1;
	/** §5.4: a marker tap asks the conversation to open at a post; the restore runnable
	 *  posted by setUnrollMode(false) would otherwise win and drop the reader back where
	 *  they left the conversation.
	 */
	private String pendingConversationScrollId;
	private final Runnable unrollBackCallback=()->setUnrollMode(false);
	private boolean unrollBackRegistered;
	private int savedFirstVisibleOffset=0;
	private boolean highlightDecorationAdded=false;
	private NewRepliesHighlightDecoration highlightDecoration=new NewRepliesHighlightDecoration();
	private float highlightAlpha=0;
	private Animator highlightAlphaAnimator;
	private final Property<Void, Float> newRepliesHighlightProperty=new Property<>(Float.class, "fdsafdsa"){
		@Override
		public Float get(Void object){
			return highlightAlpha;
		}

		@Override
		public void set(Void object, Float value){
			highlightAlpha=value;
			list.invalidate();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setLayout(R.layout.fragment_thread);
		mainStatus=Parcels.unwrap(getArguments().getParcelable("status"));
		Account inReplyToAccount=Parcels.unwrap(getArguments().getParcelable("inReplyToAccount"));
		if(inReplyToAccount!=null)
			knownAccounts.put(inReplyToAccount.id, inReplyToAccount);
		data.add(mainStatus);
		onAppendItems(Collections.singletonList(mainStatus));
		// MAMMUT: Reading Room (docs/READING-ROOM-SPEC.md §3) — this screen is a
		// Conversation. The title says so; the subtitle carries the factual counts
		// ("14 posts · 5 people") once the context has been fetched.
		setTitle(R.string.nav_thread);
		updateSubtitle();
		setRefreshEnabled(false);
		setHasOptionsMenu(true);
	}

	@Override
	public void onDestroy(){
		if(asyncRefreshID!=null){
			AccountSessionManager.get(accountID).getApiController().cancelPollingAsyncRefresh(asyncRefreshID, asyncRefreshCallback);
		}
		super.onDestroy();
	}

	@Override
	public void onDestroyView(){
		moreRepliesSnackbar=null;
		if(asyncRefreshID!=null){
			contentView.removeCallbacks(asyncRefreshPartialRunnable);
		}
		if(highlightAlphaAnimator!=null){
			highlightAlphaAnimator.cancel();
		}
		super.onDestroyView();
	}

	@Override
	protected List<StatusDisplayItem> buildDisplayItems(Status s){
		if(unrollMode && chain!=null && chain.eligible){
			// Reading Room §5.1: only chain members are rendered, in reading order —
			// the rebuild loop iterates the chain, so a non-member never reaches here.
			int chainIndex=chain.indexOf(s.id);
			if(chainIndex>=0)
				return buildUnrolledPostItems(s, chainIndex);
		}
		List<StatusDisplayItem> items=StatusDisplayItem.buildItems(this, getActivity(), s, accountID, s, knownAccounts, StatusDisplayItem.FLAG_NO_IN_REPLY_TO);
		// Address line §2: the six-state grammar row sits above the author header,
		// reusing the reply-line row type (extended, not a look-alike). Built here,
		// with the display items — never computed at draw time (§7). Suppressed
		// inside Unroll: only the conversation path reaches this branch.
		ReblogOrReplyLineCustomStatusDisplayItem address=buildAddressLine(s);
		if(address!=null){
			address.index=0;
			items.add(0, address);
		}
		if(s.id.equals(mainStatus.id)){
			for(StatusDisplayItem item:items){
				item.fullWidth=true;
				if(item instanceof TextStatusDisplayItem text){
					text.textSelectable=!item.isQuote;
					text.largerFont=true;
				}
				else if(item instanceof FooterStatusDisplayItem footer)
					footer.hideCounts=true;
				else if(item instanceof SpoilerStatusDisplayItem spoiler){
					for(StatusDisplayItem subItem:spoiler.contentItems){
						if(subItem instanceof TextStatusDisplayItem text)
							text.textSelectable=!subItem.isQuote;
					}
				}
			}
			items.add(items.size()-1, new ExtendedFooterStatusDisplayItem(s.id, this, getActivity(), s.getContentStatus(), accountID));
		}
		return items;
	}

	// Reading Room §5.1-§5.3: the first chain post keeps its author header; later
	// posts render with FLAG_NO_HEADER, full width, reading typography — their author
	// context is carried by the gutter line and numbering instead of repeated avatars.
	private List<StatusDisplayItem> buildUnrolledPostItems(Status s, int chainIndex){
		int flags=StatusDisplayItem.FLAG_NO_IN_REPLY_TO;
		if(chainIndex>0)
			flags|=StatusDisplayItem.FLAG_NO_HEADER;
		List<StatusDisplayItem> items=StatusDisplayItem.buildItems(this, getActivity(), s, accountID, s, knownAccounts, flags);
		boolean focused=s.id.equals(mainStatus.id);
		for(StatusDisplayItem item:items){
			item.fullWidth=true;
			if(item instanceof TextStatusDisplayItem text){
				text.textSelectable=!item.isQuote;
				text.largerFont=true;
			}
			if(focused && item instanceof FooterStatusDisplayItem footer)
				footer.hideCounts=true;
			if(item instanceof SpoilerStatusDisplayItem spoiler){
				for(StatusDisplayItem subItem:spoiler.contentItems){
					if(subItem instanceof TextStatusDisplayItem text)
						text.textSelectable=!subItem.isQuote;
				}
			}
		}
		if(focused)
			items.add(items.size()-1, new ExtendedFooterStatusDisplayItem(s.id, this, getActivity(), s.getContentStatus(), accountID));
		return items;
	}

// Address line machinery (docs/ADDRESS-LINE-SPEC.md §1-§5)

	/**
	 * §2: build the six-state grammar row for one status, or null when the status
	 * is not a reply (§1.6 — no empty row, no placeholder). Reuses the reply-line
	 * display item; the visible string is clamped by the rule, the accessibility
	 * string carries the full name (§5).
	 */
	private ReblogOrReplyLineCustomStatusDisplayItem buildAddressLine(Status s){
		Status parentStatus=s.inReplyToId!=null ? knownStatuses.get(s.inReplyToId) : null;
		ThreadAddressRule.Input in=addressInputFor(s, parentStatus);
		ThreadAddressRule.Result r=ThreadAddressRule.evaluate(in);
		if(r.state==ThreadAddressRule.State.NONE)
			return null;
		Account parentAccount=resolveParentAccount(s, parentStatus);
		// §2: the row inset is the layout's own content inset — 16 dp from the
		// edge in full width, 16 dp inside the item (64 dp from the edge) in the
		// indented layout. No left outRect anywhere (§7).
		boolean fw=s.id.equals(mainStatus.id) || GlobalUserPreferences.fullWidthPosts;
		ReblogOrReplyLineCustomStatusDisplayItem item=new ReblogOrReplyLineCustomStatusDisplayItem(s.id, this, getActivity(),
				r.visible, parentAccount!=null ? parentAccount.emojis : null, 0, null, null, r.accessibility, null);
		item.addressLine=true;
		item.addressVisible=r.visible;
		item.addressAccessibility=r.accessibility;
		item.fullWidth=fw;
		item.leadingInsetPx=V.dp(fw ? 16 : 64);
		// Quiet register: the Mammut grape-neutral ramp lives in OnSurfaceVariant
		// (see the palette note in colors_custom.xml), same family as the reply
		// rail; colour may reinforce the state, never define it (§5). Resolved at
		// bind time so the row can be built before the activity exists.
		item.textColorAttr=R.attr.colorM3OnSurfaceVariant;
		return item;
	}

	/**
	 * §4: resolve the rule inputs once, when the display items are built. Parent
	 * name: inReplyToAccountId → knownAccounts, falling back to the parent status
	 * in the fetched context. An id is never rendered and a name never invented.
	 * Chain bounds are filled only when ConversationChain calls the chain
	 * eligible and the status is a member (§1.4/§1.5).
	 */
	private ThreadAddressRule.Input addressInputFor(Status s, Status parentStatus){
		ThreadAddressRule.Input in=new ThreadAddressRule.Input();
		in.reply=s.inReplyToAccountId!=null;
		if(!in.reply)
			return in;
		in.sameAuthor=s.account!=null && s.inReplyToAccountId.equals(s.account.id);
		Account parentAccount=resolveParentAccount(s, parentStatus);
		if(parentAccount!=null)
			in.parentName=!TextUtils.isEmpty(parentAccount.displayName) ? parentAccount.displayName : parentAccount.username;
		in.parentPostPresent=parentStatus!=null;
		if(in.sameAuthor && chain!=null && chain.eligible){
			int idx=chain.indexOf(s.id);
			if(idx>=0){
				in.chainIndex=idx;
				in.chainSize=chain.chain.size();
			}
		}
		in.tmplReplyingTo=getString(R.string.address_line_replying_to);
		in.tmplReplyingToNotShown=getString(R.string.address_line_replying_to_parent_not_shown);
		in.tmplReplyingToUnresolved=getString(R.string.address_line_replying_to_unresolved);
		in.tmplThreadNumbered=getString(R.string.address_line_thread_numbered);
		in.tmplThreadBare=getString(R.string.address_line_thread_bare);
		in.tmplA11yReplyingTo=getString(R.string.address_line_a11y_replying_to);
		in.tmplA11yReplyingToNotShown=getString(R.string.address_line_a11y_replying_to_parent_not_shown);
		in.tmplA11yReplyingToUnresolved=getString(R.string.address_line_a11y_replying_to_unresolved);
		in.tmplA11yThreadNumbered=getString(R.string.address_line_a11y_thread_numbered);
		in.tmplA11yThreadBare=getString(R.string.address_line_a11y_thread_bare);
		return in;
	}

	private Account resolveParentAccount(Status s, Status parentStatus){
		Account a=knownAccounts.get(s.inReplyToAccountId);
		if(a==null && parentStatus!=null)
			a=parentStatus.account;
		return a;
	}

	/**
	 * §4: the conversation can change under an already-built row — the context
	 * fetch resolves a parent that was unknown at first paint, or a new reply
	 * makes the chain eligible. Re-resolve every address row in place and rebind
	 * the ones whose wording changed; cheap, because it runs on conversation
	 * changes only, never per frame (§7).
	 */
	private void refreshAddressLines(){
		for(int i=0;i<displayItems.size();i++){
			StatusDisplayItem item=displayItems.get(i);
			if(!(item instanceof ReblogOrReplyLineCustomStatusDisplayItem line) || !line.addressLine)
				continue;
			Status s=getStatusByID(line.parentID);
			if(s==null)
				continue;
			Status parentStatus=s.inReplyToId!=null ? knownStatuses.get(s.inReplyToId) : null;
			ThreadAddressRule.Result r=ThreadAddressRule.evaluate(addressInputFor(s, parentStatus));
			if(r.state==ThreadAddressRule.State.NONE)
				continue;
			if(r.visible.equals(line.addressVisible) && Objects.equals(r.accessibility, line.addressAccessibility))
				continue;
			Account parentAccount=resolveParentAccount(s, parentStatus);
			line.updateAddressLine(r.visible, parentAccount!=null ? parentAccount.emojis : null, r.accessibility);
			adapter.notifyItemChanged(i);
		}
	}

	/**
	 * §3: "End of the {n} posts Mammut fetched" — only once the fetched list is
	 * final (loaded, no request in flight, not refreshing), only with the fetched
	 * count, never in Unroll.
	 */
	private void updateEndLine(){
		if(endLine==null)
			return;
		boolean show=loaded && !refreshing && currentRequest==null && !unrollMode && !data.isEmpty();
		if(show){
			int n=data.size();
			endLine.setText(getResources().getQuantityString(R.plurals.thread_end_line, n, n));
		}
		endLine.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	@Override
	protected void doLoadData(int offset, int count){
		updateEndLine(); // hidden while the fetched list is not final (§3)
		currentRequest=new GetStatusContext(mainStatus.id)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(StatusContext result){
						currentRequest=null;
						if(getActivity()==null)
							return;
						final ArrayList<StatusDisplayItem> prevDisplayItems;
						if(refreshing){
							prevDisplayItems=new ArrayList<>(displayItems);
							data.clear();
							displayItems.clear();
							data.add(mainStatus);
							onAppendItems(Collections.singletonList(mainStatus));
						}else if(result.asyncRefresh!=null){
							prevDisplayItems=null;
							if(BuildConfig.DEBUG)
								Toast.makeText(getActivity(), "Starting async refresh", Toast.LENGTH_SHORT).show();
							asyncRefreshID=result.asyncRefresh.id;
							AccountSessionManager.get(accountID).getApiController().startPollingAsyncRefresh(result.asyncRefresh, asyncRefreshCallback);
							contentView.postDelayed(asyncRefreshPartialRunnable, 10_000);
						}else{
							prevDisplayItems=null;
						}
						filterStatuses(result.descendants);
						filterStatuses(result.ancestors);
						if(footerProgress!=null)
							footerProgress.setVisibility(View.GONE);
						data.addAll(result.descendants);
						int prevCount=displayItems.size();
						onAppendItems(result.descendants);
						int count=displayItems.size();
						if(!refreshing)
							adapter.notifyItemRangeInserted(prevCount, count-prevCount);
						prependItems(result.ancestors, !refreshing);
						dataLoaded();
						// Reading Room: the chain can only be computed once the context is in
						// memory, and it is what decides whether the Unroll action exists and
						// what the subtitle counts. Without this the action stayed hidden until
						// some later event (a refresh, a new reply) happened to recount.
						onConversationChanged();
						if(refreshing){
							refreshDone();
							DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
								@Override
								public int getOldListSize(){
									return prevDisplayItems.size();
								}

								@Override
								public int getNewListSize(){
									return displayItems.size();
								}

								@Override
								public boolean areItemsTheSame(int oldItemPosition, int newItemPosition){
									StatusDisplayItem oldItem=prevDisplayItems.get(oldItemPosition);
									StatusDisplayItem newItem=displayItems.get(newItemPosition);
									return oldItem.parentID.equals(newItem.parentID) && oldItem.index==newItem.index && oldItem.getType()==newItem.getType();
								}

								@Override
								public boolean areContentsTheSame(int oldItemPosition, int newItemPosition){
									return true;
								}
							});
							newReplyIDs.clear();
							diff.dispatchUpdatesTo(new ListUpdateCallback(){
								@Override
								public void onInserted(int position, int count){
									if(position<displayItems.size()) // TODO figure out how this could possibly be a thing
										newReplyIDs.add(displayItems.get(position).parentID);
									else if(BuildConfig.DEBUG)
										throw new IllegalStateException("onInserted called with position="+position+" count="+count+", but list size is "+displayItems.size());
								}

								@Override
								public void onRemoved(int position, int count){}

								@Override
								public void onMoved(int fromPosition, int toPosition){}

								@Override
								public void onChanged(int position, int count, @Nullable Object payload){}
							});
							diff.dispatchUpdatesTo(adapter);
							if(!newReplyIDs.isEmpty()){
								if(highlightAlphaAnimator!=null)
									highlightAlphaAnimator.cancel();
								if(!highlightDecorationAdded){
									highlightDecorationAdded=true;
									list.addItemDecoration(highlightDecoration, 0);
								}
								highlightAlpha=0.25f;
								highlightAlphaAnimator=ObjectAnimator.ofFloat(null, newRepliesHighlightProperty, 0.25f, 0f);
								highlightAlphaAnimator.setDuration(2000);
								highlightAlphaAnimator.setStartDelay(500);
								highlightAlphaAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
								highlightAlphaAnimator.addListener(new AnimatorListenerAdapter(){
									@Override
									public void onAnimationEnd(Animator animation){
										highlightAlphaAnimator=null;
										highlightDecorationAdded=false;
										list.removeItemDecoration(highlightDecoration);
									}
								});
								highlightAlphaAnimator.start();
							}
						}else{
							list.scrollToPosition(displayItems.size()-count);
						}
						setRefreshEnabled(true);
						onConversationChanged();
					}
				})
				.exec(accountID);
	}

	private void filterStatuses(List<Status> statuses){
		AccountSessionManager.get(accountID).filterStatuses(statuses, FilterContext.THREAD);
	}

	@Override
	protected void onShown(){
		super.onShown();
		if(!getArguments().getBoolean("noAutoLoad") && !loaded && !dataLoading){
			dataLoading=true;
			doLoadData();
		}
		// MAMMUT: the navigation FAB is available in threads too
		UnreadNotificationCounter.load(accountID, (count, more)->navMenuController.setUnreadCount(count, more));
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		if(navMenuController!=null)
			navMenuController.closeMenu();
	}

	private final Runnable navMenuBackCallback=()->navMenuController.closeMenu();

	@Override
	public void onNavDestinationSelected(NavDestination destination){
		// Pop back to the home surface, then switch the destination there so the
		// existing per-destination state and scroll positions survive
		navMenuController.closeMenu();
		((FragmentStackActivity)getActivity()).removeFragment(this, true);
		getActivity().getWindow().getDecorView().post(()->{
			for(android.app.Fragment f:getFragmentManager().getFragments()){
				if(f instanceof HomeFragment hf){
					hf.setDestination(destination);
					break;
				}
			}
		});
	}

	@Override
	public void onNavComposeSelected(){
		navMenuController.closeMenu();
		Bundle args=new Bundle();
		args.putString("account", accountID);
		Nav.go(getActivity(), ComposeFragment.class, args);
	}

	@Override
	public void onNavMenuOpenChanged(boolean open){
		// Back closes the menu before any navigation happens
		if(open)
			addBackCallback(navMenuBackCallback);
		else
			removeBackCallback(navMenuBackCallback);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		// MAMMUT: navigation FAB overlay; the content container is a FrameLayout so the
		// menu scrim/panel float above the thread, same as on the home surface
		navMenuController=new NavMenuController(this);
		ViewGroup content=view.findViewById(me.grishka.appkit.R.id.appkit_loader_content);
		if(content instanceof FrameLayout){
			navMenuController.attach(content);
			navMenuController.setDestination(NavDestination.HOME);
			navMenuController.setScrollTarget(getList());
		}
		replyContainer=view.findViewById(R.id.reply_button_wrapper);
		replyButton=replyContainer.findViewById(R.id.reply_button);
		replyButtonText=replyButton.findViewById(R.id.reply_btn_text);
		replyButtonAva=replyButton.findViewById(R.id.avatar);
		replyButton.setOutlineProvider(OutlineProviders.roundedRect(20));
		replyButton.setClipToOutline(true);
		if(GlobalUserPreferences.customEmojiInNames){
			SpannableStringBuilder ssb=new SpannableStringBuilder(getString(R.string.reply_to_user, "{name}"));
			SpannableStringBuilder name=HtmlParser.parseCustomEmoji(mainStatus.account.displayName, mainStatus.account.emojis);
			int index=ssb.toString().indexOf("{name}");
			ssb.replace(index, index+6, name);
			replyButtonText.setText(ssb);
			UiUtils.loadCustomEmojiInTextView(replyButtonText);
		}else{
			replyButtonText.setText(getString(R.string.reply_to_user, mainStatus.account.displayName));
		}
		replyButtonAva.setOutlineProvider(OutlineProviders.OVAL);
		replyButtonAva.setClipToOutline(true);
		replyButton.setOnClickListener(v->openReply());
		Account self=AccountSessionManager.get(accountID).self;
		if(!TextUtils.isEmpty(self.avatar)){
			ViewImageLoader.loadWithoutAnimation(replyButtonAva, getResources().getDrawable(R.drawable.image_placeholder, getActivity().getTheme()), new UrlImageLoaderRequest(self.avatar, V.dp(24), V.dp(24)));
		}
		UiUtils.loadCustomEmojiInTextView(toolbarTitleView);
		setUpToolbarMenu();
		showContent();
		if(!loaded)
			footerProgress.setVisibility(View.VISIBLE);

		list.addItemDecoration(new ReplyLinesItemDecoration());
		list.addItemDecoration(new UnrollReadingLineDecoration());
	}

	protected void onStatusCreated(Status status){
		if(status.inReplyToId!=null && getStatusByID(status.inReplyToId)!=null){
			if(!unrollMode)
				onAppendItems(Collections.singletonList(status));
			data.add(status);
			// The chain is computed from what is in memory at bind time; a new reply
			// may extend it, and the counts and the Unroll action must stay honest.
			onConversationChanged();
		}
	}

	@Override
	protected void removeStatus(Status status){
		super.removeStatus(status);
		// §6: a member deleted between fetch and open shortens the chain rather than
		// showing a hole; if it drops below eligibility the mode exits quietly.
		onConversationChanged();
	}

	@Override
	public boolean isItemEnabled(StatusDisplayItem item){
		return item.isQuote || !item.parentID.equals(mainStatus.id);
	}

	@Override
	protected RecyclerView.Adapter getAdapter(){
		MergeRecyclerAdapter a=new MergeRecyclerAdapter();
		a.addAdapter(super.getAdapter());

		// Address line §3: the honest end line replaces the purely decorative end
		// mark — "End of the {n} posts Mammut fetched", only once the list is
		// exhausted, never in Unroll (Reading Room has its own footer).
		endLine=new TextView(getActivity());
		endLine.setTextAppearance(getActivity(), R.style.m3_body_small);
		endLine.setTextColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurfaceVariant));
		endLine.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
		endLine.setPaddingRelative(V.dp(16), V.dp(12), V.dp(16), V.dp(12));
		endLine.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		endLine.setVisibility(View.GONE);
		a.addAdapter(new SingleViewRecyclerAdapter(endLine));
		updateEndLine();

		return a;
	}

	@Override
	protected boolean needDividerForExtraItem(View child, View bottomSibling, RecyclerView.ViewHolder holder, RecyclerView.ViewHolder siblingHolder){
		return bottomSibling==endLine;
	}

	@Override
	protected void onErrorRetryClick(){
		if(preloadingFailed){
			preloadingFailed=false;
			V.setVisibilityAnimated(footerProgress, View.VISIBLE);
			V.setVisibilityAnimated(footerError, View.GONE);
			doLoadData();
			return;
		}
		super.onErrorRetryClick();
	}

	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		super.onApplyWindowInsets(UiUtils.applyBottomInsetToFixedView(replyContainer, insets));
	}

	/**
	 * Reading Room §5.6 — Unroll ↔ "All N posts" lives in the app bar. Appkit renders no
	 * fragment options menu, so the action goes into the toolbar's own menu and its state
	 * is set directly rather than through onPrepareOptionsMenu, which never ran.
	 */
	private void setUpToolbarMenu(){
		Toolbar tb=getToolbar();
		if(tb==null)
			return;
		tb.inflateMenu(R.menu.thread_toolbar);
		unrollMenuItem=tb.getMenu().findItem(R.id.unroll);
		tb.setOnMenuItemClickListener(item->{
			if(item.getItemId()==R.id.unroll){
				setUnrollMode(!unrollMode);
				return true;
			}
			return false;
		});
		updateUnrollMenuItem();
	}

	private void updateUnrollMenuItem(){
		if(unrollMenuItem==null)
			return;
		if(unrollMode){
			unrollMenuItem.setVisible(true);
			int total=chain!=null ? chain.totalPosts : data.size();
			unrollMenuItem.setTitle(getResources().getQuantityString(R.plurals.thread_all_posts, total, total));
		}else{
			unrollMenuItem.setTitle(R.string.thread_unroll_action);
			unrollMenuItem.setVisible(chain!=null && chain.eligible);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(BuildConfig.DEBUG && "Simulate new replies".contentEquals(item.getTitle())){
			ArrayList<Status> toRemove=new ArrayList<>();
			Random r=ThreadLocalRandom.current();
			for(Status s:data){
				if(s!=mainStatus && r.nextBoolean())
					toRemove.add(s);
			}
			for(Status s:toRemove)
				removeStatus(s);
			showMoreRepliesSnackbar();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

// Reading Room mode machinery (docs/READING-ROOM-SPEC.md §3-§6)

	/** Recompute the chain from what is in memory, refresh the subtitle, and offer or withdraw Unroll. */
	private void onConversationChanged(){
		chain=ConversationChain.build(data, mainStatus);
		boolean wasUnroll=unrollMode;
		if(unrollMode && !chain.eligible)
			unrollMode=false; // §6: a missing link shortens the chain; below 3 members the mode quietly exits
		updateSubtitle();
		if(wasUnroll || unrollMode)
			rebuildList();
		updateUnrollMenuItem();
		// Address line §4: the conversation changed under already-built rows —
		// re-resolve parents and chain bounds, and re-state the honest end line.
		refreshAddressLines();
		updateEndLine();
	}

	@Override
	public void refreshDone(){
		super.refreshDone();
		// The refresh kept `loaded` true while the end line stayed hidden; the
		// count is final again once the spinner stops (§3).
		updateEndLine();
	}

	private void updateSubtitle(){
		if(unrollMode && chain!=null && chain.eligible){
			// §5.5: "Unrolled · Mara's 4 posts"
			int n=chain.chain.size();
			setSubtitle(getResources().getQuantityString(R.plurals.thread_unroll_subtitle, n, chain.authorDisplayName, n));
		}else{
			// §3.2: factual counts over the fetched context, including the focused post
			HashSet<String> people=new HashSet<>();
			for(Status s:data){
				if(s.account!=null)
					people.add(s.account.id);
			}
			int posts=data.size();
			setSubtitle(getResources().getQuantityString(R.plurals.thread_posts_count, posts, posts)
					+" · "
					+getResources().getQuantityString(R.plurals.thread_people_count, people.size(), people.size()));
		}
	}

	private void setUnrollMode(boolean on){
		if(on && (chain==null || !chain.eligible))
			return; // §7: no automatic unroll — and no manual one without a chain
		if(unrollMode==on)
			return;
		unrollMode=on;
		if(on && list!=null && list.getLayoutManager() instanceof LinearLayoutManager llm){
			savedFirstVisiblePosition=llm.findFirstVisibleItemPosition();
			View first=list.getChildAt(0);
			savedFirstVisibleOffset=first==null ? 0 : first.getTop();
		}
		updateUnrollBackCallback();
		rebuildList();
		updateSubtitle();
		updateUnrollMenuItem();
		updateEndLine(); // §3: the end line is suppressed for the whole Unroll session
		// §5.9: entering starts at the first chain post; exiting restores the position
		list.post(()->{
			if(list==null)
				return;
			if(unrollMode){
				list.scrollToPosition(0);
			}else if(pendingConversationScrollId!=null){
				// §5.4: a marker tap names the post to open at; that beats the restore.
				String id=pendingConversationScrollId;
				pendingConversationScrollId=null;
				scrollToStatusIn(displayItems, id);
			}else if(savedFirstVisiblePosition>=0 && list.getLayoutManager() instanceof LinearLayoutManager llm){
				llm.scrollToPositionWithOffset(savedFirstVisiblePosition, savedFirstVisibleOffset);
			}
		});
	}

	private void rebuildList(){
		displayItems.clear();
		if(unrollMode && chain!=null && chain.eligible){
			for(Status s:chain.chain){
				List<StatusDisplayItem> postItems=buildDisplayItems(s);
				populateNestedQuotes(postItems);
				displayItems.addAll(postItems);
				Integer replies=chain.sideBranchCounts.get(s.id);
				if(replies!=null && replies>0){
					// §5.4: one inline marker per post with off-chain replies
					String label=getResources().getQuantityString(R.plurals.thread_side_branch_marker, replies, replies);
					displayItems.add(new UnrollBranchMarkerStatusDisplayItem(s.id, this, getActivity(), label, ()->showInFullConversation(s.id)));
				}
			}
			// §5.7: end-of-story footer, with the side-branch line only when applicable
			String footerTitle=getString(R.string.thread_unroll_footer_end, chain.authorDisplayName);
			String footerBody=chain.sideBranchTotal>0
					? getResources().getQuantityString(R.plurals.thread_unroll_footer_side_branches, chain.sideBranchTotal, chain.sideBranchTotal)
					: null;
			displayItems.add(new UnrollFooterStatusDisplayItem("unroll_footer", this, getActivity(), footerTitle, footerBody));
		}else{
			for(Status s:data){
				List<StatusDisplayItem> postItems=buildDisplayItems(s);
				populateNestedQuotes(postItems);
				displayItems.addAll(postItems);
			}
		}
		adapter.notifyDataSetChanged();
	}

	private void showInFullConversation(String statusID){
		if(unrollMode){
			// Resolve the position in the conversation list, which setUnrollMode(false)
			// rebuilds synchronously, and let its posted scroll do the moving.
			pendingConversationScrollId=statusID;
			setUnrollMode(false);
		}else{
			scrollToStatusIn(displayItems, statusID);
		}
	}

	private void scrollToStatusIn(List<StatusDisplayItem> items, String statusID){
		if(list==null)
			return;
		for(int i=0;i<items.size();i++){
			if(statusID.equals(items.get(i).parentID)){
				list.scrollToPosition(i);
				return;
			}
		}
	}

	@Override
	public void onDetach(){
		if(unrollBackRegistered){
			removeBackCallback(unrollBackCallback);
			unrollBackRegistered=false;
		}
		super.onDetach();
	}

	private void updateUnrollBackCallback(){
		if(unrollMode && !unrollBackRegistered){
			addBackCallback(unrollBackCallback);
			unrollBackRegistered=true;
		}else if(!unrollMode && unrollBackRegistered){
			removeBackCallback(unrollBackCallback);
			unrollBackRegistered=false;
		}
	}

	private void openReply(){
		maybeShowPreReplySheet(mainStatus, ()->{
			Bundle args=new Bundle();
			args.putString("account", accountID);
			args.putParcelable("replyTo", Parcels.wrap(mainStatus));
			args.putBoolean("fromThreadFragment", true);
			Nav.go(getActivity(), ComposeFragment.class, args);
		});
	}

	@Override
	protected void drawDivider(View child, View bottomSibling, RecyclerView.ViewHolder holder, RecyclerView.ViewHolder siblingHolder, RecyclerView parent, Canvas c, Paint paint){
		if(unrollMode && (holder instanceof UnrollBranchMarkerStatusDisplayItem.Holder || siblingHolder instanceof UnrollBranchMarkerStatusDisplayItem.Holder))
			return; // the marker belongs to the post above it; no divider between them
		if(holder instanceof StatusDisplayItem.Holder<?> statusHolder && siblingHolder instanceof StatusDisplayItem.Holder<?> siblingStatusHolder){
			Status siblingStatus=getStatusByID(siblingStatusHolder.getItemID());
			if(siblingStatus==null)
				return;
			if(statusHolder.getItemID().equals(siblingStatus.inReplyToId) && siblingStatus!=mainStatus && !statusHolder.getItemID().equals(mainStatus.id))
				return;
		}
		super.drawDivider(child, bottomSibling, holder, siblingHolder, parent, c, paint);
	}

	private Status findPreviousStatus(String id){
		for(int i=0;i<data.size();i++){
			if(data.get(i).id.equals(id))
				return i>0 ? data.get(i-1) : null;
		}
		return null;
	}

	private Status findNextStatus(String id){
		for(int i=0;i<data.size();i++){
			if(data.get(i).id.equals(id))
				return i<data.size()-1 ? data.get(i+1) : null;
		}
		return null;
	}

	@Override
	public void onProvideAssistContent(AssistContent content){
		content.setWebUri(Uri.parse(mainStatus.url));
	}

	@Override
	public void navigateToStatus(Status status){
		if(status.id.equals(mainStatus.id)){
			shakeListView();
			return;
		}
		super.navigateToStatus(status);
	}

	private void onAsyncRefreshFinished(AsyncRefresh ar){
		asyncRefreshID=null;
		if(getActivity()==null)
			return;
		contentView.removeCallbacks(asyncRefreshPartialRunnable);
		if(ar.resultCount>prevNewRepliesCount){
			showMoreRepliesSnackbar();
		}else if(BuildConfig.DEBUG && ar.resultCount==0){
			Toast.makeText(getActivity(), "Async refresh finished without any results", Toast.LENGTH_SHORT).show();
		}
	}

	private void checkAsyncRefreshAndMaybeShowSnackbar(){
		int results=AccountSessionManager.get(accountID).getApiController().getAsyncRefreshResultCount(asyncRefreshID);
		if(results>0){
			showMoreRepliesSnackbar();
			prevNewRepliesCount=results;
		}
	}

	private void showMoreRepliesSnackbar(){
		if(moreRepliesSnackbar!=null || getActivity()==null)
			return;
		moreRepliesSnackbar=new Snackbar.Builder(getActivity())
				.setText(R.string.more_replies_found)
				.setAction(R.string.show, ()->{
					moreRepliesSnackbar.dismiss();
					refresh();
				})
				.setPersistent()
				.create();
		moreRepliesSnackbar.setDismissListener(()->moreRepliesSnackbar=null);
		showSnackbar(moreRepliesSnackbar);
	}

	public void showSnackbar(Snackbar sb){
		sb.showInView(contentWrap);
	}

	private class ReplyLinesItemDecoration extends RecyclerView.ItemDecoration{
		private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
		private Rect tmpRect=new Rect();

		@Override
		public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
			if(unrollMode)
				return; // the conversation reply lines don't describe the unrolled story
			String currentID=null;
			boolean connectUp=false, connectToRoot=false, connectReply=false;
			paint.setColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OutlineVariant));
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(V.dp(2));
			paint.setStrokeCap(Paint.Cap.ROUND);
			for(int i=0;i<parent.getChildCount();i++){
				View child=parent.getChildAt(i);
				if(!(parent.getChildViewHolder(child) instanceof StatusDisplayItem.Holder<?> holder) || holder.getItemID().equals(mainStatus.id))
					continue;
				// MAMMUT: the reply line lives in the avatar gutter. A full-width row puts its
				// text exactly where that gutter is, so the line would run through the words
				// instead of beside them — draw no line rather than a line through the post.
				if(holder.isFullWidth())
					continue;
				String itemID=holder.getItemID();
				if(!Objects.equals(currentID, itemID)){
					currentID=itemID;
					Status current=getStatusByID(currentID);
					Status previous=findPreviousStatus(currentID);
					Status next=findNextStatus(currentID);
					if(current==null)
						continue;

					connectUp=previous!=null && previous.id.equals(current.inReplyToId);
					connectToRoot=mainStatus.id.equals(current.inReplyToId);
					connectReply=next!=null && itemID.equals(next.inReplyToId);
				}

				if(!connectUp && !connectToRoot && !connectReply)
					continue;

				float lineX=V.dp(36);
				paint.setAlpha(Math.round(255*child.getAlpha()));
				c.save();
				parent.getDecoratedBoundsWithMargins(child, tmpRect);
				int topMargin=child.getTop()-tmpRect.top;
				int bottomMargin=tmpRect.bottom-child.getBottom();
				float top=child.getY()-topMargin;
				float bottom=child.getY()+child.getHeight()+bottomMargin;
				c.clipRect(child.getX(), top, child.getX()+child.getWidth(), bottom);
				if(holder instanceof HeaderStatusDisplayItem.Holder){
					if(connectUp || connectToRoot){
						c.drawLine(lineX, top-V.dp(2), lineX, top+V.dp(14), paint);
					}
					if(connectReply){
						c.drawLine(lineX, top+V.dp(62), lineX, bottom+V.dp(2), paint);
					}
				}else if(connectReply){
					c.drawLine(lineX, top, lineX, bottom, paint);
				}
				c.restore();
			}
		}
	}

	private class UnrollReadingLineDecoration extends RecyclerView.ItemDecoration{
		private final Paint linePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint numberPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Rect tmpRect=new Rect();

		UnrollReadingLineDecoration(){
			linePaint.setStyle(Paint.Style.STROKE);
			linePaint.setStrokeWidth(V.dp(2));
			linePaint.setStrokeCap(Paint.Cap.ROUND);
			numberPaint.setTextSize(V.dp(11));
		}

		@Override
		public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
			// Reading Room §5.2/§5.3: chain posts (and their side-branch markers) give
			// up a strip of the left edge for the reading line and the quiet numbering.
			if(!unrollMode || chain==null || !chain.eligible)
				return;
			if(parent.getChildViewHolder(view) instanceof StatusDisplayItem.Holder<?> holder && chain.indexOf(holder.getItemID())>=0)
				outRect.left=V.dp(44);
		}

		@Override
		public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
			if(!unrollMode || chain==null || !chain.eligible || getActivity()==null)
				return;
			// Grape neutral: the Mammut neutral ramp is built from Dusty Grape, so the
			// outline-variant/OnSurfaceVariant pair is the quiet grape register — the
			// same family the conversation reply lines use.
			linePaint.setColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OutlineVariant));
			numberPaint.setColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurfaceVariant));
			int total=chain.chain.size();
			float lineX=V.dp(30);
			for(int i=0;i<parent.getChildCount();i++){
				View child=parent.getChildAt(i);
				if(!(parent.getChildViewHolder(child) instanceof StatusDisplayItem.Holder<?> holder))
					continue;
				int chainIndex=chain.indexOf(holder.getItemID());
				if(chainIndex<0)
					continue;
				parent.getDecoratedBoundsWithMargins(child, tmpRect);
				// The line runs through every row of the chain posts; because the rows
				// tile the vertical space it connects consecutive posts and stops at
				// the last one, never implying a stranger's reply joined the story.
				linePaint.setAlpha(Math.round(255*child.getAlpha()));
				c.drawLine(lineX, tmpRect.top, lineX, tmpRect.bottom, linePaint);
				// §5.3: numbering on the first row of each post only — "1/4", quiet,
				// smaller than body text, never a second timeline column.
				int position=holder.getBindingAdapterPosition();
				if(position>0 && position<=displayItems.size() && holder.getItemID().equals(displayItems.get(position-1).parentID))
					continue;
				numberPaint.setAlpha(Math.round(255*child.getAlpha()));
				String number=(chainIndex+1)+"/"+total;
				float numberY=tmpRect.top+V.dp(4)-numberPaint.ascent();
				c.drawText(number, lineX-V.dp(6)-numberPaint.measureText(number), numberY, numberPaint);
			}
		}
	}

	private class NewRepliesHighlightDecoration extends RecyclerView.ItemDecoration{
		private final Paint paint=new Paint();

		@Override
		public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
			paint.setColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3Primary));
			for(int i=0;i<parent.getChildCount();i++){
				View child=parent.getChildAt(i);
				if(!(parent.getChildViewHolder(child) instanceof StatusDisplayItem.Holder<?> holder))
					continue;
				String id=holder.getItemID();
				if(newReplyIDs.contains(id)){
					paint.setAlpha(Math.round(highlightAlpha*child.getAlpha()*255));
					parent.getDecoratedBoundsWithMargins(child, tmpRect);
					c.drawRect(tmpRect, paint);
				}
			}
		}
	}

	// MOSHIDON:
	@Override
	protected FilterContext getFilterContext() {
		return FilterContext.THREAD;
	}

	@Override
	public Uri getWebUri(Uri.Builder base) {
		return Uri.parse(mainStatus.url);
	}
}
