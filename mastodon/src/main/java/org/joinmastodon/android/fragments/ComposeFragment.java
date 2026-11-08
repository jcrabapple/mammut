package org.joinmastodon.android.fragments;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.icu.text.BreakIterator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.twitter.twittertext.TwitterTextEmojiRegex;

import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonErrorResponse;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.requests.statuses.CreateStatus;
import org.joinmastodon.android.api.requests.statuses.EditStatus;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.drafts.ComposeDraft;
import org.joinmastodon.android.drafts.DraftAttachment;
import org.joinmastodon.android.drafts.DraftStore;
import org.joinmastodon.android.drafts.Segment;
import org.joinmastodon.android.events.StatusCountersUpdatedEvent;
import org.joinmastodon.android.events.StatusCreatedEvent;
import org.joinmastodon.android.events.StatusUpdatedEvent;
import org.joinmastodon.android.fragments.account_list.AccountSearchFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Emoji;
import org.joinmastodon.android.model.EmojiCategory;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.Mention;
import org.joinmastodon.android.model.Preferences;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.StatusPrivacy;
import org.joinmastodon.android.model.StatusQuotePolicy;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.CustomEmojiPopupKeyboard;
import org.joinmastodon.android.ui.ExtendedPopupMenu;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.PopupKeyboard;
import org.joinmastodon.android.ui.displayitems.InlineStatusStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.ui.drawables.SpoilerStripesDrawable;
import org.joinmastodon.android.ui.sheets.ComposerVisibilitySheet;
import org.joinmastodon.android.ui.sheets.ListItemsSheet;
import org.joinmastodon.android.ui.text.ComposeAutocompleteSpan;
import org.joinmastodon.android.ui.text.ComposeHashtagOrMentionSpan;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.SimpleTextWatcher;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.joinmastodon.android.ui.viewcontrollers.ComposeAutocompleteViewController;
import org.joinmastodon.android.ui.viewcontrollers.ComposeLanguageAlertViewController;
import org.joinmastodon.android.ui.viewcontrollers.ComposeMediaViewController;
import org.joinmastodon.android.ui.viewcontrollers.ComposePollViewController;
import org.joinmastodon.android.ui.views.ComposeEditText;
import org.joinmastodon.android.ui.views.CustomScrollView;
import org.joinmastodon.android.ui.views.NestedRecyclerScrollView;
import org.joinmastodon.android.ui.views.SizeListenerLinearLayout;
import org.joinmastodon.android.ui.views.StatusView;
import org.joinmastodon.android.ui.views.TopBarsScrollAwayLinearLayout;
import org.joinmastodon.android.utils.ViewImageLoaderHolderTarget;
import org.joinmastodon.android.utils.Tracking;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.fragments.CustomTransitionsFragment;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.CubicBezierInterpolator;
import me.grishka.appkit.utils.V;

public class ComposeFragment extends MastodonToolbarFragment implements ComposeEditText.SelectionListener, CustomTransitionsFragment{

	private static final int MEDIA_RESULT=717;
	public static final int IMAGE_DESCRIPTION_RESULT=363;
	private static final int AUTOCOMPLETE_ACCOUNT_RESULT=779;
	private static final String TAG="ComposeFragment";

	private static final Pattern MENTION_PATTERN=Pattern.compile("(^|[^\\/\\w])@(([a-z0-9_]+)@[a-z0-9\\.\\-]+[a-z0-9]+)", Pattern.CASE_INSENSITIVE);

	// from https://github.com/mastodon/mastodon-ios/blob/main/Mastodon/Helper/MastodonRegex.swift
	private static final Pattern AUTO_COMPLETE_PATTERN=Pattern.compile("(?<!\\w)(?:@([a-z0-9_]+)(@[a-z0-9_\\.\\-]*)?|#([^\\s.]+)|:([a-z0-9_]+))", Pattern.CASE_INSENSITIVE);
	private static final Pattern HIGHLIGHT_PATTERN=Pattern.compile("(?<!\\w)(?:@([a-zA-Z0-9_]+)(@[a-zA-Z0-9_.-]+)?|#([^\\s.]+))");

	@SuppressLint("NewApi") // this class actually exists on 6.0
	private final BreakIterator breakIterator=BreakIterator.getCharacterInstance();

	public TopBarsScrollAwayLinearLayout mainLayout;
	private SizeListenerLinearLayout contentView;
	private TextView selfName, selfUsername;
	private ImageView selfAvatar;
	private Account self;
	private String instanceDomain;

	private ComposeEditText mainEditText;
	private TextView charCounter;
	private String accountID;
	private int charCount, charLimit, trimmedCharCount;

	private ImageButton mediaBtn, pollBtn, emojiBtn, spoilerBtn, languageBtn;
	private FrameLayout replyWrap;
	private LinearLayout visibilityBtn;
	private TextView visibilityText1, visibilityText2, visibilityCurrentText;
	private LinearLayout bottomBar;
	private View autocompleteDivider;
	private FrameLayout quotedPostWrap;

	private List<EmojiCategory> customEmojis;
	private CustomEmojiPopupKeyboard emojiKeyboard;
	private Status replyTo;
	private Status quotedStatus;
	private String initialText;
	private String uuid;
	private EditText spoilerEdit;
	private View spoilerWrap;
	private boolean hasSpoiler;
	private ProgressBar sendProgress;
	private View sendingOverlay;
	private WindowManager wm;
	private StatusPrivacy statusVisibility=StatusPrivacy.PUBLIC;
	private StatusQuotePolicy statusQuotePolicy=StatusQuotePolicy.PUBLIC;
	private ComposeAutocompleteSpan currentAutocompleteSpan;
	private FrameLayout mainEditTextWrap;
	private ComposeLanguageAlertViewController.SelectedOption postLang;

	private ComposeAutocompleteViewController autocompleteViewController;
	private ComposePollViewController pollViewController=new ComposePollViewController(this);
	private ComposeMediaViewController mediaViewController=new ComposeMediaViewController(this);
	public Instance instance;

	public Status editingStatus;
	private boolean creatingView;
	private boolean ignoreSelectionChanges=false;
	private MenuItem publishButton;
	private boolean wasDetached;

	private BackgroundColorSpan overLimitBG;
	private ForegroundColorSpan overLimitFG;

	private Runnable emojiKeyboardHider;
	private Runnable sendingBackButtonBlocker=()->{};
	private Runnable discardConfirmationCallback=this::confirmDiscardDraftBackCallback;
	private boolean prevHadDraft;
	private boolean keyboardVisible;

	// MAMMUT: Composer 2.0 (§2/§3) — draft autosave and resume. The composer
	// holds one draftId for its lifetime so repeat saves update one draft;
	// editing an existing status never touches any of this.
	private DraftStore draftStore;
	private String draftId;
	private ComposeDraft draftToRestore;
	/**
	 * MAMMUT: Composer 2.0 (§3) — true once a resumed draft's explicit
	 * visibility has been applied. Replaces the old restoringDraft latch,
	 * which was never reset and so suppressed the account-default visibility
	 * override for the rest of the fragment's life (review N3): the draft's
	 * visibility must win over defaults for this session, and only until
	 * the user starts a fresh composer.
	 */
	private boolean restoredDraftVisibility;
	/**
	 * MAMMUT: Composer 2.0 (§4) — the persisted per-segment idempotency key.
	 * Seeded from a resumed draft, generated on the first snapshot otherwise,
	 * so a retry from the draft reuses the same CreateStatus key.
	 */
	private String draftIdempotencyKey;
	/**
	 * MAMMUT: Composer 2.0 (§1/§6) — reply/quote context ids carried by a
	 * resumed draft. Slice 1 cannot rebuild the in-memory Status objects
	 * from ids (that is threads-slice work), but re-saving the resumed
	 * draft must not wipe the context from the model.
	 */
	private String restoredReplyToStatusId, restoredQuotedStatusId;
	/**
	 * Set after a successful publish or an explicit, confirmed discard.
	 * Volatile: read by the draft-save runnable on the worker thread.
	 */
	private volatile boolean finished;

	// MAMMUT: Composer 2.0 (§4) — the thread. The first segment is the
	// existing main surface (mainEditText, the poll and the bottom-bar
	// buttons); segments 2..n are cards in the segment list. A single post
	// is a thread of one: there is no mode switch. editingStatus never gains
	// any of this — the edit path stays single-segment.
	private final ArrayList<ThreadSegment> extraSegments=new ArrayList<>();
	private ViewGroup segmentsContainer;
	private View addSegmentBtn;
	private View segmentOneHeader;
	private TextView segmentOneNumber;
	private ImageButton segmentOneOverflow;
	/** "Posting 2 of 4" — lives on the sending overlay */
	private TextView postingProgressText;
	/** The segment the bottom-bar buttons act on; 0 is the main surface */
	private int activeSegmentIndex;
	/**
	 * MAMMUT (§4) — id of the most recently published segment of this
	 * session. A retry after a partial failure chains the remaining
	 * segments below it, and the saved tail draft records it as its
	 * replyToStatusId.
	 */
	private String threadAnchorId;
	/**
	 * MAMMUT (§4) — set once a partial publish has written the tail draft
	 * to the store. From then on the fire-and-forget autosave must not
	 * touch that draft: it is the retry artifact, carrying the tail's own
	 * per-segment idempotency keys, and a later composer-level save could
	 * only rebind or prepend state it must not carry (review M3). Only a
	 * successfully landed write sets this — a failed tail save must not
	 * block the next chance to persist.
	 */
	private boolean tailDraftSaved;

	public ComposeFragment(){
		super(R.layout.toolbar_fragment_with_progressbar);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setRetainInstance(true);

		accountID=getArguments().getString("account");
		AccountSession session=AccountSessionManager.getInstance().getAccount(accountID);
		self=session.self;
		instanceDomain=session.domain;
		customEmojis=AccountSessionManager.getInstance().getCustomEmojis(instanceDomain);
		instance=AccountSessionManager.getInstance().getInstanceInfo(instanceDomain);
		if(getArguments().containsKey("editStatus")){
			editingStatus=Parcels.unwrap(getArguments().getParcelable("editStatus"));
		}
		if(instance==null){
			Nav.finish(this);
			return;
		}
		draftStore=new DraftStore(MastodonApp.context, accountID);
		if(savedInstanceState!=null){
			draftId=savedInstanceState.getString("draftId");
		}else if(getArguments().containsKey("draftId")){
			// MAMMUT: Composer 2.0 (§3) — resuming a draft from the drafts screen
			draftId=getArguments().getString("draftId");
			draftToRestore=draftStore.load(draftId);
			if(draftToRestore==null){
				draftId=null;
			}else{
				// §4: reuse the persisted per-segment idempotency key, and
				// keep the reply/quote context ids in the model across
				// re-saves: the reply id chains the publish (review M4),
				// the quote id survives for the warning dialog.
				if(!draftToRestore.segments.isEmpty())
					draftIdempotencyKey=draftToRestore.segments.get(0).idempotencyKey;
				restoredReplyToStatusId=draftToRestore.replyToStatusId;
				restoredQuotedStatusId=draftToRestore.quotedStatusId;
			}
		}
		if(customEmojis.isEmpty()){
			AccountSessionManager.getInstance().updateInstanceInfo(instanceDomain);
		}

		if(instance.maxTootChars>0)
			charLimit=instance.maxTootChars;
		else if(instance.configuration!=null && instance.configuration.statuses!=null && instance.configuration.statuses.maxCharacters>0)
			charLimit=instance.configuration.statuses.maxCharacters;
		else
			charLimit=500;

		setTitle(editingStatus==null ? R.string.new_post : R.string.edit_post);
		if(savedInstanceState!=null)
			postLang=Parcels.unwrap(savedInstanceState.getParcelable("postLang"));

		if(getArguments().containsKey("quote"))
			quotedStatus=Parcels.unwrap(getArguments().getParcelable("quote"));
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		mediaViewController.cancelAllUploads();
		removeBackCallback(emojiKeyboardHider);
		removeBackCallback(sendingBackButtonBlocker);
		removeBackCallback(discardConfirmationCallback);
	}

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		setHasOptionsMenu(true);
		wm=activity.getSystemService(WindowManager.class);

