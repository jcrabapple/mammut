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
			file=copyToCache(context, uri, suffixFor(source));
			ours=true;
		}else{
			file=new File(uri.getPath());
			ours=false;
			if(!file.isFile())
				throw new IOException("Upload file does not exist: "+uri);
		}
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
			verifyNonEmpty(jpg);
			return new StagedUpload(jpg, MediaType.parse("image/jpeg"), true, true);
		}
		return new StagedUpload(file, resolveMediaType(context, uri, source, file), false, ours);
	}

	/** Post-copy gate (t_54414ac0 req 2): a staged file with no bytes is a local read failure, never an upload. */
	public static void verifyNonEmpty(File file) throws EmptyUploadException{
		if(file==null || !file.isFile() || file.length()==0)
			throw new EmptyUploadException("Nothing readable to upload"+(file==null ? "" : ": "+file));
	}

	private static File copyToCache(Context context, Uri uri, String suffix) throws IOException{
		File file=File.createTempFile("mastodon_upload_", suffix, context.getCacheDir());
		boolean ok=false;
		try(InputStream in=context.getContentResolver().openInputStream(uri)){
			if(in==null)
				throw new IOException("ContentResolver returned no stream for "+uri);
			try(FileOutputStream out=new FileOutputStream(file)){
				byte[] buf=new byte[128*1024];
				int read;
				while((read=in.read(buf))>0)
					out.write(buf, 0, read);
				out.getFD().sync();
				ok=true;
			}
		}finally{
			if(!ok)
				file.delete();
		}
		return file;
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
				bitmap=applyExifRotation(bitmap, src);
		}
		if(bitmap==null)
			throw new IOException("Could not decode HEIC image: "+src);
		File jpg=File.createTempFile("mastodon_upload_", ".jpg", context.getCacheDir());
		boolean ok=false;
		try(FileOutputStream out=new FileOutputStream(jpg)){
			bitmap.compress(Bitmap.CompressFormat.JPEG, HEIC_JPEG_QUALITY, out);
			out.getFD().sync();
			ok=true;
		}finally{
			if(!ok)
				jpg.delete();
		}
		return jpg;
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

	private static Bitmap applyExifRotation(Bitmap bitmap, File src) throws IOException{
		ExifInterface exif=new ExifInterface(src.getPath());
		int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		int rotation=switch(orientation){
			case ExifInterface.ORIENTATION_ROTATE_90 -> 90;
			case ExifInterface.ORIENTATION_ROTATE_180 -> 180;
			case ExifInterface.ORIENTATION_ROTATE_270 -> 270;
			default -> 0;
		};
		if(rotation==0)
			return bitmap;
		Matrix matrix=new Matrix();
		matrix.setRotate(rotation);
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
	}

	private static MediaType resolveMediaType(Context context, Uri uri, UploadSource source, File file){
		String mime=source.mime();
		if(TextUtils.isEmpty(mime) && !"file".equals(uri.getScheme()))
			mime=context.getContentResolver().getType(uri);
		if(TextUtils.isEmpty(mime)){
			MediaType type=UiUtils.getFileMediaType(file);
			return type!=null ? type : MediaType.parse("application/octet-stream");
		}
		return MediaType.parse(mime);
	}
}
