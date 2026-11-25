package org.joinmastodon.android.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.MediaType;

/**
 * Executes an {@link UploadSource} plan against the filesystem (t_54414ac0).
 *
 * Content URIs are copied once into a cache temp file whose length is a fact,
 * not a provider hint; the copy is verified non-empty before it is handed back,
 * and the staged file (not the original URI) feeds every write and every retry,
 * so a temporary picker read grant expiring between attempts can no longer fail
 * a retry differently from the first try. HEIC/HEIF sources are additionally
 * decoded and re-encoded as baseline-quality-92 JPEG with orientation applied,
 * matching what the resize path already sends; the HDR gain-map auxiliary image
 * ({@code tmap} brand) is not carried over — the output is a plain 8-bit sRGB
 * JPEG, which every Mastodon instance can process.
 */
public class UploadStaging{
	public static final int HEIC_JPEG_QUALITY=92;

	private UploadStaging(){}

	public static class StagedUpload{
		public final File file;
		public final MediaType mediaType;
		/** True when {@link #file} is a JPEG we transcoded from HEIC/HEIF — callers should send a .jpg filename. */
		public final boolean transcodedFromHeic;
		/** True when this staging created {@link #file} as a cache temp — safe to delete once the attachment settles. */
		public final boolean deleteOnRelease;

		private StagedUpload(File file, MediaType mediaType, boolean transcodedFromHeic, boolean deleteOnRelease){
			this.file=file;
			this.mediaType=mediaType;
			this.transcodedFromHeic=transcodedFromHeic;
			this.deleteOnRelease=deleteOnRelease;
		}

		public long length(){
			return file.length();
		}
	}

	/** Builds the decision object for a URI. The provider size column is intentionally not consulted. */
	public static UploadSource sourceFor(Uri uri, String mime){
		return sourceFor(uri, mime, UiUtils.getFileName(uri));
	}

	/**
	 * Same as {@link #sourceFor(Uri, String)} but reuses a display name the caller already
	 * resolved, so the staging thread does not run a second {@code OpenableColumns} query.
	 */
	public static UploadSource sourceFor(Uri uri, String mime, String name){
		if("file".equals(uri.getScheme()))
			return new UploadSource(UploadSource.Kind.FILE, new File(uri.getPath()).length(), mime, name);
		return new UploadSource(UploadSource.Kind.CONTENT, UploadSource.SIZE_UNKNOWN, mime, name);
	}

	public static StagedUpload stage(Context context, Uri uri, UploadSource source, int maxImageSize) throws IOException{
		File file;
		boolean ours;
		if(source.stage()==UploadSource.Stage.COPY_TO_CACHE){
			file=copyToCache(context, uri, source);
			ours=true;
		}else{
			file=new File(uri.getPath());
			ours=false;
			if(!file.isFile())
				throw new IOException("Upload file does not exist: "+uri);
		}
		// R2 F11: once the copy exists, every failure path out of staging must take it
		// (and anything the transcode created) back down — a zero-byte or half-written
		// mastodon_upload_* temp must never survive as cache litter.
		try{
			verifyNonEmpty(file);
			if(source.format()==UploadSource.Format.TRANSCODE_TO_JPEG){
				File jpg;
				try{
					jpg=transcodeHeicToJpeg(context, file, maxImageSize);
				}finally{
					// The JPEG replaces the cache copy — including when the transcode throws,
					// otherwise a failed HEIC would leave its full-size duplicate behind.
					if(ours)
						file.delete();
				}
				try{
					verifyNonEmpty(jpg);
				}catch(IOException | RuntimeException e){
					jpg.delete();
					throw e;
				}
				return new StagedUpload(jpg, MediaType.parse("image/jpeg"), true, true);
			}
			return new StagedUpload(file, resolveMediaType(context, uri, source, file), false, ours);
		}catch(IOException | RuntimeException e){
			if(ours)
				file.delete();
			throw e;
		}
	}

