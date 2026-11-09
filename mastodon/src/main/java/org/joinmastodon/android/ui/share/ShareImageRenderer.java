package org.joinmastodon.android.ui.share;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Emoji;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.model.Poll;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.text.CustomEmojiSpan;
import org.joinmastodon.android.ui.text.MammutTypeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.ImageLoaderCallback;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.V;

/**
 * Renders one status into a 1080 px wide PNG card in {@code cacheDir/share/}, off
 * the UI thread. Geometry per the post-as-image spec §4–§6 (slice A) and §11
 * (slice B: quote block, poll block); the pure decisions live in
 * {@link MediaGridSolver}, {@link TruncationChooser}, {@link SensitiveMedia},
 * {@link QuoteBlock}, {@link PollBlock} and {@link CardFlow}.
 *
 * The whole hierarchy is created, measured and drawn on the render thread only,
 * using the application context, so no Activity is held past fragment death.
 * Callers hand over the already-parsed text (built with a themed context on the
 * UI thread) and the captured theme palette, and receive the finished file on the
 * render thread.
 *
 * SetTextI18n is suppressed deliberately: the card's chrome strings are fixed by
 * the spec ("Mammut", "CW: ", "Sensitive media", …) and are baked into a bitmap,
 * not shown as UI text.
 */
@SuppressLint("SetTextI18n")
public class ShareImageRenderer{
	private static final String TAG="ShareImageRenderer";

	public static final int CARD_WIDTH=1080;
	public static final int SIDE_PADDING=56;
	public static final int CONTENT_WIDTH=CARD_WIDTH-SIDE_PADDING*2; // 968
	public static final int MAX_HEIGHT=8000;

	// §4 geometry (px on the card canvas; the spec's sp values map 1:1 to px here)
	private static final int AVATAR_SIZE=96;
	private static final int AVATAR_GAP=24;
	private static final int NAME_SIZE=40;
	private static final int HANDLE_SIZE=30;
	private static final int TIMESTAMP_SIZE=26;
	private static final int BODY_SIZE=38;
	private static final float BODY_LINE_SPACING=1.35f;
	private static final int MEDIA_RADIUS=24;
	private static final int CW_SIZE=32;
	private static final int ELLIPSIS_SIZE=38;
	private static final int CONTINUE_SIZE=30;
	private static final int FOOTER_RULE_GAP=40;
	private static final int FOOTER_SIZE=24;
	private static final int LABEL_SIZE=32;

	// §11 geometry (slice B): gaps between content blocks come from CardFlow;
	// poll and quote registers live here
	private static final int POLL_QUESTION_SIZE=34;
	private static final int POLL_OPTION_SIZE=32;
	private static final int POLL_FOOTER_SIZE=26;
	private static final int POLL_QUESTION_GAP=24;
	private static final int POLL_OPTION_GAP=20;
	private static final int POLL_FOOTER_GAP=20;
	private static final int POLL_PCT_MARGIN=24;
	private static final int QUOTE_TILE_LABEL_SIZE=24;

	private static final int TILE_BLUR_RADIUS=32;
	private static final int BADGE_INSET=16;
	/** Empty poll-bar track: {@code outlineVariant} at ~18% alpha, per the M1 review fix. */
	private static final int TRACK_ALPHA=0x2E;
	private static final long IMAGE_LOAD_TIMEOUT_MS=15_000;
	private static final long CACHE_MAX_AGE_MS=60*60*1000L;

	private static final AtomicInteger generation=new AtomicInteger();
	private static final ExecutorService executor=Executors.newSingleThreadExecutor(new ThreadFactory(){
		@Override
		public Thread newThread(Runnable r){
			Thread t=new Thread(r, "ShareImageRenderer");
			t.setDaemon(true);
			return t;
		}
	});
	private static Future<?> pendingFuture;

	/** Theme colours captured on the UI thread from the activity, so the card matches the active palette. */
	public static class Palette{
		public final int surface, onSurface, onSurfaceVariant, primary, outlineVariant, surfaceVariant;

		public Palette(int surface, int onSurface, int onSurfaceVariant, int primary, int outlineVariant, int surfaceVariant){
			this.surface=surface;
			this.onSurface=onSurface;
			this.onSurfaceVariant=onSurfaceVariant;
			this.primary=primary;
			this.outlineVariant=outlineVariant;
			this.surfaceVariant=surfaceVariant;
		}
	}

	public interface Callback{
		void onSuccess(File file);
		void onError(Throwable error);
	}

	/**
	 * Queue a render. At most one render runs at a time; a new tap supersedes any
	 * pending request. Callbacks fire on the render thread — post to the main
	 * thread yourself.
	 */
	public static synchronized void enqueue(Context appContext, Status status, SpannableStringBuilder parsedText, Palette palette, Callback callback){
		enqueue(appContext, status, parsedText, null, palette, callback);
	}

	/**
	 * Slice B overload: also takes the quoted status's text, already parsed with a
	 * themed context on the UI thread (same contract as {@code parsedText}). May
	 * be null when the status has no rendered quote.
	 */
	public static synchronized void enqueue(Context appContext, Status status, SpannableStringBuilder parsedText, SpannableStringBuilder parsedQuoteText, Palette palette, Callback callback){
		generation.incrementAndGet();
		if(pendingFuture!=null)
			pendingFuture.cancel(false);
		final int gen=generation.get();
		pendingFuture=executor.submit(()->{
			if(gen!=generation.get())
				return; // superseded before we started
			try{
				File file=new ShareImageRenderer(appContext, status, parsedText, parsedQuoteText, palette).render();
				if(gen==generation.get())
					callback.onSuccess(file);
			}catch(Throwable t){
				if(gen==generation.get())
					callback.onError(t);
			}
		});
	}

	/**
	 * §11 gate, shared with callers that must parse the quoted text on the UI
	 * thread: the quote block renders only for an accepted quote with the quoted
	 * status attached (the timeline's gate in StatusDisplayItem). Never recurses:
	 * a quoted status that itself quotes renders its own text only.
	 */
	public static Status quotedStatus(Status status){
		if(status==null || !QuoteBlock.shouldRender(status.quote))
			return null;
		return status.quote.quotedStatus;
	}

	private final Context appContext;
	private final Status status;
	private final SpannableStringBuilder parsedText;
	private final SpannableStringBuilder parsedQuoteText;
	private final Palette palette;