		overLimitBG=new BackgroundColorSpan(UiUtils.getThemeColor(activity, R.attr.colorM3ErrorContainer));
		overLimitFG=new ForegroundColorSpan(UiUtils.getThemeColor(activity, R.attr.colorM3Error));
	}

	@Override
	public void onDetach(){
		wasDetached=true;
		super.onDetach();
	}

	@Override
	public View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		creatingView=true;
		emojiKeyboard=new CustomEmojiPopupKeyboard(getActivity(), customEmojis, instanceDomain);
		emojiKeyboard.setListener(new CustomEmojiPopupKeyboard.Listener(){
			@Override
			public void onEmojiSelected(Emoji emoji){
				onCustomEmojiClick(emoji);
			}

			@Override
			public void onBackspace(){
				getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
			}
		});
		emojiKeyboardHider=emojiKeyboard::hide;

		View view=inflater.inflate(R.layout.fragment_compose, container, false);
		mainLayout=view.findViewById(R.id.compose_main_ll);
		mainEditText=view.findViewById(R.id.toot_text);
		mainEditTextWrap=view.findViewById(R.id.toot_text_wrap);
		charCounter=view.findViewById(R.id.char_counter);
		charCounter.setText(String.valueOf(charLimit));

		selfName=view.findViewById(R.id.name);
		selfUsername=view.findViewById(R.id.username);
		selfAvatar=view.findViewById(R.id.avatar);
		HtmlParser.setTextWithCustomEmoji(selfName, self.displayName, self.emojis);
		selfUsername.setText('@'+self.username+'@'+instanceDomain);
		if(self.avatar!=null)
			ViewImageLoader.load(selfAvatar, null, new UrlImageLoaderRequest(self.avatar));
		selfAvatar.setOutlineProvider(OutlineProviders.roundedRect(12));
		selfAvatar.setClipToOutline(true);
		bottomBar=view.findViewById(R.id.bottom_bar);

		mediaBtn=view.findViewById(R.id.btn_media);
		pollBtn=view.findViewById(R.id.btn_poll);
		emojiBtn=view.findViewById(R.id.btn_emoji);
		spoilerBtn=view.findViewById(R.id.btn_spoiler);
		visibilityBtn=view.findViewById(R.id.btn_visibility);
		visibilityText1=view.findViewById(R.id.visibility_text1);
		visibilityText2=view.findViewById(R.id.visibility_text2);
		visibilityCurrentText=visibilityText1;
		languageBtn=view.findViewById(R.id.btn_language);
		replyWrap=view.findViewById(R.id.reply_wrap);
		quotedPostWrap=view.findViewById(R.id.quoted_post_wrap);

		mediaBtn.setOnClickListener(v->openFilePicker(false));
		if(UiUtils.isPhotoPickerAvailable()){
			mediaBtn.setOnLongClickListener(v->{
				openFilePicker(true);
				return true;
			});
		}
		pollBtn.setOnClickListener(v->togglePoll());
		emojiBtn.setOnClickListener(v->emojiKeyboard.toggleKeyboardPopup(activeSegmentIndex==0 ? mainEditText : extraSegments.get(activeSegmentIndex-1).text));
		spoilerBtn.setOnClickListener(v->toggleSpoiler());
		languageBtn.setOnClickListener(v->showLanguageAlert());
		visibilityBtn.setOnClickListener(this::onVisibilityClick);
		if(!instance.supportsQuotePostAuthoring()){
			visibilityBtn.setAccessibilityDelegate(new View.AccessibilityDelegate(){
				@Override
				public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info){
					super.onInitializeAccessibilityNodeInfo(host, info);
					info.setClassName("android.widget.Spinner");
				}
			});
		}
		Drawable arrow=getResources().getDrawable(R.drawable.ic_baseline_arrow_drop_down_18, getActivity().getTheme()).mutate();
		arrow.setTint(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurface));
		emojiKeyboard.setOnIconChangedListener(new PopupKeyboard.OnIconChangeListener(){
			@Override
			public void onIconChanged(int icon){
				emojiBtn.setSelected(icon!=PopupKeyboard.ICON_HIDDEN);
				updateNavigationBarColor(icon!=PopupKeyboard.ICON_HIDDEN);
				if(icon!=PopupKeyboard.ICON_HIDDEN)
					addBackCallback(emojiKeyboardHider);
				else
					removeBackCallback(emojiKeyboardHider);
				if(autocompleteViewController.getMode()==ComposeAutocompleteViewController.Mode.EMOJIS){
					contentView.layout(contentView.getLeft(), contentView.getTop(), contentView.getRight(), contentView.getBottom());
					if(icon==PopupKeyboard.ICON_HIDDEN)
						showAutocomplete();
					else
						hideAutocomplete();
				}
			}
		});

		contentView=(SizeListenerLinearLayout) view;
		contentView.addView(emojiKeyboard.getView());

		spoilerEdit=view.findViewById(R.id.content_warning);
		spoilerWrap=view.findViewById(R.id.content_warning_wrap);
		LayerDrawable spoilerBg=(LayerDrawable) spoilerWrap.getBackground().mutate();
		spoilerBg.setDrawableByLayerId(R.id.left_drawable, new SpoilerStripesDrawable(false));
		spoilerBg.setDrawableByLayerId(R.id.right_drawable, new SpoilerStripesDrawable(false));
		spoilerWrap.setBackground(spoilerBg);
		spoilerWrap.setClipToOutline(true);
		spoilerWrap.setOutlineProvider(OutlineProviders.roundedRect(8));
		if((savedInstanceState!=null && savedInstanceState.getBoolean("hasSpoiler", false)) || hasSpoiler){
			hasSpoiler=true;
			spoilerWrap.setVisibility(View.VISIBLE);
			spoilerBtn.setSelected(true);
		}else if(editingStatus!=null && !TextUtils.isEmpty(editingStatus.spoilerText)){
			hasSpoiler=true;
			spoilerWrap.setVisibility(View.VISIBLE);
			spoilerEdit.setText(getArguments().getString("sourceSpoiler", editingStatus.spoilerText));
			spoilerBtn.setSelected(true);
		}

		if(editingStatus!=null && editingStatus.visibility!=null){
			statusVisibility=editingStatus.visibility;
			if(editingStatus.quoteApproval!=null){
				statusQuotePolicy=editingStatus.quoteApproval.toQuotePolicy();
			}
		}
		updateVisibilityButton(false);

		autocompleteViewController=new ComposeAutocompleteViewController(getActivity(), accountID);
		autocompleteViewController.setCompletionSelectedListener(new ComposeAutocompleteViewController.AutocompleteListener(){
			@Override
			public void onCompletionSelected(String completion){
				onAutocompleteOptionSelected(completion);
			}

			@Override
			public void onSetEmojiPanelOpen(boolean open){
				if(open!=emojiKeyboard.isVisible())
					emojiKeyboard.toggleKeyboardPopup(mainEditText);
			}

			@Override
			public void onLaunchAccountSearch(){
				Bundle args=new Bundle();
				args.putString("account", accountID);
				Nav.goForResult(getActivity(), AccountSearchFragment.class, args, AUTOCOMPLETE_ACCOUNT_RESULT, ComposeFragment.this);
			}
		});
		View autocompleteView=autocompleteViewController.getView();
		autocompleteView.setVisibility(View.INVISIBLE);
		bottomBar.addView(autocompleteView, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(56)));
		autocompleteDivider=view.findViewById(R.id.bottom_bar_autocomplete_divider);

		pollViewController.setView(view, savedInstanceState);
		mediaViewController.setView(view, savedInstanceState);

		NestedRecyclerScrollView outerScroller=view.findViewById(R.id.outer_scroller);
		CustomScrollView innerScroller=view.findViewById(R.id.inner_scroller);
		outerScroller.setScrollableChildSupplier(()->innerScroller);

		view.findViewById(R.id.quote_post_remove).setOnClickListener(v->{
			removeQuote();
		});

		// MAMMUT: Composer 2.0 (§4) — thread wiring. The add control, the
		// first segment's number/overflow, and active-segment focus tracking
		// for the bottom-bar buttons.
		segmentsContainer=view.findViewById(R.id.compose_segments);
		addSegmentBtn=view.findViewById(R.id.add_segment);
		segmentOneHeader=view.findViewById(R.id.segment_one_header);
		segmentOneNumber=view.findViewById(R.id.segment_number_one);
		segmentOneOverflow=view.findViewById(R.id.segment_overflow_one);
		addSegmentBtn.setOnClickListener(v->{
			ThreadSegment ts=addThreadSegment(null);
			ts.text.requestFocus();
			InputMethodManager imm=getActivity().getSystemService(InputMethodManager.class);
			if(imm!=null)
				imm.showSoftInput(ts.text, 0);
		});
		segmentOneOverflow.setOnClickListener(v->showSegmentOverflow(segmentOneOverflow, 0));
		View.OnFocusChangeListener mainFocusListener=(v, hasFocus)->{
			if(hasFocus)
				setActiveSegment(0);
		};
		mainEditText.setOnFocusChangeListener(mainFocusListener);
		spoilerEdit.setOnFocusChangeListener(mainFocusListener);
		if(editingStatus!=null){
			// §4: editing an existing post is always single-segment — no
			// add control, no thread chrome, no thread code path runs.
			addSegmentBtn.setVisibility(View.GONE);
			segmentOneHeader.setVisibility(View.GONE);
		}else if(!extraSegments.isEmpty()){
			// Rotation with a retained instance: the holders survived, their
			// views did not. Rebuild every card from the holder state.
			for(ThreadSegment ts:extraSegments)
				attachThreadSegmentCard(ts, null);
			renumberSegments();
		}else if(savedInstanceState!=null){
			// Recreated after process death: rebuild the cards from the
			// saved per-segment state.
			int segCount=savedInstanceState.getInt("threadSegCount", 0);
			for(int i=0;i<segCount;i++){
				ThreadSegment ts=addThreadSegment(savedInstanceState.getBundle("threadSeg"+i));
				String text=savedInstanceState.getString("threadSeg"+i+"_text");
				if(!TextUtils.isEmpty(text)){
					ts.text.setText(text);
					ts.textMirror=text;
				}
				if(savedInstanceState.getBoolean("threadSeg"+i+"_hasSpoiler", false)){
					ts.hasSpoiler=true;
					ts.spoilerWrap.setVisibility(View.VISIBLE);
					ts.cwBtn.setSelected(true);
					String spoiler=savedInstanceState.getString("threadSeg"+i+"_spoiler");
					if(!TextUtils.isEmpty(spoiler)){
						ts.spoilerEdit.setText(spoiler);
						ts.spoilerTextMirror=spoiler;
					}
				}
				ts.idempotencyKey=savedInstanceState.getString("threadSeg"+i+"_idem");
				updateThreadSegmentCounter(ts);
			}
		}

		creatingView=false;

		return view;
	}

	private void removeQuote(){
		if(quotedStatus!=null){
			quotedStatus=null;
			// MAMMUT: the author explicitly removed the quote; a re-save must
			// not resurrect the resumed draft's stored quotedStatusId.
			restoredQuotedStatusId=null;
			quotedPostWrap.setVisibility(View.GONE);
			updateMediaPollStates();
			mainEditText.setHint(R.string.compose_hint);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		pollViewController.onSaveInstanceState(outState);
		mediaViewController.onSaveInstanceState(outState);
		outState.putBoolean("hasSpoiler", hasSpoiler);
		outState.putSerializable("visibility", statusVisibility);
		outState.putString("draftId", draftId);
		outState.putParcelable("postLang", Parcels.wrap(postLang));
		// MAMMUT: Composer 2.0 (§4) — the thread survives process death the
		// same way the main surface does: one state bundle per segment.
		outState.putInt("threadSegCount", extraSegments.size());
		for(int i=0;i<extraSegments.size();i++){
			ThreadSegment ts=extraSegments.get(i);
			Bundle segBundle=new Bundle();
			ts.mediaController.onSaveInstanceState(segBundle);
			outState.putBundle("threadSeg"+i, segBundle);
			outState.putString("threadSeg"+i+"_text", ts.text.getText().toString());
			outState.putBoolean("threadSeg"+i+"_hasSpoiler", ts.hasSpoiler);
			outState.putString("threadSeg"+i+"_spoiler", ts.hasSpoiler ? ts.spoilerEdit.getText().toString() : null);
			outState.putString("threadSeg"+i+"_idem", ts.idempotencyKey);
		}
		if(currentAutocompleteSpan!=null){
			Editable e=mainEditText.getText();
			outState.putInt("autocompleteStart", e.getSpanStart(currentAutocompleteSpan));
			outState.putInt("autocompleteEnd", e.getSpanEnd(currentAutocompleteSpan));
		}
	}

	@Override
	public void onResume(){
		super.onResume();
	}

	@Override
	public void onPause(){
		super.onPause();
		// MAMMUT: Composer 2.0 (§3) — autosave on backgrounding. Back while
		// there is content goes through confirmDiscardDraftAndFinish instead.
		// An empty composer writes nothing, and the edit path never autosaves.
		if(editingStatus==null && !finished && hasDraft()){
			// §6/N5: the autosave is awaited, not fire-and-forget — a process
			// kill in the gap between scheduling and the write would silently
			// lose the draft. The wait is bounded so a slow media copy can
			// only delay, never wedge, the UI thread.
			CountDownLatch saveDone=saveDraft(null);
			if(saveDone!=null){
				try{
					saveDone.await(3, TimeUnit.SECONDS);
				}catch(InterruptedException ignored){}
			}
		}
	}

	/**
	 * MAMMUT: Composer 2.0 (§3) — snapshots the composer into the shared store
	 * on a background thread, holding one draftId for the fragment's lifetime
	 * so repeat saves update the same draft instead of creating another.
	 * <p>
	 * When {@code onSaved} is non-null (the discard dialog's Save-draft
	 * button) the save must be observed, not merely scheduled: the callback —
	 * which sets {@code finished} and finishes the fragment — runs only after
	 * the store write has landed, so the publish-cleanup guard can no longer
	 * swallow the write (review M1).
	 * <p>
	 * Returns a latch counted down when the write attempt completes (whether
	 * it wrote, was skipped, or failed), so {@link #onPause()} can wait out
	 * the autosave before the process can die.
	 */
	private CountDownLatch saveDraft(Runnable onSaved){
		if(editingStatus!=null)
			return null;
		// §4 (review M3): after a partial publish the tail draft on disk is
		// the source of truth for the remaining thread; the fire-and-forget
		// autosave must not overwrite it. The explicit Save-draft button
		// (onSaved != null) still writes — the author asked for it.
		if(onSaved==null && tailDraftSaved)
			return null;
		final ComposeDraft d=new ComposeDraft();
		d.id=draftId!=null ? draftId : UUID.randomUUID().toString();
		d.updatedAt=System.currentTimeMillis();
		d.visibility=statusVisibility;
		// §4: once part of the thread has been published, the anchor is the
		// last published segment — a retry (or an autosave of the remaining
		// tail) must chain below it, not below the original reply target.
		d.replyToStatusId=threadAnchorId!=null ? threadAnchorId : (replyTo!=null ? replyTo.id : restoredReplyToStatusId);
		d.quotedStatusId=quotedStatus!=null ? quotedStatus.id : restoredQuotedStatusId;
		// Note: a resumed draft carries replyToStatusId/quotedStatusId (§1).
		// The reply id is consumed at publish time (review M4); only the
		// quote is truly unrestorable from an id, and applyDraft says so
		// instead of dropping it silently.
		Segment seg=new Segment();
		seg.text=mainEditText.getText().toString();
		if(hasSpoiler){
			seg.spoilerText=spoilerEdit.length()>0 ? spoilerEdit.getText().toString() : null;
			seg.sensitive=spoilerEdit.length()==0;
		}
		seg.poll=pollViewController.getDraftPoll();
		// §4: one key per segment, generated at first snapshot, reused on
		// every later save of this composer session and on resume.
		seg.idempotencyKey=draftIdempotencyKey!=null ? draftIdempotencyKey : (draftIdempotencyKey=UUID.randomUUID().toString());
		d.segments.add(seg);
		// MAMMUT (§4) — one draft.json for the whole thread: segment cards
		// follow the main surface in list order.
		final List<ThreadSegment> segsSnapshot=new ArrayList<>(extraSegments);
		for(ThreadSegment ts:segsSnapshot){
			Segment s=new Segment();
			s.text=ts.text.getText().toString();
			if(ts.hasSpoiler){
				s.spoilerText=ts.spoilerEdit.length()>0 ? ts.spoilerEdit.getText().toString() : null;
				s.sensitive=ts.spoilerEdit.length()==0;
			}
			s.idempotencyKey=ts.idempotencyKey!=null ? ts.idempotencyKey : (ts.idempotencyKey=UUID.randomUUID().toString());
			d.segments.add(s);
		}
		draftId=d.id;
		// Snapshot the attachment lists on the UI thread (M5): the copy itself
		// used to run on the worker and could throw mid-iteration while the
		// UI thread added or removed attachments, silently skipping the save.
		final List<ComposeMediaViewController.DraftMediaAttachment> attachmentSnapshot=mediaViewController.snapshotAttachments();
		final List<List<ComposeMediaViewController.DraftMediaAttachment>> threadAttachmentSnapshots=new ArrayList<>();
		for(ThreadSegment ts:segsSnapshot)
			threadAttachmentSnapshots.add(ts.mediaController.snapshotAttachments());
		final CountDownLatch saveDone=new CountDownLatch(1);
		MastodonAPIController.runInBackground(()->{
			try{
				// The single worker thread serializes this with a publish-cleanup
				// delete. The finished guard covers the fire-and-forget paths
				// (onPause autosave) once the user has published or discarded;
				// the explicit Save-draft button passes a callback and must
				// write even while the fragment is leaving — its finished is
				// only set from that callback, after the write.
				if(onSaved==null && finished)
					return;
				try{
					seg.attachments=mediaViewController.buildDraftAttachments(attachmentSnapshot, draftStore, d.id);
					for(int i=0;i<segsSnapshot.size();i++)
						d.segments.get(i+1).attachments=segsSnapshot.get(i).mediaController.buildDraftAttachments(threadAttachmentSnapshots.get(i), draftStore, d.id);
					draftStore.save(d);
				}catch(Exception x){
					android.util.Log.w(TAG, "saveDraft: failed", x);
					// §6: a failed save is surfaced, never silent — the
					// composer stays open with the text still in it.
					UiUtils.runOnUiThread(()->{
						if(getActivity()!=null)
							Toast.makeText(getActivity(), R.string.draft_save_failed, Toast.LENGTH_LONG).show();
					});
					return;
				}
				if(onSaved!=null)
					UiUtils.runOnUiThread(onSaved);
			}finally{
				saveDone.countDown();
			}
		});
		return saveDone;
	}

	/**
	 * MAMMUT: Composer 2.0 (§2/§6) — a draft disappears only after a successful
	 * publish or an explicit, confirmed discard.
	 */
	private void deleteDraftIfAny(){
		final String id=draftId;
		draftId=null;
		if(id!=null)
			MastodonAPIController.runInBackground(()->draftStore.delete(id));
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		if(prevHadDraft){
			prevHadDraft=false;
			removeBackCallback(discardConfirmationCallback);
		}
	}

	@Override
	protected void onShown(){
		super.onShown();
		updateDraftState();
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		if(editingStatus==null)
			loadDefaultStatusVisibility(savedInstanceState);
		contentView.setSizeListener(emojiKeyboard::onContentViewSizeChanged);
		InputMethodManager imm=getActivity().getSystemService(InputMethodManager.class);
		mainEditText.requestFocus();
		view.postDelayed(()->{
			imm.showSoftInput(mainEditText, 0);
		}, 100);
		sendProgress=view.findViewById(R.id.progress);
		sendProgress.setVisibility(View.GONE);

		mainEditText.setSelectionListener(this);
		mainEditText.addTextChangedListener(new TextWatcher(){
			private int lastChangeStart, lastChangeCount;

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count){
				if(s.length()==0)
					return;
				lastChangeStart=start;
				lastChangeCount=count;
			}

			@Override
			public void afterTextChanged(Editable s){
				if(s.length()==0){
					updateCharCounter();
					return;
				}
				int start=lastChangeStart;
				int count=lastChangeCount;
				// offset one char back to catch an already typed '@' or '#' or ':'
				int realStart=start;
				start=Math.max(0, start-1);
				CharSequence changedText=s.subSequence(start, realStart+count);
				String raw=changedText.toString();
				Editable editable=(Editable) s;
				// 1. find mentions, hashtags, and emoji shortcodes in any freshly inserted text, and put spans over them
				if(raw.contains("@") || raw.contains("#") || raw.contains(":")){
					Matcher matcher=AUTO_COMPLETE_PATTERN.matcher(changedText);
					while(matcher.find()){
						if(editable.getSpans(start+matcher.start(), start+matcher.end(), ComposeAutocompleteSpan.class).length>0)
							continue;
						ComposeAutocompleteSpan span;
						if(TextUtils.isEmpty(matcher.group(4))){ // not an emoji
							span=new ComposeHashtagOrMentionSpan();
						}else{
							span=new ComposeAutocompleteSpan();
						}
						editable.setSpan(span, start+matcher.start(), start+matcher.end(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
					}
				}
				// 2. go over existing spans in the affected range, adjust end offsets and remove no longer valid spans
				ComposeAutocompleteSpan[] spans=editable.getSpans(realStart, realStart+count, ComposeAutocompleteSpan.class);
				for(ComposeAutocompleteSpan span:spans){
					int spanStart=editable.getSpanStart(span);
					int spanEnd=editable.getSpanEnd(span);
					if(spanStart==spanEnd){ // empty, remove
						editable.removeSpan(span);
						continue;
					}
					char firstChar=editable.charAt(spanStart);
					String spanText=s.subSequence(spanStart, spanEnd).toString();
					if(firstChar=='@' || firstChar=='#' || firstChar==':'){
						Matcher matcher=AUTO_COMPLETE_PATTERN.matcher(spanText);
						char prevChar=spanStart>0 ? editable.charAt(spanStart-1) : ' ';
						if(!matcher.find() || !Character.isWhitespace(prevChar)){ // invalid mention, remove
							editable.removeSpan(span);
						}else if(matcher.end()+spanStart<spanEnd){ // mention with something at the end, move the end offset
							editable.setSpan(span, spanStart, spanStart+matcher.end(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
						}
					}else{
						editable.removeSpan(span);
					}
				}

				updateCharCounter();
				updateDraftState();
			}
		});
		spoilerEdit.addTextChangedListener(new SimpleTextWatcher(e->updateCharCounter()));
		if(replyTo!=null){
			InlineStatusStatusDisplayItem item=new InlineStatusStatusDisplayItem("reply", new StatusDisplayItem.NoOpCallbacks(getActivity()), getActivity(), replyTo, accountID, R.drawable.ic_reply_wght700_20px, getString(R.string.in_reply_to, replyTo.account.displayName));
			item.fullWidth=true;
			InlineStatusStatusDisplayItem.Holder holder=new InlineStatusStatusDisplayItem.Holder(getActivity(), replyWrap);
			replyWrap.addView(holder.itemView);
			holder.bind(item);
			int imgCount=item.getImageCount();
			for(int i=0;i<imgCount;i++){
				ViewImageLoader.load(new ViewImageLoaderHolderTarget(holder, i), null, item.getImageRequest(i), false, true);
			}
//			mainLayout.setTopBarsCount(1);

			ArrayList<String> mentions=new ArrayList<>();
			String ownID=AccountSessionManager.getInstance().getAccount(accountID).self.id;
			if(!replyTo.account.id.equals(ownID))
				mentions.add('@'+replyTo.account.acct);
			// MAMMUT: phase 1 port from upstream master
			if(GlobalUserPreferences.mentionRebloggerAutomatically && replyTo.rebloggedBy!=null && !replyTo.rebloggedBy.id.equals(ownID))
				mentions.add('@'+replyTo.rebloggedBy.acct);
			for(Mention mention:replyTo.mentions){
				if(mention.id.equals(ownID))
					continue;
				String m='@'+mention.acct;
				if(!mentions.contains(m))
					mentions.add(m);
			}
			initialText=mentions.isEmpty() ? "" : TextUtils.join(" ", mentions)+" ";
			if(savedInstanceState==null){
				mainEditText.setText(initialText);
				ignoreSelectionChanges=true;
				mainEditText.setSelection(mainEditText.length());
				ignoreSelectionChanges=false;
				if(!TextUtils.isEmpty(replyTo.spoilerText) && AccountSessionManager.getInstance().isSelf(accountID, replyTo.account)){
					hasSpoiler=true;
					spoilerWrap.setVisibility(View.VISIBLE);
					// MAMMUT: phase 1 port from upstream master — prefix the CW with "re: " per preference
					String prefix=(GlobalUserPreferences.prefixReplies==GlobalUserPreferences.PrefixRepliesMode.ALWAYS
							|| (GlobalUserPreferences.prefixReplies==GlobalUserPreferences.PrefixRepliesMode.TO_OTHERS && !ownID.equals(replyTo.account.id)))
							&& !replyTo.spoilerText.startsWith("re: ") ? "re: " : "";
					spoilerEdit.setText(prefix + replyTo.spoilerText);
					spoilerBtn.setSelected(true);
				}
			}
		}else{
			replyWrap.setVisibility(View.GONE);
		}

		if(quotedStatus!=null){
			quotedPostWrap.setVisibility(View.VISIBLE);
			StatusView sv=new StatusView(getActivity(), this);
			sv.addItems(StatusDisplayItem.buildItems(null, getActivity(), quotedStatus, accountID, quotedStatus, Map.of(), StatusDisplayItem.FLAG_NO_FOOTER | StatusDisplayItem.FLAG_IS_QUOTE | StatusDisplayItem.FLAG_FULL_WIDTH));
			LinearLayout.LayoutParams headerLP=(LinearLayout.LayoutParams) sv.getChildAt(0).getLayoutParams();
			headerLP.setMarginEnd(V.dp(48-16));
			headerLP.setMarginStart(V.dp(-4));
			quotedPostWrap.addView(sv);
			mainEditText.setHint(R.string.compose_hint_quote);
			if(savedInstanceState==null && quotedStatus.visibility==StatusPrivacy.UNLISTED && !GlobalUserPreferences.alertSeen("quoteUnlisted")){
				view.postDelayed(()->{
					new M3AlertDialogBuilder(getActivity())
							.setTitle(R.string.unlisted_quote_alert_title)
							.setMessage(R.string.unlisted_quote_alert)
							.setPositiveButton(R.string.dismiss, (dlg, which)->{})
							.setNegativeButton(R.string.dont_remind_again, (dlg, which)->GlobalUserPreferences.setAlertSeen("quoteUnlisted"))
							.show();
				}, 200);
			}
		}

		if(savedInstanceState==null){
			if(editingStatus!=null){
				initialText=getArguments().getString("sourceText", "");
				mainEditText.setText(initialText);
				ignoreSelectionChanges=true;
				mainEditText.setSelection(mainEditText.length());
				ignoreSelectionChanges=false;
				mediaViewController.onViewCreated(savedInstanceState);
			}else{
				String prefilledText=getArguments().getString("prefilledText");
				if(draftToRestore==null && !TextUtils.isEmpty(prefilledText)){
					mainEditText.setText(prefilledText);
					ignoreSelectionChanges=true;
					mainEditText.setSelection(mainEditText.length());
					ignoreSelectionChanges=false;
					initialText=prefilledText;
				}
				ArrayList<Uri> mediaUris=getArguments().getParcelableArrayList("mediaAttachments");
				if(draftToRestore==null && mediaUris!=null && !mediaUris.isEmpty()){
					for(Uri uri:mediaUris){
						mediaViewController.addMediaAttachment(uri, null);
					}
				}
			}
		}

		if(editingStatus!=null){
			updateCharCounter();
			visibilityBtn.setEnabled(false);
			visibilityBtn.setAlpha(0.5f);
		}
		updateMediaPollStates();
		if(draftToRestore!=null){
			// MAMMUT: Composer 2.0 (§3) — a resumed draft wins over defaults
			applyDraft(draftToRestore);
			draftToRestore=null;
		}
	}

	/**
	 * MAMMUT: Composer 2.0 (§3) — restores text, CW, sensitivity, visibility,
	 * media with descriptions, and the poll. Restored media goes through
	 * {@link ComposeMediaViewController#addRestoredAttachment}, which never
	 * runs the Caption Track metadata prefill: a restored description is the
	 * author's text, not a suggestion.
	 */
	private void applyDraft(ComposeDraft d){
		if(d.segments.isEmpty())
			return;
		Segment seg=d.segments.get(0);
		if(d.visibility!=null){
			statusVisibility=d.visibility;
			// The draft's visibility wins over account defaults for this
			// session (N3): this flag replaces the never-reset latch.
			restoredDraftVisibility=true;
			updateVisibilityButton(false);
		}
		if(d.quotedStatusId!=null){
			// MAMMUT: Composer 2.0 (§1/§6, review M4) — the reply context
			// is consumed at publish time through replyToStatusId, so only
			// the quote is truly lost on a resumed draft, and that must
			// never be a silent surprise.
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.draft_context_lost_title)
					.setMessage(R.string.draft_context_lost)
					.setPositiveButton(R.string.ok, null)
					.show();
		}
		if(!TextUtils.isEmpty(seg.text)){
			mainEditText.setText(seg.text);
			ignoreSelectionChanges=true;
			mainEditText.setSelection(mainEditText.length());
			ignoreSelectionChanges=false;
		}
		if(seg.spoilerText!=null || seg.sensitive){
			hasSpoiler=true;
			spoilerWrap.setVisibility(View.VISIBLE);
			spoilerBtn.setSelected(true);
			if(seg.spoilerText!=null)
				spoilerEdit.setText(seg.spoilerText);
		}
		pollViewController.restorePoll(seg.poll);
		for(DraftAttachment da:seg.attachments)
			mediaViewController.addRestoredAttachment(da);
		updateCharCounter();
		updateMediaPollStates();
		updateDraftState();
		// MAMMUT: Composer 2.0 (§4) — a resumed draft may be a whole
		// thread: every segment after the first becomes a card, keeping its
		// own text, CW, media with descriptions, and idempotency key. The
		// poll stays with the first segment; a poll on any other segment is
		// not a shape the composer can produce and is ignored.
		for(int i=1;i<d.segments.size();i++){
			Segment s=d.segments.get(i);
			ThreadSegment ts=addThreadSegment(null);
			ts.idempotencyKey=s.idempotencyKey;
			if(!TextUtils.isEmpty(s.text)){
				ts.text.setText(s.text);
				ts.textMirror=s.text;
			}
			if(s.spoilerText!=null || s.sensitive){
				ts.hasSpoiler=true;
				ts.spoilerWrap.setVisibility(View.VISIBLE);
				ts.cwBtn.setSelected(true);
				if(s.spoilerText!=null){
					ts.spoilerEdit.setText(s.spoilerText);
					ts.spoilerTextMirror=s.spoilerText;
				}
			}
			for(DraftAttachment da:s.attachments)
				ts.mediaController.addRestoredAttachment(da);
			updateThreadSegmentCounter(ts);
		}
		renumberSegments();
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState){
		super.onViewStateRestored(savedInstanceState);
		if(savedInstanceState!=null && savedInstanceState.containsKey("autocompleteStart")){
			int start=savedInstanceState.getInt("autocompleteStart"), end=savedInstanceState.getInt("autocompleteEnd");
			currentAutocompleteSpan=new ComposeAutocompleteSpan();
			mainEditText.getText().setSpan(currentAutocompleteSpan, start, end, Editable.SPAN_EXCLUSIVE_INCLUSIVE);
			startAutocomplete(currentAutocompleteSpan);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		inflater.inflate(editingStatus==null ? R.menu.compose : R.menu.compose_edit, menu);
		publishButton=menu.findItem(R.id.publish);
		updatePublishButtonState();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(item.getItemId()==R.id.publish){
			if(GlobalUserPreferences.altTextReminders && editingStatus==null)
				checkAltTextsAndPublish();
			else
				publish();
		}
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig){
		super.onConfigurationChanged(newConfig);
		emojiKeyboard.onConfigurationChanged();
	}

	@SuppressLint("NewApi")
	private void updateCharCounter(){
		Editable text=mainEditText.getText();
		charCount=countTextChars(text);
		if(hasSpoiler){
			charCount+=spoilerEdit.length();
		}
		applyOverLimitSpans(text, charCount);
		if(activeSegmentIndex==0)
			updateCounterView(charCounter, charCount);
		trimmedCharCount=text.toString().trim().length();
		updatePublishButtonState();
	}

	/**
	 * The weighted character count of one segment's text: URLs, mentions
	 * and emoji count the way the instance's limit expects (§4: letters
	 * count per segment against the instance's own limits).
	 */
	private int countTextChars(Editable text){
		String countableText=TwitterTextEmojiRegex.VALID_EMOJI_PATTERN.matcher(
			MENTION_PATTERN.matcher(
					HtmlParser.URL_PATTERN.matcher(text).replaceAll("$2xxxxxxxxxxxxxxxxxxxxxxx")
			).replaceAll("$1@$3")
		).replaceAll("x");
		int count=0;
		breakIterator.setText(countableText);
		while(breakIterator.next()!=BreakIterator.DONE){
			count++;
		}
		return count;
	}

	/** Over-limit highlight on the segment's own text field. */
	private void applyOverLimitSpans(Editable text, int count){
		text.removeSpan(overLimitBG);
		text.removeSpan(overLimitFG);
		if(count>charLimit){
			int start=text.length()-(count-charLimit);
			int end=text.length();
			text.setSpan(overLimitFG, start, end, 0);
			text.setSpan(overLimitBG, start, end, 0);
		}
	}

	/** Remaining characters and error color on one counter label. */
	private void updateCounterView(TextView counter, int count){
		counter.setText(String.valueOf(charLimit-count));
		counter.setTextColor(UiUtils.getThemeColor(getActivity(), count>charLimit ? R.attr.colorM3Error : R.attr.colorM3OnSurface));
	}

	/**
	 * MAMMUT (§4) — one card's counter after an edit. The card's own
	 * counter always reflects its segment; the shared bottom-bar counter
	 * mirrors whichever segment is active.
	 */
	private void updateThreadSegmentCounter(ThreadSegment ts){
		Editable editable=ts.text.getText();
		int count=countTextChars(editable);
		if(ts.hasSpoiler)
			count+=ts.spoilerEdit.length();
		ts.charCount=count;
		applyOverLimitSpans(editable, count);
		updateCounterView(ts.counter, count);
		ts.trimmedCharCount=editable.toString().trim().length();
		if(activeSegmentIndex==extraSegments.indexOf(ts)+1)
			updateCounterView(charCounter, count);
		updatePublishButtonState();
	}

	/**
	 * MAMMUT (§4) — the bottom-bar counter shows the active segment's
	 * remaining characters; its CW and media buttons act on that segment.
	 */
	private void setActiveSegment(int index){
		if(activeSegmentIndex!=index){
			activeSegmentIndex=index;
			spoilerBtn.setSelected(segmentHasSpoilerAt(index));
		}
		updateActiveCounterDisplay();
		updateMediaPollStates();
	}

	private void updateActiveCounterDisplay(){
		updateCounterView(charCounter, activeSegmentIndex==0 ? charCount : extraSegments.get(activeSegmentIndex-1).charCount);
	}

	public void updatePublishButtonState(){
		uuid=null;
		if(publishButton==null)
			return;
		boolean enabled=(trimmedCharCount>0 || !mediaViewController.isEmpty()) && charCount<=charLimit && mediaViewController.getNonDoneAttachmentCount()==0 && (pollViewController.isEmpty() || pollViewController.getNonEmptyOptionsCount()>1);
		// MAMMUT (§4) — every segment must be publishable on its own before
		// the thread can start; an empty or over-limit middle segment would
		// fail mid-chain, after the prefix is already public.
		for(ThreadSegment ts:extraSegments){
			enabled &= (ts.trimmedCharCount>0 || !ts.mediaController.isEmpty()) && ts.charCount<=charLimit
					&& ts.mediaController.getNonDoneAttachmentCount()==0;
		}
		publishButton.setEnabled(enabled);
		updateDraftState();
	}

	private void onCustomEmojiClick(Emoji emoji){
		if(getActivity().getCurrentFocus() instanceof EditText edit){
			if(edit==mainEditText && currentAutocompleteSpan!=null && autocompleteViewController.getMode()==ComposeAutocompleteViewController.Mode.EMOJIS){
				Editable text=mainEditText.getText();
				int start=text.getSpanStart(currentAutocompleteSpan);
				int end=text.getSpanEnd(currentAutocompleteSpan);
				finishAutocomplete();
				text.replace(start, end, ':'+emoji.shortcode+':');
				return;
			}
			int start=edit.getSelectionStart();
			String prefix=start>0 && !Character.isWhitespace(edit.getText().charAt(start-1)) ? " :" : ":";
			edit.getText().replace(start, edit.getSelectionEnd(), prefix+emoji.shortcode+':');
		}
	}

	@Override
	protected void updateToolbar(){
		super.updateToolbar();
		int color=UiUtils.alphaBlendThemeColors(getActivity(), R.attr.colorM3Background, R.attr.colorM3Primary, 0.11f);
		getToolbar().setBackgroundColor(color);
		setStatusBarColor(color);
		bottomBar.setBackgroundColor(color);
		updateNavigationBarColor(emojiKeyboard.isVisible());
	}

	private void updateNavigationBarColor(boolean emojiKeyboardVisible){
		int color=UiUtils.alphaBlendThemeColors(getActivity(), R.attr.colorM3Background, R.attr.colorM3Primary, emojiKeyboardVisible ? 0.08f : 0.11f);
		setNavigationBarColor(color);
	}

	@Override
	protected int getNavigationIconDrawableResource(){
		return R.drawable.ic_baseline_close_24;
	}

	@Override
	public boolean wantsCustomNavigationIcon(){
		return true;
	}

	private void checkAltTextsAndPublish(){
		// MAMMUT (§4) — the reminder covers every segment's media.
		int count=mediaViewController.getMissingAltTextAttachmentCount();
		boolean allImages=mediaViewController.areAllAttachmentsImages();
		for(ThreadSegment ts:extraSegments){
			count+=ts.mediaController.getMissingAltTextAttachmentCount();
			allImages &= ts.mediaController.areAllAttachmentsImages();
		}
		if(count==0){
			publish();
		}else{
			String msg=getResources().getQuantityString(allImages ? R.plurals.alt_text_reminder_x_images : R.plurals.alt_text_reminder_x_attachments,
					count, switch(count){
						case 1 -> getString(R.string.count_one);
						case 2 -> getString(R.string.count_two);
						case 3 -> getString(R.string.count_three);
						case 4 -> getString(R.string.count_four);
						default -> String.valueOf(count);
					});
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.alt_text_reminder_title)
					.setMessage(msg)
					.setPositiveButton(R.string.alt_text_reminder_post_anyway, (dlg, item)->publish())
					.setNegativeButton(R.string.cancel, null)
					.show();
		}
	}

	private void publish(){
		Runnable publishAction=()->{
			// MAMMUT (§4) — the overlay is a FrameLayout so the chain can say
			// "Posting 2 of 4" mid-publish. The pill exists only when there
			// is an actual chain: a single post keeps the old silent look
			// (review M5) — an empty outlined box mid-screen helped no one.
			FrameLayout overlay=new FrameLayout(getActivity());
			postingProgressText=null;
			if(!extraSegments.isEmpty()){
				postingProgressText=new TextView(getActivity());
				postingProgressText.setTextAppearance(R.style.m3_label_large);
				postingProgressText.setTextColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurface));
				postingProgressText.setBackgroundResource(R.drawable.bg_composer_visibility);
				postingProgressText.setPadding(V.dp(16), V.dp(8), V.dp(16), V.dp(8));
				overlay.addView(postingProgressText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
			}
			sendingOverlay=overlay;
			WindowManager.LayoutParams overlayParams=new WindowManager.LayoutParams();
			overlayParams.type=WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
			overlayParams.flags=WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
			overlayParams.width=overlayParams.height=WindowManager.LayoutParams.MATCH_PARENT;
			overlayParams.format=PixelFormat.TRANSLUCENT;
			overlayParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
			overlayParams.token=mainEditText.getWindowToken();
			wm.addView(sendingOverlay, overlayParams);
			addBackCallback(sendingBackButtonBlocker);

			publishButton.setEnabled(false);
			V.setVisibilityAnimated(sendProgress, View.VISIBLE);

			// §4: save every segment's alt texts first — the chain starts
			// only when all of them are on the server.
			saveAllAltTextsBeforePublishing(this::actuallyPublish, this::handlePublishError);
		};
		if(quotedStatus!=null && !AccountSessionManager.getInstance().isSelf(accountID, quotedStatus.account) && statusVisibility==StatusPrivacy.PRIVATE && !GlobalUserPreferences.alertSeen("quoteFollowersOnly")){
			new M3AlertDialogBuilder(getActivity())
					.setCheckboxText(R.string.quote_followers_only_dont_show_again)
					.setTitle(R.string.quote_followers_only_confirm_title)
					.setMessage(R.string.quote_followers_only_confirm)
					.setPositiveButton(R.string.quote_followers_only_publish, (dlg, which)->{
						CheckBox cb=((AlertDialog)dlg).findViewById(R.id.checkbox);
						if(cb!=null && cb.isChecked())
							GlobalUserPreferences.setAlertSeen("quoteFollowersOnly");
						publishAction.run();
					})
					.setNegativeButton(R.string.quote_followers_only_cancel, null)
					.show();
		}else{
			publishAction.run();
		}
	}

	/**
	 * MAMMUT (§4) — flushes unsaved image descriptions segment by segment;
	 * the chain starts only when every segment's batch has landed. Media
	 * upload itself already happened per attachment through the existing
	 * {@code UploadAttachment} queue; this is the last write before publish.
	 */
	private void saveAllAltTextsBeforePublishing(Runnable onSuccess, Consumer<ErrorResponse> onError){
		saveAltTextsBeforePublishingFrom(0, onSuccess, onError);
	}

	private void saveAltTextsBeforePublishingFrom(int index, Runnable onSuccess, Consumer<ErrorResponse> onError){
		if(index>extraSegments.size()){
			onSuccess.run();
			return;
		}
		ComposeMediaViewController controller=index==0 ? mediaViewController : extraSegments.get(index-1).mediaController;
		controller.saveAltTextsBeforePublishing(()->saveAltTextsBeforePublishingFrom(index+1, onSuccess, onError), onError);
	}

	private void actuallyPublish(){
		if(editingStatus!=null){
			actuallyPublishEdit();
			return;
		}
		// MAMMUT (§4) — publish the thread in order, each segment a plain
		// CreateStatus chained to the id the server returned for the
		// previous one. No batch path.
		final List<SegmentPublish> pubs=new ArrayList<>();
		pubs.add(buildPublishForFirstSegment());
		for(ThreadSegment ts:extraSegments)
			pubs.add(buildPublishForThreadSegment(ts));
		if(pubs.size()>1)
			updatePostingProgress(1, pubs.size());
		// §4 (review M4): a tail draft resumed after a partial publish
		// carries replyToStatusId = the last published segment; chain below
		// it so the retried thread continues instead of posting top-level.
		publishNextSegment(pubs, 0, threadAnchorId!=null ? threadAnchorId : (replyTo!=null ? replyTo.id : restoredReplyToStatusId));
	}

	/**
	 * MAMMUT (§4) — the edit path is untouched by threads: one request, one
	 * EditStatus, exactly as before slice 2.
	 */
	private void actuallyPublishEdit(){
		String text=mainEditText.getText().toString();
		// MAMMUT: port from upstream master — strip tracking parameters from published URLs
		if(GlobalUserPreferences.removeTrackingParams)
			text=Tracking.cleanUrlsInText(text);
		CreateStatus.Request req=new CreateStatus.Request();
		req.status=text;
		req.visibility=statusVisibility;
		if(!mediaViewController.isEmpty()){
			req.mediaIds=mediaViewController.getAttachmentIDs();
			req.mediaAttributes=mediaViewController.getAttachmentAttributes();
		}
		if(replyTo!=null){
			req.inReplyToId=replyTo.id;
		}
		if(!pollViewController.isEmpty()){
			req.poll=pollViewController.getPollForRequest();
		}
		if(hasSpoiler){
			if(spoilerEdit.length()>0)
				req.spoilerText=spoilerEdit.getText().toString();
			else
				req.sensitive=true;
		}
		if(postLang!=null){
			req.language=postLang.locale.toLanguageTag();
		}
		if(statusQuotePolicy!=null && instance.supportsQuotePostAuthoring())
			req.quoteApprovalPolicy=statusQuotePolicy;
		if(quotedStatus!=null)
			req.quotedStatusId=quotedStatus.id;

		Callback<Status> resCallback=new Callback<>(){
			@Override
			public void onSuccess(Status result){
				finished=true;
				deleteDraftIfAny();
				dismissSendingOverlay();
				removeBackCallback(discardConfirmationCallback);
				removeBackCallback(emojiKeyboardHider);
				E.post(new StatusUpdatedEvent(result));
				Nav.finish(ComposeFragment.this);
			}

			@Override
			public void onError(ErrorResponse error){
				handlePublishError(error);
			}
		};

		new EditStatus(req, editingStatus.id)
				.setCallback(resCallback)
				.exec(accountID);
	}

	/**
	 * MAMMUT (§4) — the first segment's request. Reply and quote context
	 * belong here and only here; visibility, quote policy and language are
	 * thread-wide and ride on every segment's request.
	 */
	private SegmentPublish buildPublishForFirstSegment(){
		String text=mainEditText.getText().toString();
		// MAMMUT: port from upstream master — strip tracking parameters from published URLs
		if(GlobalUserPreferences.removeTrackingParams)
			text=Tracking.cleanUrlsInText(text);
		CreateStatus.Request req=new CreateStatus.Request();
		req.status=text;
		req.visibility=statusVisibility;
		if(!mediaViewController.isEmpty()){
			req.mediaIds=mediaViewController.getAttachmentIDs();
		}
		if(!pollViewController.isEmpty()){
			req.poll=pollViewController.getPollForRequest();
		}
		if(hasSpoiler){
			if(spoilerEdit.length()>0)
				req.spoilerText=spoilerEdit.getText().toString();
			else
				req.sensitive=true;
		}
		if(postLang!=null){
			req.language=postLang.locale.toLanguageTag();
		}
		if(statusQuotePolicy!=null && instance.supportsQuotePostAuthoring())
			req.quoteApprovalPolicy=statusQuotePolicy;
		if(quotedStatus!=null)
			req.quotedStatusId=quotedStatus.id;
		// §4: a draft resumed from the store publishes with its persisted
		// per-segment key, so a retry after failure cannot double-post.
		if(uuid==null)
			uuid=draftIdempotencyKey!=null ? draftIdempotencyKey : UUID.randomUUID().toString();
		return new SegmentPublish(req, uuid);
	}

	private SegmentPublish buildPublishForThreadSegment(ThreadSegment ts){
		String text=ts.text.getText().toString();
		if(GlobalUserPreferences.removeTrackingParams)
			text=Tracking.cleanUrlsInText(text);
		CreateStatus.Request req=new CreateStatus.Request();
		req.status=text;
		req.visibility=statusVisibility;
		if(!ts.mediaController.isEmpty()){
			req.mediaIds=ts.mediaController.getAttachmentIDs();
		}
		if(ts.hasSpoiler){
			if(ts.spoilerEdit.length()>0)
				req.spoilerText=ts.spoilerEdit.getText().toString();
			else
				req.sensitive=true;
		}
		if(postLang!=null){
			req.language=postLang.locale.toLanguageTag();
		}
		if(statusQuotePolicy!=null && instance.supportsQuotePostAuthoring())
			req.quoteApprovalPolicy=statusQuotePolicy;
		if(ts.idempotencyKey==null)
			ts.idempotencyKey=UUID.randomUUID().toString();
		return new SegmentPublish(req, ts.idempotencyKey);
	}

	/**
	 * MAMMUT (§4) — publish one segment, then the next, chained on the id
	 * the server returned. On success of the last segment the thread is
	 * done; on failure of any segment the chain stops and
	 * {@link #handlePartialPublishFailure} takes over.
	 */
	private void publishNextSegment(List<SegmentPublish> pubs, int index, String inReplyToId){
		SegmentPublish pub=pubs.get(index);
		pub.req.inReplyToId=inReplyToId;
		new CreateStatus(pub.req, pub.idempotencyKey)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Status result){
						threadAnchorId=result.id;
						E.post(new StatusCreatedEvent(result, accountID));
						if(index+1<pubs.size()){
							updatePostingProgress(index+2, pubs.size());
							publishNextSegment(pubs, index+1, result.id);
						}else{
							// MAMMUT: Composer 2.0 (§2/§6) — a successful publish retires
							// the draft; nothing autosaves after this point.
							finished=true;
							deleteDraftIfAny();
							dismissSendingOverlay();
							removeBackCallback(discardConfirmationCallback);
							removeBackCallback(emojiKeyboardHider);
							if(replyTo!=null){
								replyTo.repliesCount++;
								E.post(new StatusCountersUpdatedEvent(replyTo, StatusCountersUpdatedEvent.CounterType.REPLIES));
							}
							Nav.finish(ComposeFragment.this);
						}
					}

					@Override
					public void onError(ErrorResponse error){
						if(index==0){
							// Nothing made it out: the plain single-post
							// failure dialog, with Retry, exactly as before.
							handlePublishError(error);
						}else{
							handlePartialPublishFailure(index, error);
						}
					}
				})
				.exec(accountID);
	}

	private void updatePostingProgress(int current, int total){
		if(postingProgressText!=null)
			postingProgressText.setText(getString(R.string.thread_posting_progress, current, total));
	}

	/**
	 * MAMMUT (§4) — segment k+1 failed after k segments went public. The
	 * published prefix is left alone on the server (never retried, never
	 * edited, never deleted); the remaining tail stays in the composer and
	 * is written to the drafts store under one draft, reusing each
	 * segment's persisted idempotency key so a retry cannot double-post
	 * the prefix. The user is told plainly what happened.
	 */
	private void handlePartialPublishFailure(int publishedCount, ErrorResponse error){
		// The tail draft snapshots the current fields on the UI thread
		// before the prefix is cleared from under them.
		CountDownLatch tailSaved=saveThreadTailAsDraft(publishedCount);
		removePublishedPrefix(publishedCount);
		// §6/N4: the tail write is awaited, not fire-and-forget — the same
		// class of loss as the slice-1 autosave that vanished. Bounded so a
		// slow media copy can only delay, never wedge, the UI thread. After
		// the wait the tail draft is on disk (or its failure was surfaced),
		// so the autosave guard below is decided on fact, not hope.
		if(tailSaved!=null){
			try{
				tailSaved.await(3, TimeUnit.SECONDS);
			}catch(InterruptedException ignored){}
		}
		dismissSendingOverlay();
		// No publishButton.setEnabled(true) here (review M2): the promoted
		// tail went through updatePublishButtonState inside
		// removePublishedPrefix, and the button may only be what that
		// computed — an empty or over-limit lead segment keeps it off.
		String msg=getResources().getQuantityString(R.plurals.thread_partial_publish, publishedCount, publishedCount);
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.post_failed)
				.setMessage(msg)
				.setPositiveButton(R.string.ok, null)
				.show();
	}

	/**
	 * Writes segments publishedCount..n as one draft. The draft reuses the
	 * composer's draftId when there is one, and each tail segment keeps its
	 * idempotency key, so resuming the draft and publishing it replays the
	 * same keys — Mastodon's Idempotency-Key dedupes a replayed segment and
	 * the published prefix is not in the draft at all.
	 */
	private CountDownLatch saveThreadTailAsDraft(int publishedCount){
		final ComposeDraft d=new ComposeDraft();
		d.id=draftId!=null ? draftId : UUID.randomUUID().toString();
		d.updatedAt=System.currentTimeMillis();
		d.visibility=statusVisibility;
		// The tail's first segment chains below the last published one.
		d.replyToStatusId=threadAnchorId;
		final List<ThreadSegment> tail=new ArrayList<>(extraSegments.subList(publishedCount-1, extraSegments.size()));
		final List<List<ComposeMediaViewController.DraftMediaAttachment>> threadAttachmentSnapshots=new ArrayList<>();
		for(ThreadSegment ts:tail){
			Segment s=new Segment();
			s.text=ts.text.getText().toString();
			if(ts.hasSpoiler){
				s.spoilerText=ts.spoilerEdit.length()>0 ? ts.spoilerEdit.getText().toString() : null;
				s.sensitive=ts.spoilerEdit.length()==0;
			}
			s.idempotencyKey=ts.idempotencyKey!=null ? ts.idempotencyKey : (ts.idempotencyKey=UUID.randomUUID().toString());
			d.segments.add(s);
			threadAttachmentSnapshots.add(ts.mediaController.snapshotAttachments());
		}
		draftId=d.id;
		final String draftIdFinal=d.id;
		// §6/N4: the write is awaited by handlePartialPublishFailure through
		// this latch — a process kill in the gap between scheduling and the
		// write would silently lose the only copy of the tail.
		final CountDownLatch saveDone=new CountDownLatch(1);
		MastodonAPIController.runInBackground(()->{
			try{
				try{
					for(int i=0;i<tail.size();i++)
						d.segments.get(i).attachments=tail.get(i).mediaController.buildDraftAttachments(threadAttachmentSnapshots.get(i), draftStore, draftIdFinal);
					draftStore.save(d);
					// §4 (review M3): only a landed write may suppress the
					// autosave; a failed one must not block the next chance
					// to persist the tail.
					tailDraftSaved=true;
				}catch(Exception x){
					android.util.Log.w(TAG, "saveThreadTailAsDraft: failed", x);
					// §6: a failed save is surfaced, never silent.
					UiUtils.runOnUiThread(()->{
						if(getActivity()!=null)
							Toast.makeText(getActivity(), R.string.draft_save_failed, Toast.LENGTH_LONG).show();
					});
				}
			}finally{
				saveDone.countDown();
			}
		});
		return saveDone;
	}

	/**
	 * Drops the published prefix from the composer surface so the visible
	 * thread is exactly the unpublished tail. Nothing server-side is
	 * touched — the prefix stays public, alone.
	 */
	private void removePublishedPrefix(int publishedCount){
		// The quote was published with the prefix; the tail must not quote
		// it a second time on retry.
		if(quotedStatus!=null){
			quotedStatus=null;
			restoredQuotedStatusId=null;
			quotedPostWrap.setVisibility(View.GONE);
		}
		if(pollViewController.isShown())
			pollViewController.toggle();
		if(hasSpoiler){
			hasSpoiler=false;
			spoilerWrap.setVisibility(View.GONE);
			spoilerEdit.setText("");
			spoilerBtn.setSelected(false);
		}
		mainEditText.setText("");
		mediaViewController.clearAllAttachments();
		for(int i=0;i<publishedCount-1;i++)
			removeSegmentCard(extraSegments.get(0));
		// Promote the tail's first segment onto the main surface (review
		// M2): after this the composer holds exactly the unpublished tail
		// and the next publish chains below threadAnchorId. The promoted
		// segment keeps its own idempotency key (M3) — the retried first
		// request must replay the key the failed attempt already used, or
		// a timeout-class failure can double-post it.
		if(!extraSegments.isEmpty()){
			ThreadSegment promoted=extraSegments.get(0);
			draftIdempotencyKey=promoted.idempotencyKey;
			if(promoted.hasSpoiler){
				hasSpoiler=true;
				spoilerWrap.setVisibility(View.VISIBLE);
				spoilerBtn.setSelected(true);
				if(promoted.spoilerEdit.length()>0)
					spoilerEdit.setText(promoted.spoilerEdit.getText().toString());
			}
			mainEditText.setText(promoted.text.getText().toString());
			mediaViewController.swapAttachmentsWith(promoted.mediaController);
			removeSegmentCard(promoted);
		}
		activeSegmentIndex=0;
		updateCharCounter();
		updateActiveCounterDisplay();
		updateMediaPollStates();
		updatePublishButtonState();
		updateDraftState();
	}

	/**
	 * One segment of the publish chain: the prepared request plus the
	 * idempotency key CreateStatus must send with it.
	 */
	private static class SegmentPublish{
		final CreateStatus.Request req;
		final String idempotencyKey;

		SegmentPublish(CreateStatus.Request req, String idempotencyKey){
			this.req=req;
			this.idempotencyKey=idempotencyKey;
		}
	}

	/** Tears down the sending overlay and re-enables the publish button. */
	private void dismissSendingOverlay(){
		if(sendingOverlay!=null){
			if(sendingOverlay.isAttachedToWindow())
				wm.removeView(sendingOverlay);
			sendingOverlay=null;
		}
		postingProgressText=null;
		removeBackCallback(sendingBackButtonBlocker);
		V.setVisibilityAnimated(sendProgress, View.GONE);
	}

	private void handlePublishError(ErrorResponse error){
		dismissSendingOverlay();
		publishButton.setEnabled(true);
		if(error instanceof MastodonErrorResponse me){
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.post_failed)
					.setMessage(me.error)
					.setPositiveButton(R.string.retry, (dlg, btn)->publish())
					.setNegativeButton(R.string.cancel, null)
					.show();
		}else{
			error.showToast(getActivity());
		}
	}

	private boolean hasDraft(){
		if(editingStatus!=null){
			if(!mainEditText.getText().toString().equals(initialText))
				return true;
			List<String> existingMediaIDs=editingStatus.mediaAttachments.stream().map(a->a.id).collect(Collectors.toList());
			if(!existingMediaIDs.equals(mediaViewController.getAttachmentIDs()))
				return true;
			return pollViewController.isPollChanged();
		}
		boolean pollFieldsHaveContent=pollViewController.getNonEmptyOptionsCount()>0;
		if((mainEditText.length()>0 && !mainEditText.getText().toString().equals(initialText)) || !mediaViewController.isEmpty() || pollFieldsHaveContent)
			return true;
		// MAMMUT (§4) — any content in any segment makes the draft real.
		for(ThreadSegment ts:extraSegments){
			if(ts.text.length()>0 || !ts.mediaController.isEmpty() || ts.hasSpoiler)
				return true;
		}
		return false;
	}

	private void updateDraftState(){
		boolean hasDraft=hasDraft();
		if(hasDraft!=prevHadDraft){
			prevHadDraft=hasDraft;
			if(hasDraft){
				addBackCallback(discardConfirmationCallback);
			}else{
				removeBackCallback(discardConfirmationCallback);
			}
		}
	}

	@Override
	public void onToolbarNavigationClick(){
		if(hasDraft()){
			confirmDiscardDraftAndFinish();
		}else{
			super.onToolbarNavigationClick();
		}
	}

	@Override
	public void onFragmentResult(int reqCode, boolean success, Bundle result){
		if(reqCode==IMAGE_DESCRIPTION_RESULT && success){
			String attID=result.getString("attachment");
			String text=result.getString("text");
			// MAMMUT (§4) — the video/audio description dialog may belong to
			// any segment's controller; the others no-op.
			mediaViewController.setAltTextByID(attID, text);
			for(ThreadSegment ts:extraSegments)
				ts.mediaController.setAltTextByID(attID, text);
		}else if(reqCode==AUTOCOMPLETE_ACCOUNT_RESULT && success){
			Account acc=Parcels.unwrap(result.getParcelable("selectedAccount"));
			if(currentAutocompleteSpan==null)
				return;
			Editable e=mainEditText.getText();
			int start=e.getSpanStart(currentAutocompleteSpan);
			int end=e.getSpanEnd(currentAutocompleteSpan);
			e.removeSpan(currentAutocompleteSpan);
			e.replace(start, end, '@'+acc.acct+' ');
			finishAutocomplete();
		}
	}

	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
			Insets imeInsets=insets.getInsets(WindowInsets.Type.ime());
			keyboardVisible=imeInsets.left>0 || imeInsets.top>0 || imeInsets.right>0 || imeInsets.bottom>0;
		}
		super.onApplyWindowInsets(insets);
	}

	private void confirmDiscardDraftBackCallback(){
		// If the back callback was set with the keyboard active, it will be invoked first.
		// So, dismiss the keyboard first, because of course that's what the user expects.
		if(keyboardVisible){
			getActivity().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(contentView.getWindowToken(), 0);
			return;
		}
		confirmDiscardDraftAndFinish();
	}

	private void confirmDiscardDraftAndFinish(){
		if(editingStatus!=null){
			new M3AlertDialogBuilder(getActivity())
					.setTitle(R.string.discard_changes)
					.setPositiveButton(R.string.discard, (dialog, which)->{
						removeBackCallback(discardConfirmationCallback);
						Nav.finish(this);
					})
					.setNegativeButton(R.string.cancel, null)
					.show();
			return;
		}
		// MAMMUT: Composer 2.0 (§3) — the discard confirmation gains a third
		// outcome when there is content: Save draft. Discarding an existing
		// autosaved draft is a confirmed, explicit delete; Save draft writes
		// the draft and leaves; Cancel keeps composing.
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.discard_draft)
				.setPositiveButton(R.string.discard, (dialog, which)->{
					finished=true;
					deleteDraftIfAny();
					removeBackCallback(discardConfirmationCallback);
					Nav.finish(this);
				})
				.setNeutralButton(R.string.save_draft, (dialog, which)->{
					// MAMMUT (§3/§6, M1): the save is observed, not scheduled —
					// finished and Nav.finish happen inside the completion
					// callback, which runs only after the store write has landed
					// on the worker thread, so the publish-cleanup guard can no
					// longer win the race and swallow the write.
					saveDraft(()->{
						finished=true;
						removeBackCallback(discardConfirmationCallback);
						Nav.finish(this);
					});
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}


	/**
	 * Builds the correct intent for the device version to select media.
	 *
	 * <p>For Device version > T or R_SDK_v2, use the android platform photopicker via
	 * {@link MediaStore#ACTION_PICK_IMAGES}
	 *
	 * <p>For earlier versions use the built in docs ui via {@link Intent#ACTION_GET_CONTENT}
	 */
	private void openFilePicker(boolean forceGetContent){
		Intent intent;
		boolean usePhotoPicker=!forceGetContent && UiUtils.isPhotoPickerAvailable();
		if(usePhotoPicker){
			intent=new Intent(MediaStore.ACTION_PICK_IMAGES);
			ComposeMediaViewController activeMedia=getActiveMediaController();
			if(activeMedia.getMaxAttachments()-activeMedia.getMediaAttachmentsCount()>1)
				intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, activeMedia.getMaxAttachments()-activeMedia.getMediaAttachmentsCount());
		}else{
			intent=new Intent(Intent.ACTION_GET_CONTENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
		}
		if(!usePhotoPicker && instance.configuration!=null &&
				instance.configuration.mediaAttachments!=null &&
				instance.configuration.mediaAttachments.supportedMimeTypes!=null &&
				!instance.configuration.mediaAttachments.supportedMimeTypes.isEmpty()){
			intent.putExtra(Intent.EXTRA_MIME_TYPES,
					instance.configuration.mediaAttachments.supportedMimeTypes.toArray(
							new String[0]));
		}else{
			if(!usePhotoPicker){
				// If photo picker is being used these are the default mimetypes.
				intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
			}
		}
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		startActivityForResult(intent, MEDIA_RESULT);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		if(requestCode==MEDIA_RESULT && resultCode==Activity.RESULT_OK){
			// MAMMUT (§4) — picker media lands in the active segment.
			ComposeMediaViewController activeMedia=getActiveMediaController();
			Uri single=data.getData();
			if(single!=null){
				activeMedia.addMediaAttachment(single, null);
			}else{
				ClipData clipData=data.getClipData();
				for(int i=0;i<clipData.getItemCount();i++){
					activeMedia.addMediaAttachment(clipData.getItemAt(i).getUri(), null);
				}
			}
		}
	}


	public void updateMediaPollStates(){
		pollBtn.setSelected(pollViewController.isShown());
		// MAMMUT (§4) — media goes to the active segment; a poll (first
		// segment only) disables media on that segment as today, and never
		// on the other segments, which stay plain.
		boolean activePoll=activeSegmentIndex==0 && pollViewController.isShown();
		ComposeMediaViewController activeMedia=getActiveMediaController();
		mediaBtn.setEnabled(!activePoll && activeMedia.canAddMoreAttachments() && quotedStatus==null);
		pollBtn.setEnabled(mediaViewController.isEmpty() && quotedStatus==null);
		// MAMMUT: The Caption Track (§4) — the description block hides while a poll is attached
		mediaViewController.setDescriptionBlockHiddenByPoll(pollViewController.isShown());
	}

	/** MAMMUT (§4) — the media controller of the active segment. */
	private ComposeMediaViewController getActiveMediaController(){
		return activeSegmentIndex==0 ? mediaViewController : extraSegments.get(activeSegmentIndex-1).mediaController;
	}

	private boolean segmentHasSpoilerAt(int index){
		return index==0 ? hasSpoiler : extraSegments.get(index-1).hasSpoiler;
	}

	private ComposeMediaViewController segmentMediaAt(int index){
		return index==0 ? mediaViewController : extraSegments.get(index-1).mediaController;
	}

	private String segmentTextAt(int index){
		return index==0 ? mainEditText.getText().toString() : extraSegments.get(index-1).text.getText().toString();
	}

	private void togglePoll(){
		pollViewController.toggle();
		updatePublishButtonState();
		updateMediaPollStates();
	}

	private void toggleSpoiler(){
		// MAMMUT (§4) — the CW toggle acts on the active segment: the main
		// surface or the card holding the focused field.
		if(activeSegmentIndex!=0){
			ThreadSegment ts=extraSegments.get(activeSegmentIndex-1);
			ts.hasSpoiler=!ts.hasSpoiler;
			if(ts.hasSpoiler){
				ts.spoilerWrap.setVisibility(View.VISIBLE);
				ts.cwBtn.setSelected(true);
				ts.spoilerEdit.requestFocus();
			}else{
				ts.spoilerWrap.setVisibility(View.GONE);
				ts.spoilerEdit.setText("");
				ts.cwBtn.setSelected(false);
				ts.text.requestFocus();
			}
			updateThreadSegmentCounter(ts);
			return;
		}
		hasSpoiler=!hasSpoiler;
		if(hasSpoiler){
			spoilerWrap.setVisibility(View.VISIBLE);
			spoilerBtn.setSelected(true);
			spoilerEdit.requestFocus();
		}else{
			spoilerWrap.setVisibility(View.GONE);
			spoilerEdit.setText("");
			spoilerBtn.setSelected(false);
			mainEditText.requestFocus();
			updateCharCounter();
		}
	}

	private void onVisibilityClick(View v){
		if(instance.supportsQuotePostAuthoring()){
			ComposerVisibilitySheet sheet=new ComposerVisibilitySheet(getActivity(), statusVisibility, statusQuotePolicy,
					true, quotedStatus!=null, quotedStatus==null ? StatusPrivacy.PUBLIC : quotedStatus.visibility, accountID, (s, visibility, policy)->{
				if(statusVisibility!=visibility || statusQuotePolicy!=policy){
					statusVisibility=visibility;
					statusQuotePolicy=policy;
					updateVisibilityButton(true);
					if(quotedStatus!=null && visibility==StatusPrivacy.DIRECT){
						mainEditText.append("\n\n"+quotedStatus.url);
						removeQuote();
					}
				}
				return true;
			});
			sheet.show();
		}else{
			ArrayList<ListItem<StatusPrivacy>> items=new ArrayList<>();
			ExtendedPopupMenu menu=new ExtendedPopupMenu(getActivity(), items);
			Consumer<ListItem<StatusPrivacy>> onClick=i->{
				if(statusVisibility!=i.parentObject){
					statusVisibility=i.parentObject;
					updateVisibilityButton(true);
				}
				menu.dismiss();
			};
			items.add(new ListItem<>(R.string.visibility_public, R.string.visibility_subtitle_public, R.drawable.ic_public_24px, StatusPrivacy.PUBLIC, onClick));
			items.add(new ListItem<>(R.string.visibility_unlisted, R.string.visibility_subtitle_unlisted, R.drawable.ic_clear_night_24px, StatusPrivacy.UNLISTED, onClick));
			items.add(new ListItem<>(R.string.visibility_followers_only, R.string.visibility_subtitle_followers, R.drawable.ic_lock_24px, StatusPrivacy.PRIVATE, onClick));
			items.add(new ListItem<>(R.string.visibility_private, R.string.visibility_subtitle_private, R.drawable.ic_alternate_email_24px, StatusPrivacy.DIRECT, onClick));
			menu.showAsDropDown(v);
		}
	}

	private void loadDefaultStatusVisibility(Bundle savedInstanceState){
		if(getArguments().containsKey("replyTo")){
			replyTo=Parcels.unwrap(getArguments().getParcelable("replyTo"));
			statusVisibility=replyTo.visibility;
			// MAMMUT: port from upstream master — optionally relax public replies to unlisted
			if(statusVisibility==StatusPrivacy.PUBLIC && GlobalUserPreferences.defaultToUnlistedReplies)
				statusVisibility=StatusPrivacy.UNLISTED;
		}

		// A saved privacy setting from a previous compose session wins over the reply visibility
		if(savedInstanceState!=null){
			statusVisibility=(StatusPrivacy) savedInstanceState.getSerializable("visibility");
		}

		Preferences prevPrefs=AccountSessionManager.getInstance().getAccount(accountID).preferences;
		if(prevPrefs!=null){
			applyPreferencesForPostVisibility(prevPrefs, savedInstanceState);
		}
		AccountSessionManager.getInstance().getAccount(accountID).reloadPreferences(prefs->{
			applyPreferencesForPostVisibility(prefs, savedInstanceState);
		});
	}

	private void applyPreferencesForPostVisibility(Preferences prefs, Bundle savedInstanceState){
		if(quotedStatus!=null && quotedStatus.visibility==StatusPrivacy.UNLISTED){
			statusVisibility=StatusPrivacy.UNLISTED;
		}

		// Only override the reply visibility if our preference is more private.
		// MAMMUT: Composer 2.0 — a resumed draft's visibility wins over defaults.
		if(!restoredDraftVisibility && prefs.postingDefaultVisibility.isLessVisibleThan(statusVisibility)){
			statusVisibility=prefs.postingDefaultVisibility;
		}

		// A saved privacy setting from a previous compose session wins over all
		if(savedInstanceState!=null){
			statusVisibility=(StatusPrivacy) savedInstanceState.getSerializable("visibility");
		}

		if(prefs.postingDefaultQuotePolicy!=null)
			statusQuotePolicy=prefs.postingDefaultQuotePolicy;

		updateVisibilityButton(false);
	}

	private void updateVisibilityButton(boolean animated){
		if(getActivity()==null)
			return;
		if(statusVisibility==null){ // TODO find out why this happens
			statusVisibility=StatusPrivacy.PUBLIC;
		}
		TextView visibilityText;
		if(!animated){
			visibilityText=visibilityCurrentText;
		}else{
			TransitionManager.beginDelayedTransition(visibilityBtn, new TransitionSet()
					.addTransition(new Fade(Fade.IN | Fade.OUT))
					.addTransition(new ChangeBounds().excludeTarget(TextView.class, true))
					.setDuration(250)
					.setInterpolator(CubicBezierInterpolator.DEFAULT)
			);
			visibilityText=visibilityCurrentText==visibilityText1 ? visibilityText2 : visibilityText1;
			visibilityText.setVisibility(View.VISIBLE);
			visibilityCurrentText.setVisibility(View.GONE);
			visibilityCurrentText=visibilityText;
		}
		if(instance.supportsQuotePostAuthoring()){
			visibilityText.setText(switch(statusVisibility){
				case PUBLIC -> switch(statusQuotePolicy){
					case PUBLIC -> R.string.compose_visibility_public_anyone;
					case FOLLOWERS -> R.string.compose_visibility_public_limited;
					case NOBODY -> R.string.compose_visibility_public_disabled;
				};
				case UNLISTED -> switch(statusQuotePolicy){
					case PUBLIC -> R.string.compose_visibility_unlisted_anyone;
					case FOLLOWERS -> R.string.compose_visibility_unlisted_limited;
					case NOBODY -> R.string.compose_visibility_unlisted_disabled;
				};
				case PRIVATE -> R.string.visibility_followers_only;
				case DIRECT -> R.string.visibility_private;
				// MOSHIDON:
				case LOCAL -> R.string.sk_local_only;
			});
		}else{
			visibilityText.setText(switch(statusVisibility){
				case PUBLIC -> R.string.visibility_public;
				case UNLISTED -> R.string.visibility_unlisted;
				case PRIVATE -> R.string.visibility_followers_only;
				case DIRECT -> R.string.visibility_private;
				// MOSHIDON:
				case LOCAL -> R.string.sk_local_only;
			});
		}
		Drawable icon=getResources().getDrawable(switch(statusVisibility){
			case PUBLIC -> R.drawable.ic_public_20px;
			case UNLISTED -> R.drawable.ic_clear_night_20px;
			case PRIVATE -> R.drawable.ic_group_20px;
			case DIRECT -> R.drawable.ic_alternate_email_20px;
			// MOSHIDON:
			case LOCAL -> R.drawable.ic_fluent_eye_16_regular;
		}, getActivity().getTheme()).mutate();
		icon.setBounds(0, 0, V.dp(18), V.dp(18));
		icon.setTint(UiUtils.getThemeColor(getActivity(), R.attr.colorM3OnSurfaceVariant));
		visibilityText.setCompoundDrawablesRelative(icon, null, null, null);
	}

	@Override
	public void onSelectionChanged(int start, int end){
		if(ignoreSelectionChanges)
			return;
		if(start==end && mainEditText.length()>0){
			ComposeAutocompleteSpan[] spans=mainEditText.getText().getSpans(start, end, ComposeAutocompleteSpan.class);
			if(spans.length>0){
				assert spans.length==1;
				ComposeAutocompleteSpan span=spans[0];
				if(currentAutocompleteSpan==null && end==mainEditText.getText().getSpanEnd(span)){
					startAutocomplete(span);
				}else if(currentAutocompleteSpan!=null){
					Editable e=mainEditText.getText();
					String spanText=e.toString().substring(e.getSpanStart(span), e.getSpanEnd(span));
					autocompleteViewController.setText(spanText);
				}
			}else if(currentAutocompleteSpan!=null){
				finishAutocomplete();
			}
		}else if(currentAutocompleteSpan!=null){
			finishAutocomplete();
		}
	}

	@Override
	public String[] onGetAllowedMediaMimeTypes(){
		if(instance!=null && instance.configuration!=null && instance.configuration.mediaAttachments!=null && instance.configuration.mediaAttachments.supportedMimeTypes!=null)
			return instance.configuration.mediaAttachments.supportedMimeTypes.toArray(new String[0]);
		return new String[]{"image/jpeg", "image/gif", "image/png", "video/mp4"};
	}

	// MAMMUT: The Caption Track — also used by ComposeMediaViewController for metadata prefill
	public String sanitizeMediaDescription(String description){
		if(description == null){
			return null;
		}

		// The Gboard android keyboard attaches this text whenever the user
		// pastes something from the keyboard's suggestion bar.
		// Due to different end user locales, the exact text may vary, but at
		// least in version 13.4.08, all of the translations contained the
		// string "Gboard".
		if (description.contains("Gboard")){
			return null;
		}

		return description;
	}

	@Override
	public boolean onAddMediaAttachmentFromEditText(Uri uri, String description){
		description = sanitizeMediaDescription(description);
		return mediaViewController.addMediaAttachment(uri, description);
	}

	private void startAutocomplete(ComposeAutocompleteSpan span){
		currentAutocompleteSpan=span;
		Editable e=mainEditText.getText();
		String spanText=e.toString().substring(e.getSpanStart(span), e.getSpanEnd(span));
		autocompleteViewController.setText(spanText);
		showAutocomplete();
	}

	private void finishAutocomplete(){
		if(currentAutocompleteSpan==null)
			return;
		autocompleteViewController.setText(null);
		currentAutocompleteSpan=null;
		hideAutocomplete();
	}

	private void showAutocomplete(){
		UiUtils.beginLayoutTransition(bottomBar);
		View autocompleteView=autocompleteViewController.getView();
		bottomBar.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
		bottomBar.requestLayout();
		autocompleteView.setVisibility(View.VISIBLE);
		autocompleteDivider.setVisibility(View.VISIBLE);
	}

	private void hideAutocomplete(){
		UiUtils.beginLayoutTransition(bottomBar);
		bottomBar.getLayoutParams().height=V.dp(48);
		bottomBar.requestLayout();
		autocompleteViewController.getView().setVisibility(View.INVISIBLE);
		autocompleteDivider.setVisibility(View.INVISIBLE);
	}

	private void onAutocompleteOptionSelected(String text){
		Editable e=mainEditText.getText();
		int start=e.getSpanStart(currentAutocompleteSpan);
		int end=e.getSpanEnd(currentAutocompleteSpan);
		if(start==-1 || end==-1)
			return;
		e.replace(start, end, text+" ");
		finishAutocomplete();
		InputConnection conn=mainEditText.getCurrentInputConnection();
		if(conn!=null)
			conn.finishComposingText();
	}

	@Override
	public CharSequence getTitle(){
		return getString(R.string.new_post);
	}

	@Override
	public boolean wantsLightStatusBar(){
		return !UiUtils.isDarkTheme();
	}

	@Override
	public boolean wantsLightNavigationBar(){
		return !UiUtils.isDarkTheme();
	}

	public boolean getWasDetached(){
		return wasDetached;
	}

	public boolean isCreatingView(){
		return creatingView;
	}

	public String getAccountID(){
		return accountID;
	}

	public void addFakeMediaAttachment(Uri uri, String description){
		mediaViewController.addFakeMediaAttachment(uri, description);
	}

	private void showLanguageAlert(){
		Preferences prefs=AccountSessionManager.getInstance().getAccount(accountID).preferences;
		ComposeLanguageAlertViewController vc=new ComposeLanguageAlertViewController(getActivity(), prefs!=null ? prefs.postingDefaultLanguage : null, postLang, mainEditText.getText().toString());
		final AlertDialog dlg=new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.language)
				.setView(vc.getView())
				.setPositiveButton(R.string.cancel, null)
				.show();
		vc.setSelectionListener(opt->{
			setPostLanguage(opt);
			dlg.dismiss();
		});
	}

	private void setPostLanguage(ComposeLanguageAlertViewController.SelectedOption language){
		postLang=language;
	}

	// MAMMUT: Composer 2.0 (§4) — segment management. Every method here is
	// thread-only; the edit path never reaches them.

	private ThreadSegment addThreadSegment(Bundle mediaSavedState){
		ThreadSegment ts=new ThreadSegment();
		attachThreadSegmentCard(ts, mediaSavedState);
		int pos=segmentsContainer.indexOfChild(addSegmentBtn);
		segmentsContainer.addView(ts.card, pos);
		extraSegments.add(ts);
		renumberSegments();
		updatePublishButtonState();
		updateMediaPollStates();
		updateDraftState();
		return ts;
	}

	/**
	 * Inflates a card for the holder and wires everything: its own media
	 * controller, its counter, its CW toggle, its overflow, and focus
	 * tracking so the bottom-bar buttons act on it while it holds the
	 * focus. {@code mediaSavedState} is non-null only when recreating a
	 * card after process death.
	 */
	private void attachThreadSegmentCard(ThreadSegment ts, Bundle mediaSavedState){
		View card=getActivity().getLayoutInflater().inflate(R.layout.compose_thread_segment, segmentsContainer, false);
		ts.card=card;
		ts.number=card.findViewById(R.id.segment_number);
		ts.counter=card.findViewById(R.id.segment_counter);
		ts.overflowBtn=card.findViewById(R.id.segment_overflow);
		ts.cwBtn=card.findViewById(R.id.segment_cw);
		ts.text=card.findViewById(R.id.segment_text);
		ts.spoilerWrap=card.findViewById(R.id.segment_spoiler_wrap);
		ts.spoilerEdit=card.findViewById(R.id.segment_content_warning);

		LayerDrawable spoilerBg=(LayerDrawable) ts.spoilerWrap.getBackground().mutate();
		spoilerBg.setDrawableByLayerId(R.id.left_drawable, new SpoilerStripesDrawable(false));
		spoilerBg.setDrawableByLayerId(R.id.right_drawable, new SpoilerStripesDrawable(false));
		ts.spoilerWrap.setBackground(spoilerBg);
		ts.spoilerWrap.setClipToOutline(true);
		ts.spoilerWrap.setOutlineProvider(OutlineProviders.roundedRect(8));

		// ComposeEditText dereferences its selection listener from
		// onCreateInputConnection, so a card's field always gets one:
		// keyboard/paste/drag media lands in the segment it was dropped on.
		ts.text.setSelectionListener(new ComposeEditText.SelectionListener(){
			@Override
			public void onSelectionChanged(int start, int end){}

			@Override
			public String[] onGetAllowedMediaMimeTypes(){
				return ComposeFragment.this.onGetAllowedMediaMimeTypes();
			}

			@Override
			public boolean onAddMediaAttachmentFromEditText(Uri uri, String description){
				description=sanitizeMediaDescription(description);
				return ts.mediaController.addMediaAttachment(uri, description);
			}
		});
		ts.text.addTextChangedListener(new TextWatcher(){
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count){}

			@Override
			public void afterTextChanged(Editable s){
				ts.textMirror=s.toString();
				updateThreadSegmentCounter(ts);
				updateDraftState();
			}
		});
		ts.spoilerEdit.addTextChangedListener(new SimpleTextWatcher(e->{
			ts.spoilerTextMirror=e.toString();
			updateThreadSegmentCounter(ts);
		}));
		View.OnFocusChangeListener focusListener=(v, hasFocus)->{
			if(hasFocus)
				setActiveSegment(extraSegments.indexOf(ts)+1);
		};
		ts.text.setOnFocusChangeListener(focusListener);
		ts.spoilerEdit.setOnFocusChangeListener(focusListener);
		ts.cwBtn.setOnClickListener(v->toggleSpoiler());
		ts.overflowBtn.setOnClickListener(v->showSegmentOverflow(ts.overflowBtn, extraSegments.indexOf(ts)+1));
		ts.mediaController.setView(card, mediaSavedState);
		// Rebuilding an existing holder (rotation): views were destroyed,
		// the state was not.
		if(ts.textMirror!=null)
			ts.text.setText(ts.textMirror);
		if(ts.hasSpoiler){
			ts.spoilerWrap.setVisibility(View.VISIBLE);
			ts.cwBtn.setSelected(true);
			if(ts.spoilerTextMirror!=null)
				ts.spoilerEdit.setText(ts.spoilerTextMirror);
		}
		updateThreadSegmentCounter(ts);
	}

	private void removeSegmentCard(ThreadSegment ts){
		ts.mediaController.cancelAllUploads();
		segmentsContainer.removeView(ts.card);
		extraSegments.remove(ts);
		if(activeSegmentIndex>extraSegments.size())
			activeSegmentIndex=0;
		renumberSegments();
		updateActiveCounterDisplay();
		updateMediaPollStates();
		updatePublishButtonState();
		updateDraftState();
	}

	private void renumberSegments(){
		segmentOneNumber.setText("1");
		segmentOneNumber.setContentDescription(getString(R.string.thread_post_number, 1));
		for(int i=0;i<extraSegments.size();i++){
			ThreadSegment ts=extraSegments.get(i);
			ts.number.setText(String.valueOf(i+2));
			ts.number.setContentDescription(getString(R.string.thread_post_number, i+2));
		}
	}

	private void showSegmentOverflow(View anchor, int index){
		PopupMenu popup=new PopupMenu(getActivity(), anchor);
		popup.inflate(R.menu.compose_segment_overflow);
		int count=extraSegments.size()+1;
		popup.getMenu().findItem(R.id.move_up).setEnabled(index>0);
		popup.getMenu().findItem(R.id.move_down).setEnabled(index<count-1);
		popup.getMenu().findItem(R.id.delete).setVisible(count>1);
		popup.setOnMenuItemClickListener(item->{
			int id=item.getItemId();
			if(id==R.id.move_up)
				moveSegment(index, -1);
			else if(id==R.id.move_down)
				moveSegment(index, 1);
			else if(id==R.id.delete)
				deleteSegment(index);
			return true;
		});
		popup.show();
	}

	/**
	 * §4 — Move up / Move down exchange two adjacent segments wholesale:
	 * text, CW and media. The poll is fixed to the first position (a poll
	 * is allowed on the first segment only), so a first segment carrying
	 * one cannot move down.
	 */
	private void moveSegment(int index, int direction){
		int target=index+direction;
		if(target<0 || target>extraSegments.size())
			return;
		if(index==0 && pollViewController.isShown()){
			Toast.makeText(getActivity(), R.string.thread_move_poll_blocked, Toast.LENGTH_SHORT).show();
			return;
		}
		swapSegments(index, target);
	}

	private void swapSegments(int a, int b){
		// Text
		String textA=segmentTextAt(a);
		setSegmentTextAt(a, segmentTextAt(b));
		setSegmentTextAt(b, textA);
		// CW state
		boolean spoilerA=segmentHasSpoilerAt(a);
		String spoilerTextA=segmentSpoilerTextAt(a);
		setSegmentSpoilerAt(a, segmentHasSpoilerAt(b), segmentSpoilerTextAt(b));
		setSegmentSpoilerAt(b, spoilerA, spoilerTextA);
		// Media (attachment objects move unchanged, uploads and
		// descriptions included)
		segmentMediaAt(a).swapAttachmentsWith(segmentMediaAt(b));
		renumberSegments();
		updateCharCounter();
		updateActiveCounterDisplay();
		updateMediaPollStates();
		updatePublishButtonState();
		updateDraftState();
	}

	private String segmentSpoilerTextAt(int index){
		EditText field=index==0 ? spoilerEdit : extraSegments.get(index-1).spoilerEdit;
		return field.getText().toString();
	}

	private void setSegmentTextAt(int index, String text){
		EditText field=index==0 ? mainEditText : extraSegments.get(index-1).text;
		field.setText(text);
	}

	private void setSegmentSpoilerAt(int index, boolean has, String text){
		if(index==0){
			hasSpoiler=has;
			spoilerWrap.setVisibility(has ? View.VISIBLE : View.GONE);
			spoilerBtn.setSelected(has);
			spoilerEdit.setText(text);
		}else{
			ThreadSegment ts=extraSegments.get(index-1);
			ts.hasSpoiler=has;
			ts.spoilerWrap.setVisibility(has ? View.VISIBLE : View.GONE);
			ts.cwBtn.setSelected(has);
			ts.spoilerEdit.setText(text);
		}
	}

	/**
	 * §4 — a segment can be removed unless it is the only one. Deleting the
	 * first segment promotes the second onto the main surface.
	 */
	private void deleteSegment(int index){
		if(extraSegments.isEmpty())
			return;
		if(index==0){
			// The poll belongs to the deleted segment; it dies with it
			// rather than inheriting the segment that moves up.
			if(pollViewController.isShown())
				pollViewController.toggle();
			swapSegments(0, 1);
			removeSegmentCard(extraSegments.get(0));
		}else{
			removeSegmentCard(extraSegments.get(index-1));
		}
	}

	/**
	 * MAMMUT: Composer 2.0 (§4) — one card of the thread below the first
	 * segment. Holds the card's views, its own media controller and its
	 * persisted idempotency key; {@link #textMirror} and
	 * {@link #spoilerTextMirror} keep the text across view rebuilds
	 * (rotation), since the EditText itself does not survive them.
	 */
	private class ThreadSegment{
		View card;
		TextView number, counter;
		ImageButton overflowBtn, cwBtn;
		ComposeEditText text;
		View spoilerWrap;
		EditText spoilerEdit;
		final ComposeMediaViewController mediaController=new ComposeMediaViewController(ComposeFragment.this);
		String idempotencyKey;
		boolean hasSpoiler;
		String textMirror, spoilerTextMirror;
		int charCount, trimmedCharCount;
	}

	@Override
	public Animator onCreateEnterTransition(View prev, View container){
		AnimatorSet anim=new AnimatorSet();
		if(getArguments().getBoolean("fromThreadFragment")){
			anim.playTogether(
					ObjectAnimator.ofFloat(container, View.ALPHA, 0f, 1f),
					ObjectAnimator.ofFloat(container, View.TRANSLATION_Y, V.dp(200), 0)
			);
		}else{
			anim.playTogether(
					ObjectAnimator.ofFloat(container, View.ALPHA, 0f, 1f),
					ObjectAnimator.ofFloat(container, View.TRANSLATION_X, V.dp(100), 0)
			);
		}
		anim.setDuration(300);
		anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
		return anim;
	}

	@Override
	public Animator onCreateExitTransition(View prev, View container){
		AnimatorSet anim=new AnimatorSet();
		anim.playTogether(
				ObjectAnimator.ofFloat(container, View.TRANSLATION_X, V.dp(100)),
				ObjectAnimator.ofFloat(container, View.ALPHA, 0)
		);
		anim.setDuration(200);
		anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
		return anim;
	}
}
