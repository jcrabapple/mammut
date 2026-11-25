package org.joinmastodon.android.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import okhttp3.MediaType;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Request body for an image that may need resizing (1.3.5 fix wave, R2 F1/F2/F3/F10).
 *
 * <p>The body is always built over a verified local file. A {@code content://} source is
 * first copied into a verified cache temp ({@link UploadStaging}), so the declared length
 * comes from bytes on disk instead of a provider size column that is frequently NULL
 * (read as 0), and every {@link #writeTo} of the request lifetime — OkHttp transport
 * retries included — reads the same local bytes instead of re-opening a picker URI whose
 * read grant may have expired (F1). PNG output keeps its alpha, MIME and filename because
 * PNG is detected by type/subtype, never {@code String.equals(MediaType)} (F2).</p>
 *
 * <p>Temp files are deleted only by {@link #release()} — on terminal success, error,
 * cancel or constructor failure — never by the first write, which is what used to make
 * the first retry stream a file that was already gone (F3). Callers that own a request
 * lifecycle (e.g. {@code UploadAttachment}, {@code UpdateAccountCredentials}) wire
 * release to their done/cancel hooks; the finalizer is a safety net for a body that was
 * built but never sent.</p>
 */
public class ResizedImageRequestBody extends CountingRequestBody{
	private Uri uri;
	private File localFile;
	private File tempFile;
	private final UploadStaging.TempFiles tempFiles=new UploadStaging.TempFiles();
	private MediaType contentType;
	private int maxSize;
	private volatile boolean released;

	public ResizedImageRequestBody(Uri uri, int maxSize, ProgressListener progressListener) throws IOException{
		this(uri, maxSize, progressListener, null);
	}

	public ResizedImageRequestBody(Uri uri, int maxSize, ProgressListener progressListener, MediaType explicitMediaType) throws IOException{
		super(progressListener);
		this.uri=uri;
		this.maxSize=maxSize;
		boolean prepared=false;
		try{
			localFile=ensureLocalFile(uri);
			contentType=explicitMediaType!=null ? explicitMediaType : resolveContentType(uri, localFile);
			BitmapFactory.Options opts=new BitmapFactory.Options();
			opts.inJustDecodeBounds=true;
			BitmapFactory.decodeFile(localFile.getPath(), opts);
			if(opts.outWidth<=0 || opts.outHeight<=0)
				throw new IOException("Could not decode image bounds: "+uri);
			if(needResize(opts.outWidth, opts.outHeight) || needCrop(opts.outWidth, opts.outHeight)){
				Bitmap bitmap=decodeAndScale(localFile, opts);
				if(bitmap==null)
					throw new IOException("Could not decode image bitmap: "+uri);
				try{
					boolean isPNG=UploadSource.isPngMime(contentType.type()+"/"+contentType.subtype());
					tempFile=File.createTempFile("mastodon_tmp_resized", isPNG ? ".png" : ".jpg", MastodonApp.context.getCacheDir());
					tempFiles.add(tempFile);
					try(FileOutputStream out=new FileOutputStream(tempFile)){
						if(isPNG){
							// PNG keeps alpha, MIME and filename — no flattening to JPEG.
							bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
						}else{
							bitmap.compress(Bitmap.CompressFormat.JPEG, 97, out);
							contentType=MediaType.get("image/jpeg");
						}
						out.getFD().sync();
					}
					if(tempFile.length()==0)
						throw new IOException("Could not encode image: "+uri);
					length=tempFile.length();
				}finally{
					bitmap.recycle();
				}
			}else{
				// The on-disk length is a fact; a provider SIZE column (NULL cursor,
				// empty cursor, expired grant) is never consulted.
				length=localFile.length();
			}
			prepared=true;
		}catch(IOException e){
			throw e;
		}catch(RuntimeException e){
			throw new IOException("Could not prepare image for upload: "+uri, e);
		}finally{
			if(!prepared)
				tempFiles.releaseAll();
		}
	}

	/**
	 * F1: content URIs are copied once into a verified cache temp whose length is real
	 * and whose read grant cannot expire; file URIs are used in place. An empty or
	 * failed copy throws {@link EmptyUploadException} and leaves no artifact behind.
	 */
	private File ensureLocalFile(Uri uri) throws IOException{
		UploadSource source=UploadStaging.sourceFor(uri, resolverMime(uri));
		if(!source.requiresVerifiedCopy()){
			File file=new File(uri.getPath());
			if(!file.isFile())
				throw new IOException("Upload file does not exist: "+uri);
			return file;
		}
		File staged=UploadStaging.copyToCache(MastodonApp.context, uri, source);
		tempFiles.add(staged);
		return staged;
	}

	private String resolverMime(Uri uri){
		try{
			return MastodonApp.context.getContentResolver().getType(uri);
		}catch(RuntimeException e){
			return null;
		}
	}

	private MediaType resolveContentType(Uri uri, File localFile){
		if(!"file".equals(uri.getScheme())){
			String mime=resolverMime(uri);
			if(!TextUtils.isEmpty(mime)){
				try{
					return MediaType.get(mime);
				}catch(IllegalArgumentException ignored){}
			}
		}
		MediaType type=fileMediaType(localFile);
		return type!=null ? type : MediaType.get("image/jpeg");
	}

	private static MediaType fileMediaType(File file){
		try{
			return UiUtils.getFileMediaType(file);
		}catch(RuntimeException e){
			return null;
		}
	}

	private Bitmap decodeAndScale(File localFile, BitmapFactory.Options bounds) throws IOException{
		Bitmap bitmap;
		if(Build.VERSION.SDK_INT>=28){
			bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(localFile), (decoder, info, _source)->{
				int[] size=getTargetSize(info.getSize().getWidth(), info.getSize().getHeight());
				decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
				decoder.setTargetSize(size[0], size[1]);
				// Breaks images in mysterious ways
//				if(needCrop(size[0], size[1]))
//					decoder.setCrop(getCropBounds(size[0], size[1]));
			});
			if(needCrop(bitmap.getWidth(), bitmap.getHeight())){
				Rect crop=getCropBounds(bitmap.getWidth(), bitmap.getHeight());
				bitmap=Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height());
			}
		}else{
			int[] size=getTargetSize(bounds.outWidth, bounds.outHeight);
			int targetWidth=size[0];
			int targetHeight=size[1];
			float factor=bounds.outWidth/(float)targetWidth;
			BitmapFactory.Options opts=new BitmapFactory.Options();
			opts.inSampleSize=(int)factor;
			bitmap=BitmapFactory.decodeFile(localFile.getPath(), opts);
			if(bitmap==null)
				throw new IOException("Could not decode image file: "+localFile.getName());
			boolean needCrop=needCrop(targetWidth, targetHeight);
			if(factor%1f!=0f || needCrop){
				Rect srcBounds=null;
				Rect dstBounds;
				if(needCrop){
					Rect crop=getCropBounds(targetWidth, targetHeight);
					dstBounds=new Rect(0, 0, crop.width(), crop.height());
					srcBounds=new Rect(
							Math.round(crop.left/(float)targetWidth*bitmap.getWidth()),
							Math.round(crop.top/(float)targetHeight*bitmap.getHeight()),
							Math.round(crop.right/(float)targetWidth*bitmap.getWidth()),
							Math.round(crop.bottom/(float)targetHeight*bitmap.getHeight())
					);
				}else{
					dstBounds=new Rect(0, 0, targetWidth, targetHeight);
				}
				Bitmap scaled=Bitmap.createBitmap(dstBounds.width(), dstBounds.height(), Bitmap.Config.ARGB_8888);
				new Canvas(scaled).drawBitmap(bitmap, srcBounds, dstBounds, new Paint(Paint.FILTER_BITMAP_FLAG));
				bitmap=scaled;
			}
			// One shared transform for all 8 EXIF orientations (R2 F10).
			bitmap=UploadStaging.applyExifOrientation(bitmap, localFile);
		}
		return bitmap;
	}

	@Override
	protected Source openSource() throws IOException{
		// Every write re-opens the local bytes, so a transport retry emits the same
		// data — the source of truth never disappears mid-request (R2 F3).
		return Okio.source(tempFile!=null ? tempFile : localFile);
	}

	@Override
	public MediaType contentType(){
		return contentType;
	}

	@Override
	public void writeTo(BufferedSink sink) throws IOException{
		if(released)
			throw new IOException("Request body was released");
		super.writeTo(sink);
	}

	/**
	 * Terminal cleanup (R2 F3): deletes every temp this body created — the staged copy
	 * and/or the re-encoded image. Idempotent; after release the body refuses further
	 * writes. Callers wire this to request success/error/cancel; the finalizer covers a
	 * body that was built but never sent (cancel-before-write).
	 */
	public void release(){
		released=true;
		tempFiles.releaseAll();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void finalize() throws Throwable{
		try{
			release();
		}finally{
			super.finalize();
		}
	}

	protected int[] getTargetSize(int srcWidth, int srcHeight){
		int targetWidth=Math.round((float)Math.sqrt((float)maxSize*((float)srcWidth/srcHeight)));
		int targetHeight=Math.round((float)Math.sqrt((float)maxSize*((float)srcHeight/srcWidth)));
		return new int[]{targetWidth, targetHeight};
	}

	protected boolean needResize(int srcWidth, int srcHeight){
		return srcWidth*srcHeight>maxSize;
	}

	protected boolean needCrop(int srcWidth, int srcHeight){
		return false;
	}

	protected Rect getCropBounds(int srcWidth, int srcHeight){
		return null;
	}
}