	/** Post-copy gate (t_54414ac0 req 2): a staged file with no bytes is a local read failure, never an upload. */
	public static void verifyNonEmpty(File file) throws EmptyUploadException{
		if(file==null || !file.isFile() || file.length()==0)
			throw new EmptyUploadException("Nothing readable to upload"+(file==null ? "" : ": "+file));
	}

	public static File copyToCache(Context context, Uri uri, UploadSource source) throws IOException{
		try(InputStream in=context.getContentResolver().openInputStream(uri)){
			if(in==null)
				throw new IOException("ContentResolver returned no stream for "+uri);
			return copyToCache(in, context.getCacheDir(), suffixFor(source));
		}
	}

	/**
	 * Copies a stream into a {@code mastodon_upload_*} temp under {@code dir} and verifies
	 * the result non-empty before handing it back (R2 F1/F11). An empty or failed copy is
	 * deleted here, in the finally, so no artifact outlives a failed staging — the caller
	 * never sees a zero-byte temp. Stream-based so the decision is unit-testable on a JVM.
	 */
	public static File copyToCache(InputStream in, File dir, String suffix) throws IOException{
		File file=File.createTempFile("mastodon_upload_", suffix, dir);
		boolean ok=false;
		try{
			if(in==null)
				throw new IOException("No stream to stage");
			try(FileOutputStream out=new FileOutputStream(file)){
				byte[] buf=new byte[128*1024];
				int read;
				while((read=in.read(buf))>0)
					out.write(buf, 0, read);
				out.getFD().sync();
			}
			// ok is set only after the copy verified: an empty provider stream must
			// take the temp back down in the finally below.
			verifyNonEmpty(file);
			ok=true;
			return file;
		}finally{
			if(!ok)
				file.delete();
		}
	}

	/**
	 * The cache temp files one upload request body created, deleted exactly once on
	 * explicit release (R2 F3). A resized body stays readable for every {@code writeTo}
	 * of the request lifetime — OkHttp transport retries included — and is removed only
	 * at a terminal event (success, error, cancel, constructor failure), never on first
	 * write, which is what used to make the first retry stream a deleted file.
	 */
	public static class TempFiles{
		private final java.util.List<File> files=new java.util.ArrayList<>();

		public synchronized void add(File file){
			if(file!=null)
				files.add(file);
		}

		public synchronized boolean isEmpty(){
			return files.isEmpty();
		}

		public synchronized void releaseAll(){
			for(File file:files)
				file.delete();
			files.clear();
		}
	}

	private static String suffixFor(UploadSource source){
		String name=source.name();
		if(name!=null){
			int dot=name.lastIndexOf('.');
			if(dot>=0 && dot<name.length()-1){
				String ext=name.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
				if(ext.length()>=2 && ext.length()<=8)
					return ext;
			}
		}
		String mime=source.mime();
		if(mime!=null && mime.startsWith("image/")){
			String sub=mime.substring(mime.indexOf('/')+1);
			if(sub.matches("[a-z0-9]{2,8}"))
				return "."+sub;
		}
		return ".bin";
	}

	private static File transcodeHeicToJpeg(Context context, File src, int maxImageSize) throws IOException{
		// The output file is created up front and every failure after that point —
		// decode, encode, sync — takes it back down in the finally (R2 F11): a
		// zero-byte or half-written mastodon_upload_*.jpg must never leak.
		File jpg=File.createTempFile("mastodon_upload_", ".jpg", context.getCacheDir());
		boolean ok=false;
		try{
			Bitmap bitmap=decodeHeic(src, maxImageSize);
			if(bitmap==null)
				throw new IOException("Could not decode HEIC image: "+src);
			try(FileOutputStream out=new FileOutputStream(jpg)){
				if(!bitmap.compress(Bitmap.CompressFormat.JPEG, HEIC_JPEG_QUALITY, out))
					throw new IOException("Could not encode HEIC as JPEG: "+src);
				out.getFD().sync();
				ok=true;
			}
			return jpg;
		}finally{
			if(!ok)
				jpg.delete();
		}
	}