	private LinearLayout card;
	private Space mediaSpace;
	private boolean bodyPresent, mediaPresent, cwPresent;
	private int mediaTopInCard;

	// §11 state (slice B)
	private Status quoted;
	private boolean quotePresent, pollPresent;
	private LinearLayout quoteContainer;
	private Space quoteTileSpace;
	private Drawable quoteTileDrawable;
	private boolean quoteTileBlurred;
	private Space pollSpace;
	private int pollTopInCard;
	private TextView pollQuestion;
	private TextView[] pollOptionLabels;
	private int[] pollOptionHeights;
	private int pollOptionLabelWidth;
	private TextView pollFooter;
	private int[] pollPcts;
	private boolean[] pollLeaders;

	private ShareImageRenderer(Context appContext, Status status, SpannableStringBuilder parsedText, SpannableStringBuilder parsedQuoteText, Palette palette){
		this.appContext=appContext.getApplicationContext();
		this.status=status;
		this.parsedText=parsedText;
		this.parsedQuoteText=parsedQuoteText;
		this.palette=palette;
	}

	private File render() throws Exception{
		try{
			return renderOnce(true);
		}catch(Exception first){
			// Degrade before giving up: the card's job is the post's words, and a
			// broken avatar, emoji or media fetch should cost the picture they
			// bring, not the whole share. A second pass without remote images
			// either produces a usable card or surfaces a cause that is not
			// image-related at all.
			Log.w(TAG, "Render with images failed, retrying without them", first);
			try{
				return renderOnce(false);
			}catch(Exception second){
				second.addSuppressed(first);
				throw second;
			}
		}
	}

	private File renderOnce(boolean withImages) throws Exception{
		File dir=cleanupAndGetShareDir();
		if(withImages)
			loadEmojiDrawables();
		Drawable avatar=withImages ? loadAvatar() : null;
		List<Attachment> attachments=status.mediaAttachments!=null ? status.mediaAttachments : List.of();
		MediaPrep media=withImages ? prepareMedia(attachments) : MediaPrep.none(attachments.size());
		quoted=quotedStatus(status);
		if(quoted!=null){
			if(parsedQuoteText!=null)
				loadEmojiDrawables(parsedQuoteText);
			prepareQuoteTile();
		}

		int totalHeight=measureAndLayout(attachments, media);
		if(totalHeight>MAX_HEIGHT)
			throw new IllegalStateException("Card measured "+totalHeight+" px, over the "+MAX_HEIGHT+" px cap");

		Bitmap bitmap=Bitmap.createBitmap(CARD_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
		Canvas canvas=new Canvas(bitmap);
		card.draw(canvas);
		drawAvatar(canvas, avatar);
		if(mediaPresent)
			drawMedia(canvas, attachments, media);
		if(quoteTileSpace!=null)
			drawQuoteTile(canvas);
		if(pollPresent)
			drawPoll(canvas);

		File out=new File(dir, "post-"+safeFileName(status.id)+"-"+System.currentTimeMillis()+".png");
		try(FileOutputStream fos=new FileOutputStream(out)){
			if(!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos))
				throw new IOException("Bitmap.compress returned false");
		}
		return out;
	}

	//region Image loading

	private File cleanupAndGetShareDir(){
		File dir=new File(appContext.getCacheDir(), "share");
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		long now=System.currentTimeMillis();
		File[] files=dir.listFiles();
		if(files!=null){
			for(File f:files){
				if(f.isFile() && now-f.lastModified()>CACHE_MAX_AGE_MS){
					//noinspection ResultOfMethodCallIgnored
					f.delete();
				}
			}
		}
		return dir;
	}

	/**
	 * Every custom-emoji drawable must be resolved before draw; a pending span would
	 * render as a blank box.
	 */
	/**
	 * Every image this class decodes must be a software bitmap: the card is drawn on an
	 * off-screen {@link Canvas}, and appkit's {@code UrlImageLoaderRequest(url, w, h)}
	 * leaves the config null, which lets the decoder hand back {@link Bitmap.Config#HARDWARE}
	 * on API 26+. Asking for ARGB_8888 is the fix at the source; the toSoftware() conversion
	 * in the draw path stays as a backstop for anything already cached.
	 */
	private static ImageLoaderRequest softwareRequest(String url, int width, int height){
		return new UrlImageLoaderRequest(Bitmap.Config.ARGB_8888, width, height, List.of(), Uri.parse(url));
	}

	private void loadEmojiDrawables() throws Exception{
		loadEmojiDrawables(parsedText);
	}

	/**
	 * Shared emoji-resolution core for the body text (slice A) and the quoted
	 * text (slice B) — identical rule, just a different Spanned.
	 */
	private void loadEmojiDrawables(SpannableStringBuilder text) throws Exception{
		if(text==null)
			return;
		CustomEmojiSpan[] spans=text.getSpans(0, text.length(), CustomEmojiSpan.class);
		ArrayList<List<CustomEmojiSpan>> groups=new ArrayList<>();
		ArrayList<ImageLoaderRequest> requests=new ArrayList<>();
		for(CustomEmojiSpan span:spans){
			int existing=-1;
			for(int i=0;i<groups.size();i++){
				if(groups.get(i).get(0).emoji.equals(span.emoji)){
					existing=i;
					break;
				}
			}
			if(existing>=0){
				groups.get(existing).add(span);
			}else{
				ArrayList<CustomEmojiSpan> group=new ArrayList<>();
				group.add(span);
				groups.add(group);
				Emoji emoji=group.get(0).emoji;
				// Same URL choice the span itself makes, but decoded as software.
				requests.add(softwareRequest(GlobalUserPreferences.playGifs ? emoji.url : emoji.staticUrl, V.dp(20), V.dp(20)));
			}
		}
		for(int i=0;i<requests.size();i++){
			// Hard rule: custom emoji must be resolved before drawing — a null
			// drawable would draw a blank box, so a load failure fails the render
			// (and the share) instead.
			Drawable d=load(requests.get(i));
			for(CustomEmojiSpan span:groups.get(i))
				span.setDrawable(d);
		}
	}

	private Drawable loadAvatar(){
		Account account=status.account;
		String url=account.avatarStatic!=null ? account.avatarStatic : account.avatar;
		if(TextUtils.isEmpty(url))
			return null;
		try{
			return load(softwareRequest(url, AVATAR_SIZE, AVATAR_SIZE));
		}catch(Exception x){
			// A missing avatar is decoration, not content: the card still has the
			// display name, the handle and the post itself. Failing the whole share
			// over a picture the reader does not need is the wrong trade.
			Log.w(TAG, "Avatar failed to load, drawing the card without it", x);
			return null;
		}
	}

