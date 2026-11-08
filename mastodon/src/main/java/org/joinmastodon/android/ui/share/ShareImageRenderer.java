package org.joinmastodon.android.ui.share;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.text.CustomEmojiSpan;

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

/**
 * Renders one status into a 1080 px wide PNG card in {@code cacheDir/share/}, off
 * the UI thread. Geometry per the post-as-image spec §4–§6; the pure decisions
 * live in {@link MediaGridSolver}, {@link TruncationChooser} and
 * {@link SensitiveMedia}.
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
	private static final int AVATAR_RADIUS_DP=10; // OutlineProviders.roundedRect(10), as in the timeline
	private static final int AVATAR_GAP=24;
	private static final int NAME_SIZE=40;
	private static final int HANDLE_SIZE=30;
	private static final int TIMESTAMP_SIZE=26;
	private static final int HEADER_BODY_GAP=56;
	private static final int BODY_SIZE=38;
	private static final float BODY_LINE_SPACING=1.35f;
	private static final int BODY_MEDIA_GAP=40;
	private static final int MEDIA_RADIUS=24;
	private static final int CW_SIZE=32;
	private static final int ELLIPSIS_SIZE=38;
	private static final int CONTINUE_SIZE=30;
	private static final int FOOTER_RULE_GAP=40;
	private static final int FOOTER_SIZE=24;
	private static final int LABEL_SIZE=32;

	private static final int TILE_BLUR_RADIUS=32;
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
		generation.incrementAndGet();
		if(pendingFuture!=null)
			pendingFuture.cancel(false);
		final int gen=generation.get();
		pendingFuture=executor.submit(()->{
			if(gen!=generation.get())
				return; // superseded before we started
			try{
				File file=new ShareImageRenderer(appContext, status, parsedText, palette).render();
				if(gen==generation.get())
					callback.onSuccess(file);
			}catch(Throwable t){
				if(gen==generation.get())
					callback.onError(t);
			}
		});
	}

	private final Context appContext;
	private final Status status;
	private final SpannableStringBuilder parsedText;
	private final Palette palette;

	private LinearLayout card;
	private Space mediaSpace;
	private boolean bodyPresent, mediaPresent, cwPresent;
	private int mediaTopInCard;

	private ShareImageRenderer(Context appContext, Status status, SpannableStringBuilder parsedText, Palette palette){
		this.appContext=appContext.getApplicationContext();
		this.status=status;
		this.parsedText=parsedText;
		this.palette=palette;
	}

	private File render() throws Exception{
		File dir=cleanupAndGetShareDir();
		loadEmojiDrawables();
		Drawable avatar=loadAvatar();
		List<Attachment> attachments=status.mediaAttachments!=null ? status.mediaAttachments : List.of();
		MediaPrep media=prepareMedia(attachments);

		int totalHeight=measureAndLayout(attachments, media);
		if(totalHeight>MAX_HEIGHT)
			throw new IllegalStateException("Card measured "+totalHeight+" px, over the "+MAX_HEIGHT+" px cap");

		Bitmap bitmap=Bitmap.createBitmap(CARD_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
		Canvas canvas=new Canvas(bitmap);
		card.draw(canvas);
		drawAvatar(canvas, avatar);
		if(mediaPresent)
			drawMedia(canvas, attachments, media);

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
	private void loadEmojiDrawables() throws Exception{
		if(parsedText==null)
			return;
		CustomEmojiSpan[] spans=parsedText.getSpans(0, parsedText.length(), CustomEmojiSpan.class);
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
				requests.add(span.createImageLoaderRequest());
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

	private Drawable loadAvatar() throws Exception{
		Account account=status.account;
		String url=account.avatarStatic!=null ? account.avatarStatic : account.avatar;
		if(TextUtils.isEmpty(url))
			return null;
		return load(new UrlImageLoaderRequest(url, AVATAR_SIZE, AVATAR_SIZE));
	}

	private static class MediaPrep{
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
				drawables[i]=load(new UrlImageLoaderRequest(att.previewUrl, MediaGridSolver.TILE*2, MediaGridSolver.TILE*2));
			}catch(Exception x){
				Log.w(TAG, "Failed to load media "+att.previewUrl, x);
			}
		}
		return new MediaPrep(drawables, blurred);
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

		TextView body=null;
		if(bodyPresent || cwPresent)
			card.addView(vspace(HEADER_BODY_GAP));
		if(cwPresent){
			TextView cw=newTextView(CW_SIZE, palette.onSurface, Typeface.create("sans-serif-medium", Typeface.NORMAL));
			cw.setText("CW: "+status.spoilerText);
			card.addView(cw, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		if(bodyPresent){
			body=newBodyTextView();
			body.setText(bodyText);
			card.addView(body, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		if(mediaPresent){
			card.addView(vspace(BODY_MEDIA_GAP));
			mediaSpace=vspace(mediaHeight(attachments));
			card.addView(mediaSpace);
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
			mediaTopInCard=SIDE_PADDING+mediaSpace.getTop();
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
		TextView ellipsis=newTextView(ELLIPSIS_SIZE, palette.onSurface, Typeface.DEFAULT);
		ellipsis.setText("…");
		card.addView(ellipsis, bodyIndex+1);
		TextView continueLine=newTextView(CONTINUE_SIZE, palette.onSurfaceVariant, Typeface.DEFAULT);
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

		TextView name=newTextView(NAME_SIZE, palette.onSurface, Typeface.create("sans-serif-medium", Typeface.NORMAL));
		name.setText(displayName);
		names.addView(name, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView handle=newTextView(HANDLE_SIZE, palette.onSurfaceVariant, Typeface.DEFAULT);
		handle.setText(fullHandle(account));
		names.addView(handle, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView timestamp=newTextView(TIMESTAMP_SIZE, palette.onSurfaceVariant, Typeface.DEFAULT);
		timestamp.setText(status.createdAt.atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT));
		names.addView(timestamp, new LinearLayout.LayoutParams(namesWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

		header.addView(names);
		card.addView(header, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private void buildFooter(){
		LinearLayout footer=new LinearLayout(appContext);
		footer.setOrientation(LinearLayout.HORIZONTAL);

		TextView url=newTextView(FOOTER_SIZE, palette.onSurfaceVariant, Typeface.DEFAULT);
		url.setText(status.url);
		url.setSingleLine(true);
		url.setEllipsize(TextUtils.TruncateAt.END);
		footer.addView(url, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

		TextView wordmark=newTextView(FOOTER_SIZE, palette.onSurfaceVariant, Typeface.DEFAULT);
		wordmark.setText("Mammut");
		footer.addView(wordmark);

		card.addView(footer, new LinearLayout.LayoutParams(CONTENT_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private TextView newBodyTextView(){
		TextView tv=newTextView(BODY_SIZE, palette.onSurface, Typeface.DEFAULT);
		tv.setLineSpacing(0, BODY_LINE_SPACING);
		tv.setLinkTextColor(palette.primary);
		return tv;
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
		TextView probe=newTextView(textSizePx, palette.onSurface, Typeface.DEFAULT);
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
		float radius=AVATAR_RADIUS_DP*appContext.getResources().getDisplayMetrics().density;
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
				drawCenteredLabel(canvas, tileRect, "+"+(attachments.size()-MediaGridSolver.MAX_TILES), LABEL_SIZE, 0xFFFFFFFF, 0x99000000, labelPaint);
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

	private void drawCenteredLabel(Canvas canvas, RectF tileRect, String text, int sizePx, int textColor, int pillColor, Paint paint){
		paint.setTextSize(sizePx);
		paint.setColor(textColor);
		paint.setTextAlign(Paint.Align.CENTER);
		Paint.FontMetrics fm=paint.getFontMetrics();
		float textHeight=fm.descent-fm.ascent;
		if(pillColor!=0){
			float pillPadX=sizePx*0.7f, pillPadY=sizePx*0.35f;
			float halfWidth=paint.measureText(text)/2;
			RectF pill=new RectF(tileRect.centerX()-halfWidth-pillPadX, tileRect.centerY()-textHeight/2-pillPadY,
					tileRect.centerX()+halfWidth+pillPadX, tileRect.centerY()+textHeight/2+pillPadY);
			Paint pillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
			pillPaint.setColor(pillColor);
			canvas.drawRoundRect(pill, pill.height()/2, pill.height()/2, pillPaint);
		}
		canvas.drawText(text, tileRect.centerX(), tileRect.centerY()-(fm.ascent+fm.descent)/2, paint);
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
			return centerCrop(src, width, height);
		Bitmap bmp=Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas=new Canvas(bmp);
		drawable.setBounds(0, 0, width, height);
		drawable.draw(canvas);
		return bmp;
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