	private static Bitmap decodeHeic(File src, int maxImageSize) throws IOException{
		Bitmap bitmap;
		if(Build.VERSION.SDK_INT>=28){
			// ImageDecoder applies EXIF orientation itself — the P+ path of
			// ResizedImageRequestBody relies on the same behavior. The decode is
			// capped at the instance's image matrix limit (t_8ab12a49 S3): a
			// 24 Mpx HEIC would otherwise decode to a ~98 MB software bitmap here
			// and then be decoded AGAIN by ResizedImageRequestBody.
			bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(src), (decoder, info, _source)->{
				decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
				int[] target=targetSize(info.getSize().getWidth(), info.getSize().getHeight(), maxImageSize);
				if(target!=null)
					decoder.setTargetSize(target[0], target[1]);
			});
		}else{
			// HEIF decoding is only guaranteed from API 28; try the platform
			// decoder and fail locally if it cannot handle the file.
			BitmapFactory.Options opts=new BitmapFactory.Options();
			if(maxImageSize>0){
				opts.inJustDecodeBounds=true;
				BitmapFactory.decodeFile(src.getPath(), opts);
				if(opts.outWidth>0 && opts.outHeight>0 && (long)opts.outWidth*opts.outHeight>maxImageSize)
					opts.inSampleSize=(int)Math.sqrt((double)opts.outWidth*opts.outHeight/maxImageSize);
				opts.inJustDecodeBounds=false;
			}
			bitmap=BitmapFactory.decodeFile(src.getPath(), opts);
			if(bitmap!=null)
				bitmap=applyExifOrientation(bitmap, src);
		}
		return bitmap;
	}

	/**
	 * Area-preserving shrink to the instance's image matrix limit, mirroring
	 * {@link ResizedImageRequestBody#getTargetSize}. Null when the source already
	 * fits (or no limit was configured) — callers then skip {@code setTargetSize}.
	 */
	private static int[] targetSize(int srcWidth, int srcHeight, int maxImageSize){
		if(maxImageSize<=0 || (long)srcWidth*srcHeight<=maxImageSize)
			return null;
		int targetWidth=Math.max(1, Math.round((float)Math.sqrt(maxImageSize*((float)srcWidth/srcHeight))));
		int targetHeight=Math.max(1, Math.round((float)Math.sqrt(maxImageSize*((float)srcHeight/srcWidth))));
		return new int[]{targetWidth, targetHeight};
	}

	/**
	 * The one EXIF-orientation transform for the legacy (&lt; API 28) decode path (R2 F10):
	 * all 8 orientation values, not just the three rotations — a flipped photo (e.g.
	 * {@code ORIENTATION_TRANSPOSE} from a front camera) must not arrive mirrored.
	 * Shared by the HEIC transcode and {@link ResizedImageRequestBody}.
	 */
	public static Bitmap applyExifOrientation(Bitmap bitmap, File src) throws IOException{
		ExifInterface exif=new ExifInterface(src.getPath());
		int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		Matrix matrix=new Matrix();
		switch(orientation){
			case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90);
			case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180);
			case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270);
			case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1, 1);
			case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1, -1);
			case ExifInterface.ORIENTATION_TRANSPOSE -> {
				matrix.setRotate(90);
				matrix.postScale(-1, 1);
			}
			case ExifInterface.ORIENTATION_TRANSVERSE -> {
				matrix.setRotate(270);
				matrix.postScale(-1, 1);
			}
			default -> {
				return bitmap;
			}
		}
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
	}

	private static MediaType resolveMediaType(Context context, Uri uri, UploadSource source, File file){
		String mime=source.mime();
		if(TextUtils.isEmpty(mime) && !"file".equals(uri.getScheme()))
			mime=context.getContentResolver().getType(uri);
		if(TextUtils.isEmpty(mime)){
			try{
				MediaType type=UiUtils.getFileMediaType(file);
				if(type!=null)
					return type;
			}catch(RuntimeException ignored){}
			return MediaType.parse("application/octet-stream");
		}
		MediaType parsed=MediaType.parse(mime);
		return parsed!=null ? parsed : MediaType.parse("application/octet-stream");
	}
}