	private static class MediaPrep{
		static MediaPrep none(int count){
			int shown=Math.min(count, MediaGridSolver.MAX_TILES);
			return new MediaPrep(new Drawable[shown], new boolean[shown]);
		}

		/** Drawable per tile index; null → draw a placeholder tile. */
		final Drawable[] drawables;
		/** Blur decision per tile index. */
		final boolean[] blurred;

		MediaPrep(Drawable[] drawables, boolean[] blurred){
			this.drawables=drawables;
			this.blurred=blurred;
		}
	}

	private MediaPrep prepareMedia(List<Attachment> attachments){
		int shown=Math.min(attachments.size(), MediaGridSolver.MAX_TILES);
		Drawable[] drawables=new Drawable[shown];
		// This build's Attachment model has no per-attachment sensitive flag; the
		// status flag applies to every tile. The pure rule accepts both inputs.
		boolean[] attachmentFlags=new boolean[shown];
		boolean[] blurred=SensitiveMedia.blurFlags(status.sensitive, attachmentFlags);
		for(int i=0;i<shown;i++){
			Attachment att=attachments.get(i);
			if(att.type!=Attachment.Type.IMAGE || TextUtils.isEmpty(att.previewUrl))
				continue; // placeholder tile per §5
			try{
				drawables[i]=load(softwareRequest(att.previewUrl, MediaGridSolver.TILE*2, MediaGridSolver.TILE*2));
			}catch(Exception x){
				Log.w(TAG, "Failed to load media "+att.previewUrl, x);
			}
		}
		return new MediaPrep(drawables, blurred);
	}

	/**
	 * §11: the quoted status's first media attachment becomes a 160 × 160 tile.
	 * Blur decision is the §5 rule with the quoted status's sensitive flag. A
	 * missing or failed image leaves the drawable null; the draw step then
	 * paints a neutral placeholder, never a blank hole.
	 */
	private void prepareQuoteTile(){
		List<Attachment> attachments=quoted.mediaAttachments!=null ? quoted.mediaAttachments : List.of();
		if(!QuoteBlock.hasMediaTile(attachments.size()))
			return;
		Attachment att=attachments.get(0);
		// This build's Attachment model has no per-attachment sensitive flag;
		// the quoted status's flag applies, mirroring prepareMedia.
		quoteTileBlurred=SensitiveMedia.isBlurred(quoted.sensitive, false);
		if(att.type!=Attachment.Type.IMAGE || TextUtils.isEmpty(att.previewUrl))
			return; // placeholder tile at draw time
		try{
			quoteTileDrawable=load(softwareRequest(att.previewUrl, QuoteBlock.MEDIA_TILE*2, QuoteBlock.MEDIA_TILE*2));
		}catch(Exception x){
			Log.w(TAG, "Failed to load quoted media "+att.previewUrl, x);
		}
	}

	/**
	 * Load one image through the app image loader, blocking the render thread until
	 * it resolves. Memory cache → disk cache → download.
	 */
	private Drawable load(ImageLoaderRequest req) throws Exception{
		ImageCache cache=ImageCache.getInstance(appContext);
		Drawable cached=cache.getFromTop(req);
		if(cached!=null)
			return cached;
		File file=cache.getFile(req);
		if(file!=null){
			try{
				return cache.decodeImage(file, null, req);
			}catch(IOException ignore){
				// corrupted cache entry — fall through and re-download
			}
		}
		CountDownLatch latch=new CountDownLatch(1);
		Drawable[] out=new Drawable[1];
		Throwable[] err=new Throwable[1];
		cache.get(req, null, new ImageLoaderCallback(){
			@Override
			public void onImageLoaded(ImageLoaderRequest request, Drawable drawable){
				out[0]=drawable;
				latch.countDown();
			}

			@Override
			public void onImageLoadingFailed(ImageLoaderRequest request, Throwable throwable){
				err[0]=throwable;
				latch.countDown();
			}
		}, true);
		if(!latch.await(IMAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS))
			throw new TimeoutException("Timed out loading "+req);
		if(err[0]!=null)
			throw new Exception("Failed to load "+req, err[0]);
		if(out[0]==null)
			throw new IllegalStateException("Image loader returned null for "+req);
		return out[0];
	}

	//endregion

	//region Layout

	private int measureAndLayout(List<Attachment> attachments, MediaPrep media){
		int cardWidthSpec=View.MeasureSpec.makeMeasureSpec(CARD_WIDTH, View.MeasureSpec.EXACTLY);
		int contentWidthSpec=View.MeasureSpec.makeMeasureSpec(CONTENT_WIDTH, View.MeasureSpec.EXACTLY);
		int unspecified=View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

		card=new LinearLayout(appContext);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackgroundColor(palette.surface);
		card.setPadding(SIDE_PADDING, SIDE_PADDING, SIDE_PADDING, SIDE_PADDING);

		buildHeader();

		Spanned bodyText=stripTrailingNewlines(parsedText);
		bodyPresent=bodyText.length()>0;
		cwPresent=!TextUtils.isEmpty(status.spoilerText);
		mediaPresent=!attachments.isEmpty();
		quotePresent=quoted!=null;
		pollPresent=status.poll!=null && status.poll.options!=null && !status.poll.options.isEmpty();

		// §11 ordering and gaps come from CardFlow: every gap belongs to the
		// block that follows it (header → first block, block → block), derived
		// from the blocks actually inserted — never from bodyPresent, which is
		// also true when the poll consumes the text as its question.
		TextView body=null;
		List<CardFlow.Entry> flow=CardFlow.build(new CardFlow.Input(cwPresent, bodyPresent, quotePresent, pollPresent, mediaPresent));
		for(CardFlow.Entry entry:flow){
			card.addView(vspace(entry.gapAbove));
			switch(entry.kind){
				case CW -> {
					TextView cw=newTextView(CW_SIZE, palette.onSurface, familyTypeface(500, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
					cw.setText("CW: "+status.spoilerText);
					card.addView(cw, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
				}
				case BODY -> {
					body=newBodyTextView();
					body.setText(bodyText);
					card.addView(body, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
				}
				case QUOTE -> buildQuoteBlock();
				case POLL -> {
					pollSpace=vspace(measurePollBlock(status.poll, entry.pollQuestion ? bodyText : null));
					card.addView(pollSpace);
				}
				case MEDIA -> {
					mediaSpace=vspace(mediaHeight(attachments));
					card.addView(mediaSpace);
				}
			}
		}
		card.addView(vspace(FOOTER_RULE_GAP));
		View rule=new View(appContext);
		rule.setBackgroundColor(palette.outlineVariant);
		card.addView(rule, new LinearLayout.LayoutParams(CONTENT_WIDTH, 1));
		buildFooter();

		card.measure(cardWidthSpec, unspecified);
		if(card.getMeasuredHeight()>MAX_HEIGHT && body!=null){
			truncateBody(body, bodyText);
			card.measure(cardWidthSpec, unspecified);
		}

		card.layout(0, 0, CARD_WIDTH, card.getMeasuredHeight());
		if(mediaSpace!=null)
			// mediaSpace.getTop() is relative to the card's own origin:
			// LinearLayout positions children starting at its paddingTop,
			// so SIDE_PADDING is already included. Adding it again here
			// would double-count and push the grid past the footer rule.
			mediaTopInCard=mediaSpace.getTop();
		if(pollSpace!=null)
			// Same parent-relative coordinate as mediaSpace above.
			pollTopInCard=pollSpace.getTop();
		return card.getMeasuredHeight();
	}

	/**
	 * §6: cut the body at the last complete paragraph that fits the 8000 px cap,
	 * then append an ellipsis line and a "Continue reading" line.
	 */
	private void truncateBody(TextView body, Spanned bodyText){
		int bodyBudget=MAX_HEIGHT
				-(card.getMeasuredHeight()-body.getMeasuredHeight())
				-lineHeight(ELLIPSIS_SIZE)
				-lineHeight(CONTINUE_SIZE);

		List<Spanned> paragraphs=splitParagraphs(bodyText);
		ArrayList<Integer> heights=new ArrayList<>(paragraphs.size());
		int widthSpec=View.MeasureSpec.makeMeasureSpec(CONTENT_WIDTH, View.MeasureSpec.EXACTLY);
		for(Spanned p:paragraphs){
			TextView probe=newBodyTextView();
			probe.setText(p);
			heights.add(measureView(probe, widthSpec));
		}

		TruncationChooser.Result choice=TruncationChooser.choose(heights, lineHeight(BODY_SIZE), bodyBudget);
		SpannableStringBuilder truncated=new SpannableStringBuilder();
		for(int i=0;i<choice.paragraphsIncluded;i++){
			if(i>0)
				truncated.append("\n\n");
			truncated.append(paragraphs.get(i));
		}
		body.setText(truncated);
		if(!choice.truncated)
			return;

		int bodyIndex=card.indexOfChild(body);
		TextView ellipsis=newTextView(ELLIPSIS_SIZE, palette.onSurface, familyTypeface(400, Typeface.DEFAULT));
		ellipsis.setText("…");
		card.addView(ellipsis, bodyIndex+1);
		TextView continueLine=newTextView(CONTINUE_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		continueLine.setText("Continue reading at "+status.url);
		card.addView(continueLine, bodyIndex+2);
	}

	private List<Spanned> splitParagraphs(Spanned text){
		ArrayList<Spanned> paragraphs=new ArrayList<>();
		String plain=text.toString();
		int start=0;
		while(true){
			int idx=plain.indexOf("\n\n", start);
			if(idx<0)
				break;
			paragraphs.add((Spanned)text.subSequence(start, idx));
			start=idx+2;
		}
		paragraphs.add((Spanned)text.subSequence(start, text.length()));
		return paragraphs;
	}

	private void buildHeader(){
		LinearLayout header=new LinearLayout(appContext);
		header.setOrientation(LinearLayout.HORIZONTAL);

		header.addView(vspace(AVATAR_SIZE, AVATAR_SIZE));
		header.addView(vspace(AVATAR_GAP, AVATAR_GAP));

		LinearLayout names=new LinearLayout(appContext);
		names.setOrientation(LinearLayout.VERTICAL);
		Account account=status.account;
		String displayName=TextUtils.isEmpty(account.displayName) ? account.username : account.displayName;
		int namesWidth=CONTENT_WIDTH-AVATAR_SIZE-AVATAR_GAP;

		TextView name=newTextView(NAME_SIZE, palette.onSurface, familyTypeface(500, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
		name.setText(displayName);
		names.addView(name, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView handle=newTextView(HANDLE_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		handle.setText(fullHandle(account));
		names.addView(handle, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView timestamp=newTextView(TIMESTAMP_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		timestamp.setText(status.createdAt.atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT));
		names.addView(timestamp, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		header.addView(names);
		card.addView(header, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private void buildFooter(){
		LinearLayout footer=new LinearLayout(appContext);
		footer.setOrientation(LinearLayout.HORIZONTAL);

		TextView url=newTextView(FOOTER_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		url.setText(status.url);
		url.setSingleLine(true);
		url.setEllipsize(TextUtils.TruncateAt.END);
		footer.addView(url, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

		TextView wordmark=newTextView(FOOTER_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		wordmark.setText("Mammut");
		footer.addView(wordmark);

		card.addView(footer, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/**
	 * §11 quote block: 1 px outline rounded 20 px, background
	 * {@code darken(colorM3SurfaceVariant, 0.06f)}, inner padding 32 px, author
	 * name + handle at 28 sp, text clamped to 4 lines at 32 sp, and the quoted
	 * status's first media thumbnail as a 160 × 160 tile on the right (drawn
	 * manually like the main media grid). The author lines and text are real
	 * TextViews, so they measure and draw with the same machinery as the body.
	 */
	private void buildQuoteBlock(){
		quoteContainer=new LinearLayout(appContext);
		quoteContainer.setOrientation(LinearLayout.HORIZONTAL);
		GradientDrawable background=new GradientDrawable();
		background.setColor(darken(palette.surfaceVariant, 0.06f));
		background.setStroke(QuoteBlock.OUTLINE_WIDTH, palette.outlineVariant);
		background.setCornerRadius(QuoteBlock.CORNER_RADIUS);
		quoteContainer.setBackground(background);
		quoteContainer.setPadding(QuoteBlock.INNER_PADDING, QuoteBlock.INNER_PADDING, QuoteBlock.INNER_PADDING, QuoteBlock.INNER_PADDING);

		List<Attachment> attachments=quoted.mediaAttachments!=null ? quoted.mediaAttachments : List.of();
		boolean tile=QuoteBlock.hasMediaTile(attachments.size());

		LinearLayout textColumn=new LinearLayout(appContext);
		textColumn.setOrientation(LinearLayout.VERTICAL);
		Account account=quoted.account;
		String displayName=TextUtils.isEmpty(account.displayName) ? account.username : account.displayName;

		TextView name=newTextView(QuoteBlock.AUTHOR_SIZE, palette.onSurface, familyTypeface(500, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
		name.setText(displayName);
		textColumn.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView handle=newTextView(QuoteBlock.AUTHOR_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		handle.setText(fullHandle(account));
		textColumn.addView(handle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		Spanned quoteText=stripTrailingNewlines(parsedQuoteText);
		if(quoteText.length()>0){
			TextView text=newTextView(QuoteBlock.TEXT_SIZE, palette.onSurface, familyTypeface(400, Typeface.DEFAULT));
			text.setLineSpacing(0, BODY_LINE_SPACING);
			text.setLinkTextColor(palette.primary);
			text.setMaxLines(QuoteBlock.MAX_TEXT_LINES);
			text.setEllipsize(TextUtils.TruncateAt.END);
			text.setText(quoteText);
			LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin=12;
			textColumn.addView(text, lp);
		}

		quoteContainer.addView(textColumn, new LinearLayout.LayoutParams(QuoteBlock.textColumnWidth(CONTENT_WIDTH, tile), ViewGroup.LayoutParams.WRAP_CONTENT));
		if(tile){
			quoteTileSpace=vspace(QuoteBlock.MEDIA_TILE, QuoteBlock.MEDIA_TILE);
			LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(QuoteBlock.MEDIA_TILE, QuoteBlock.MEDIA_TILE);
			lp.leftMargin=QuoteBlock.MEDIA_GAP;
			quoteContainer.addView(quoteTileSpace, lp);
		}
		card.addView(quoteContainer, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/**
	 * §11 poll block: the Mastodon API has no poll question field — the status's
	 * own text is the question, so a poll post's body is drawn here at 34 sp
	 * semibold instead of as the regular body block. Options are 32 sp labels
	 * with the rounded percentage right-aligned and a 12 px bar behind the row;
	 * the footer carries votes and the closed/ends-in state.
	 *
	 * All text is measured here with real TextViews (kept for the draw step);
	 * only the bars and percentages are drawn by hand, so they can sit behind
	 * the labels.
	 *
	 * @return the block's total height for the Space in the card layout
	 */
	private int measurePollBlock(Poll poll, Spanned questionText){
		int[] votes=new int[poll.options.size()];
		for(int i=0;i<votes.length;i++)
			votes[i]=poll.options.get(i).votesCount!=null ? poll.options.get(i).votesCount : 0;
		int total=PollBlock.totalVotes(poll.votersCount, poll.votesCount);
		pollPcts=PollBlock.percentages(votes, total);
		pollLeaders=new boolean[votes.length];
		for(int i=0;i<votes.length;i++)
			pollLeaders[i]=PollBlock.isLeader(i, votes);

		// The percentage column reserves room for the widest printable label.
		Paint probe=new Paint(Paint.ANTI_ALIAS_FLAG);
		probe.setTextSize(POLL_OPTION_SIZE);
		probe.setTypeface(familyTypeface(400, null));
		int pctWidth=(int)Math.ceil(probe.measureText("100%"));
		pollOptionLabelWidth=CONTENT_WIDTH-pctWidth-POLL_PCT_MARGIN;

		int widthSpec=View.MeasureSpec.makeMeasureSpec(CONTENT_WIDTH, View.MeasureSpec.EXACTLY);
		int height=0;
		if(questionText!=null && questionText.length()>0){
			pollQuestion=newTextView(POLL_QUESTION_SIZE, palette.onSurface, familyTypeface(500, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
			pollQuestion.setText(questionText);
			int questionHeight=measureView(pollQuestion, widthSpec);
			pollQuestion.layout(0, 0, CONTENT_WIDTH, questionHeight);
			height+=questionHeight+POLL_QUESTION_GAP;
		}
		pollOptionLabels=new TextView[votes.length];
		pollOptionHeights=new int[votes.length];
		int optionWidthSpec=View.MeasureSpec.makeMeasureSpec(pollOptionLabelWidth, View.MeasureSpec.EXACTLY);
		for(int i=0;i<votes.length;i++){
			TextView label=newTextView(POLL_OPTION_SIZE, palette.onSurface, familyTypeface(400, Typeface.DEFAULT));
			String title=poll.options.get(i).title;
			label.setText(title!=null ? title : "");
			int labelHeight=measureView(label, optionWidthSpec);
			label.layout(0, 0, pollOptionLabelWidth, labelHeight);
			pollOptionLabels[i]=label;
			pollOptionHeights[i]=labelHeight;
			height+=Math.max(labelHeight, PollBlock.BAR_HEIGHT);
			if(i<votes.length-1)
				height+=POLL_OPTION_GAP;
		}
		pollFooter=newTextView(POLL_FOOTER_SIZE, palette.onSurfaceVariant, familyTypeface(400, Typeface.DEFAULT));
		Long expiresAt=poll.expiresAt!=null ? poll.expiresAt.toEpochMilli() : null;
		pollFooter.setText(PollBlock.footer(poll.votesCount, poll.isExpired(), expiresAt, System.currentTimeMillis()));
		int footerHeight=measureView(pollFooter, widthSpec);
		pollFooter.layout(0, 0, CONTENT_WIDTH, footerHeight);
		height+=POLL_FOOTER_GAP+footerHeight;
		return height;
	}

	/** §11: background = {@code darken(colorM3SurfaceVariant, 0.06f)}. */
	private static int darken(int color, float amount){
		float f=1f-amount;
		int a=color>>>24, r=(color>>>16)&0xFF, g=(color>>>8)&0xFF, b=color&0xFF;
		return a<<24 | Math.round(r*f)<<16 | Math.round(g*f)<<8 | Math.round(b*f);
	}

	private TextView newBodyTextView(){
		TextView tv=newTextView(BODY_SIZE, palette.onSurface, familyTypeface(400, Typeface.DEFAULT));
		tv.setLineSpacing(0, BODY_LINE_SPACING);
		tv.setLinkTextColor(palette.primary);
		return tv;
	}

	/**
	 * §4.2: every face on the card comes from the reader's active family, so a
	 * shared image matches the screen. The fallback is what the card rendered
	 * before this feature — asking for a family face at SYSTEM returns null
	 * and costs nothing, and the card keeps its old look byte-for-byte.
	 */
	private Typeface familyTypeface(int weight, Typeface fallback){
		Typeface font=MammutTypeface.get(GlobalUserPreferences.fontFamily, weight, false);
		return font!=null ? font : fallback;
	}

	private TextView newTextView(int sizePx, int color, Typeface typeface){
		TextView tv=new TextView(appContext);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
		tv.setTextColor(color);
		tv.setTypeface(typeface);
		return tv;
	}

	private Space vspace(int height){
		return vspace(CONTENT_WIDTH, height);
	}

	private Space vspace(int width, int height){
		Space s=new Space(appContext);
		s.setLayoutParams(new LinearLayout.LayoutParams(width, height));
		return s;
	}

	private static int measureView(View v, int widthSpec){
		v.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		return v.getMeasuredHeight();
	}

	private int lineHeight(int textSizePx){
		TextView probe=newTextView(textSizePx, palette.onSurface, familyTypeface(400, Typeface.DEFAULT));
		probe.setText("Mg");
		return measureView(probe, View.MeasureSpec.makeMeasureSpec(CONTENT_WIDTH, View.MeasureSpec.EXACTLY));
	}

	private int mediaHeight(List<Attachment> attachments){
		return MediaGridSolver.gridHeight(attachments.size(), firstAspect(attachments));
	}

	private static Spanned stripTrailingNewlines(SpannableStringBuilder text){
		if(text==null)
			return new SpannableStringBuilder("");
		int end=text.length();
		while(end>0 && text.charAt(end-1)=='\n')
			end--;
		return (Spanned)text.subSequence(0, end);
	}

	private static String fullHandle(Account account){
		if(account.acct!=null && account.acct.contains("@"))
			return "@"+account.acct;
		String host=null;
		try{
			if(account.url!=null)
				host=Uri.parse(account.url).getHost();
		}catch(Exception ignore){}
		return host!=null ? "@"+account.acct+"@"+host : "@"+account.acct;
	}

	private static String safeFileName(String id){
		return id==null ? "unknown" : id.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	private static final DateTimeFormatter TIMESTAMP_FORMAT=DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm");

	//endregion

	//region Drawing

	private void drawAvatar(Canvas canvas, Drawable avatar){
		if(avatar==null)
			return;
		// Software rendering ignores ViewOutlineProvider clipping, so round the
		// avatar the same way the timeline does (OutlineProviders.roundedRect(10))
		// with an explicit clip.
		Path clip=new Path();
		// The canvas is a fixed 1080 px regardless of device density, so the
		// clip radius must be a literal pixel value too: 10 dp on a 40 dp
		// timeline avatar is 25% of the size, hence AVATAR_SIZE*10/40 (24 px).
		float radius=AVATAR_SIZE*10f/40f;
		clip.addRoundRect(new RectF(SIDE_PADDING, SIDE_PADDING, SIDE_PADDING+AVATAR_SIZE, SIDE_PADDING+AVATAR_SIZE), radius, radius, Path.Direction.CW);
		canvas.save();
		canvas.clipPath(clip);
		Bitmap bmp=drawableToBitmap(avatar, AVATAR_SIZE, AVATAR_SIZE);
		canvas.drawBitmap(bmp, null, new Rect(SIDE_PADDING, SIDE_PADDING, SIDE_PADDING+AVATAR_SIZE, SIDE_PADDING+AVATAR_SIZE), null);
		canvas.restore();
	}

	private void drawMedia(Canvas canvas, List<Attachment> attachments, MediaPrep media){
		List<MediaGridSolver.Tile> tiles=MediaGridSolver.solve(attachments.size(), firstAspect(attachments));
		Paint labelPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		labelPaint.setTypeface(familyTypeface(400, null));
		for(int i=0;i<tiles.size();i++){
			MediaGridSolver.Tile tile=tiles.get(i);
			int left=SIDE_PADDING+tile.x, top=mediaTopInCard+tile.y;
			RectF tileRect=new RectF(left, top, left+tile.width, top+tile.height);

			Path clip=new Path();
			clip.addRoundRect(tileRect, MEDIA_RADIUS, MEDIA_RADIUS, Path.Direction.CW);
			canvas.save();
			canvas.clipPath(clip);

			Drawable d=media.drawables[i];
			boolean blurred=media.blurred[i];
			if(d!=null){
				Bitmap tileBmp=drawableToBitmap(d, tile.width, tile.height);
				if(blurred){
					// The unblurred bitmap never touches the PNG: blur into a temp
					// tile, then draw the blurred result.
					Bitmap blurredBmp=boxBlur(tileBmp, blurRadiusFor(tile.width));
					canvas.drawBitmap(blurredBmp, null, tileRect, null);
				}else{
					canvas.drawBitmap(tileBmp, null, tileRect, null);
				}
			}else{
				canvas.drawColor(palette.surfaceVariant);
			}
			canvas.restore();

			if(d==null){
				// Neutral placeholder: description or a type label, never a blank hole.
				drawCenteredLabel(canvas, tileRect, placeholderLabel(attachments.get(i)), LABEL_SIZE, palette.onSurfaceVariant, 0, labelPaint);
			}else if(blurred){
				drawCenteredLabel(canvas, tileRect, "Sensitive media", LABEL_SIZE, 0xFFFFFFFF, 0x8C000000, labelPaint);
			}

			if(tile.moreBadge){
				// When the tile centre is already taken by the "Sensitive media"
				// pill or the placeholder label, pin the "+N" badge to the inset
				// top-right corner so the two never stack.
				String badge="+"+(attachments.size()-MediaGridSolver.MAX_TILES);
				if(d==null || blurred)
					drawCornerLabel(canvas, tileRect, badge, LABEL_SIZE, 0xFFFFFFFF, 0x99000000, labelPaint);
				else
					drawCenteredLabel(canvas, tileRect, badge, LABEL_SIZE, 0xFFFFFFFF, 0x99000000, labelPaint);
			}
		}
	}

	private static String placeholderLabel(Attachment att){
		if(!TextUtils.isEmpty(att.description))
			return att.description;
		return switch(att.type){
			case VIDEO -> "Video";
			case AUDIO -> "Audio";
			case GIFV -> "GIF";
			default -> "Media";
		};
	}

	/**
	 * §11: draw the quoted status's first media attachment into its 160 × 160
	 * tile space. Sensitive quoted media is blurred with the same §5 machinery
	 * as the main grid; the unblurred bitmap never enters the PNG.
	 */
	private void drawQuoteTile(Canvas canvas){
		// The tile Space lives inside the quote container, so both offsets are
		// parent-relative and must be composed (same coordinate class as M1).
		int left=quoteContainer.getLeft()+quoteTileSpace.getLeft();
		int top=quoteContainer.getTop()+quoteTileSpace.getTop();
		RectF tileRect=new RectF(left, top, left+QuoteBlock.MEDIA_TILE, top+QuoteBlock.MEDIA_TILE);

		Path clip=new Path();
		clip.addRoundRect(tileRect, MEDIA_RADIUS, MEDIA_RADIUS, Path.Direction.CW);
		canvas.save();
		canvas.clipPath(clip);

		Drawable d=quoteTileDrawable;
		if(d!=null){
			Bitmap tileBmp=drawableToBitmap(d, QuoteBlock.MEDIA_TILE, QuoteBlock.MEDIA_TILE);
			if(quoteTileBlurred){
				Bitmap blurredBmp=boxBlur(tileBmp, blurRadiusFor(QuoteBlock.MEDIA_TILE));
				canvas.drawBitmap(blurredBmp, null, tileRect, null);
			}else{
				canvas.drawBitmap(tileBmp, null, tileRect, null);
			}
		}else{
			canvas.drawColor(palette.surfaceVariant);
		}
		canvas.restore();

		Paint labelPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		labelPaint.setTypeface(familyTypeface(400, null));
		if(d==null){
			// Neutral placeholder with a type label (the description would not
			// fit legibly on a 160 px tile).
			drawCenteredLabel(canvas, tileRect, quotePlaceholderLabel(), QUOTE_TILE_LABEL_SIZE, palette.onSurfaceVariant, 0, labelPaint);
		}else if(quoteTileBlurred){
			drawCenteredLabel(canvas, tileRect, "Sensitive media", QUOTE_TILE_LABEL_SIZE, 0xFFFFFFFF, 0x8C000000, labelPaint);
		}
	}

	private String quotePlaceholderLabel(){
		Attachment att=quoted.mediaAttachments.get(0);
		return switch(att.type){
			case VIDEO -> "Video";
			case AUDIO -> "Audio";
			case GIFV -> "GIF";
			default -> "Media";
		};
	}

	/**
	 * §11: draw the poll block into its Space. Bars go down first so they sit
	 * behind the option labels; the percentage text is drawn from the same
	 * rounded value that sizes the bar, per the spec. The bar geometry comes
	 * from {@link PollBlock#bars(int[], boolean[], int)}: the empty track is a
	 * full-width rounded rect in {@code outlineVariant} at low alpha — never
	 * {@code surfaceVariant}, which is the non-leader fill colour, so a
	 * non-leader bar can never read as 100% (review M1). The leader fill is
	 * colorM3Primary at 35% alpha; every other fill is colorM3SurfaceVariant.
	 */
	private void drawPoll(Canvas canvas){
		canvas.save();
		canvas.translate(pollSpace.getLeft(), pollTopInCard);
		int y=0;
		if(pollQuestion!=null){
			canvas.save();
			canvas.translate(0, y);
			pollQuestion.draw(canvas);
			canvas.restore();
			y+=pollQuestion.getHeight()+POLL_QUESTION_GAP;
		}

		PollBlock.Bar[] bars=PollBlock.bars(pollPcts, pollLeaders, CONTENT_WIDTH);
		Paint trackPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		trackPaint.setColor((palette.outlineVariant&0xFFFFFF)|(TRACK_ALPHA<<24));
		Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		Paint pctPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
		pctPaint.setTextSize(POLL_OPTION_SIZE);
		pctPaint.setTypeface(familyTypeface(400, null));
		pctPaint.setColor(palette.onSurfaceVariant);
		pctPaint.setTextAlign(Paint.Align.RIGHT);
		int barRadius=PollBlock.BAR_HEIGHT/2;
		for(int i=0;i<pollOptionLabels.length;i++){
			int rowHeight=Math.max(pollOptionHeights[i], PollBlock.BAR_HEIGHT);
			int barTop=y+(rowHeight-PollBlock.BAR_HEIGHT)/2;
			// The track is always the empty-bar colour at low alpha, so any
			// fill drawn over it — including a 0% option's absence of fill —
			// stays visible against it.
			canvas.drawRoundRect(new RectF(0, barTop, CONTENT_WIDTH, barTop+PollBlock.BAR_HEIGHT), barRadius, barRadius, trackPaint);
			if(bars[i].fillWidth>0){
				fillPaint.setColor(bars[i].leader ? (palette.primary&0xFFFFFF)|0x59000000 : palette.surfaceVariant);
				canvas.drawRoundRect(new RectF(0, barTop, bars[i].fillWidth, barTop+PollBlock.BAR_HEIGHT), barRadius, barRadius, fillPaint);
			}
			// Same rounded percentage as the bar, right-aligned in its column.
			canvas.drawText(pollPcts[i]+"%", CONTENT_WIDTH, y+pollOptionLabels[i].getBaseline(), pctPaint);
			canvas.save();
			canvas.translate(0, y);
			pollOptionLabels[i].draw(canvas);
			canvas.restore();
			y+=rowHeight;
			if(i<pollOptionLabels.length-1)
				y+=POLL_OPTION_GAP;
		}

		y+=POLL_FOOTER_GAP;
		canvas.save();
		canvas.translate(0, y);
		pollFooter.draw(canvas);
		canvas.restore();
		canvas.restore();
	}

	private void drawCenteredLabel(Canvas canvas, RectF tileRect, String text, int sizePx, int textColor, int pillColor, Paint paint){
		drawLabel(canvas, tileRect, text, sizePx, textColor, pillColor, paint, false);
	}

	private void drawCornerLabel(Canvas canvas, RectF tileRect, String text, int sizePx, int textColor, int pillColor, Paint paint){
		drawLabel(canvas, tileRect, text, sizePx, textColor, pillColor, paint, true);
	}

	/**
	 * Draw a labelled pill on a tile. Centred labels sit on the tile centre;
	 * corner labels pin the pill's top-right to a 16 px inset from the tile's
	 * top-right corner (same pill geometry, right-aligned text).
	 */
	private void drawLabel(Canvas canvas, RectF tileRect, String text, int sizePx, int textColor, int pillColor, Paint paint, boolean topRight){
		paint.setTextSize(sizePx);
		paint.setColor(textColor);
		paint.setTextAlign(topRight ? Paint.Align.RIGHT : Paint.Align.CENTER);
		Paint.FontMetrics fm=paint.getFontMetrics();
		float textHeight=fm.descent-fm.ascent;
		float pillPadX=sizePx*0.7f, pillPadY=sizePx*0.35f;
		float cx=topRight ? tileRect.right-BADGE_INSET : tileRect.centerX();
		float cy=topRight ? tileRect.top+BADGE_INSET+pillPadY+textHeight/2 : tileRect.centerY();
		float halfWidth=paint.measureText(text)/2;
		// With right alignment the text ends at cx, so its block is centred at
		// cx-halfWidth; the pill hugs the block either way.
		float blockCenterX=topRight ? cx-halfWidth : cx;
		if(pillColor!=0){
			RectF pill=new RectF(blockCenterX-halfWidth-pillPadX, cy-textHeight/2-pillPadY,
					blockCenterX+halfWidth+pillPadX, cy+textHeight/2+pillPadY);
			Paint pillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
			pillPaint.setColor(pillColor);
			canvas.drawRoundRect(pill, pill.height()/2, pill.height()/2, pillPaint);
		}
		canvas.drawText(text, cx, cy-(fm.ascent+fm.descent)/2, paint);
	}

	private static float firstAspect(List<Attachment> attachments){
		if(attachments.isEmpty())
			return 0;
		Attachment first=attachments.get(0);
		if(first.hasKnownDimensions())
			return (float)first.getWidth()/(float)first.getHeight();
		return 0;
	}

	private static int blurRadiusFor(int tileWidth){
		return Math.max(1, Math.round(TILE_BLUR_RADIUS*(tileWidth/(float)MediaGridSolver.TILE)));
	}

	private Bitmap drawableToBitmap(Drawable drawable, int width, int height){
		Bitmap src=null;
		if(drawable instanceof BitmapDrawable bd)
			src=bd.getBitmap();
		if(src!=null)
			return centerCrop(toSoftware(src), width, height);
		Bitmap bmp=Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas=new Canvas(bmp);
		drawable.setBounds(0, 0, width, height);
		drawable.draw(canvas);
		return bmp;
	}

	/**
	 * The image loader hands back {@link Bitmap.Config#HARDWARE} bitmaps on Android 9+, which
	 * is correct for on-screen drawing and illegal on the software canvas this class renders
	 * into ({@code Software rendering doesn't support hardware bitmaps}). Every bitmap that
	 * reaches a canvas here has to be a software bitmap first.
	 */
	private static Bitmap toSoftware(Bitmap src){
		if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
			return src; // hardware bitmaps do not exist before API 26
		if(src.getConfig()!=Bitmap.Config.HARDWARE)
			return src;
		Bitmap copy=src.copy(Bitmap.Config.ARGB_8888, false);
		return copy!=null ? copy : src;
	}

	private static Bitmap centerCrop(Bitmap src, int width, int height){
		if(src.getWidth()==width && src.getHeight()==height)
			return src;
		float scale=Math.max(width/(float)src.getWidth(), height/(float)src.getHeight());
		int scaledW=Math.max(width, Math.round(src.getWidth()*scale));
		int scaledH=Math.max(height, Math.round(src.getHeight()*scale));
		Bitmap scaled=Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
		int x=Math.max(0, (scaledW-width)/2), y=Math.max(0, (scaledH-height)/2);
		return Bitmap.createBitmap(scaled, x, y, width, height);
	}

	/**
	 * Real separable box blur, three passes over a private copy, so the source
	 * bitmap (which may back a cache drawable) is never mutated.
	 */
	private static Bitmap boxBlur(Bitmap src, int radius){
		src=toSoftware(src);
		if(radius<1 || src.getWidth()==0 || src.getHeight()==0)
			return src;
		Bitmap work=src.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap tmp=Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
		for(int pass=0;pass<3;pass++){
			boxBlurPass(work, tmp, radius, true);
			boxBlurPass(tmp, work, radius, false);
		}
		return work;
	}

	private static void boxBlurPass(Bitmap src, Bitmap dst, int radius, boolean horizontal){
		int w=src.getWidth(), h=src.getHeight();
		int[] in=new int[w*h];
		src.getPixels(in, 0, w, 0, 0, w, h);
		int[] out=new int[w*h];
		int span=horizontal ? w : h;
		int lines=horizontal ? h : w;
		int diameter=radius*2+1;
		for(int line=0;line<lines;line++){
			long sumA=0, sumR=0, sumG=0, sumB=0;
			for(int i=-radius;i<=radius;i++){
				int p=in[lineOffset(horizontal, line, clamp(i, 0, span-1), w)];
				sumA+=(p>>>24)&0xFF;
				sumR+=(p>>>16)&0xFF;
				sumG+=(p>>>8)&0xFF;
				sumB+=p&0xFF;
			}
			for(int i=0;i<span;i++){
				out[lineOffset(horizontal, line, i, w)]=(int)Math.min(255, sumA/diameter)<<24
						| (int)Math.min(255, sumR/diameter)<<16
						| (int)Math.min(255, sumG/diameter)<<8
						| (int)Math.min(255, sumB/diameter);
				int add=in[lineOffset(horizontal, line, clamp(i+radius+1, 0, span-1), w)];
				int remove=in[lineOffset(horizontal, line, clamp(i-radius, 0, span-1), w)];
				sumA+=((add>>>24)&0xFF)-((remove>>>24)&0xFF);
				sumR+=((add>>>16)&0xFF)-((remove>>>16)&0xFF);
				sumG+=((add>>>8)&0xFF)-((remove>>>8)&0xFF);
				sumB+=(add&0xFF)-(remove&0xFF);
			}
		}
		dst.setPixels(out, 0, w, 0, 0, w, h);
	}

	private static int lineOffset(boolean horizontal, int line, int pos, int rowWidth){
		// Horizontal pass walks rows; vertical pass walks columns but keeps the same
		// pixel indexing so both passes can write with a plain setPixels.
		return horizontal ? line*rowWidth+pos : pos*rowWidth+line;
	}

	private static int clamp(int v, int min, int max){
		return Math.max(min, Math.min(max, v));
	}

	//endregion
}
