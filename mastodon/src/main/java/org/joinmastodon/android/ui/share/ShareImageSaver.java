package org.joinmastodon.android.ui.share;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Saves a rendered share card to the device (spec §11, "Save image").
 *
 * API 29+: a real gallery entry via {@code MediaStore.Images} under
 * {@code Pictures/Mammut}, inserted with {@code IS_PENDING} and published by
 * clearing it. API 23–28: the app's external files dir under {@code Pictures/},
 * which needs no runtime storage permission; the caller toasts the resulting
 * path instead of showing the success snackbar.
 */
public class ShareImageSaver{
	public static final String ALBUM_DIR="Mammut";

	/**
	 * API 29+. Creates a real gallery entry and returns once it is visible to
	 * other apps. Throws on failure; a half-created entry is removed.
	 */
	public static void saveToMediaStore(Context context, File png) throws IOException{
		ContentValues values=new ContentValues();
		values.put(MediaStore.Images.Media.DISPLAY_NAME, png.getName());
		values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
		values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES+File.separator+ALBUM_DIR);
		values.put(MediaStore.Images.Media.IS_PENDING, 1);
		ContentResolver resolver=context.getContentResolver();
		Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
		if(uri==null)
			throw new IOException("MediaStore insert returned null");
		boolean completed=false;
		try(OutputStream out=resolver.openOutputStream(uri)){
			if(out==null)
				throw new IOException("MediaStore returned no stream for "+uri);
			copy(png, out);
			out.flush();
			ContentValues done=new ContentValues();
			done.put(MediaStore.Images.Media.IS_PENDING, 0);
			if(resolver.update(uri, done, null, null)!=1)
				throw new IOException("Failed to publish "+uri);
			completed=true;
		}finally{
			if(!completed)
				resolver.delete(uri, null, null);
		}
	}

	/**
	 * API 23–28. Writes into {@code Android/data/<pkg>/files/Pictures/Mammut/};
	 * no runtime permission exists or is requested for that location.
	 *
	 * @return the file that was written, whose absolute path the caller toasts
	 */
	public static File saveToAppPictures(Context context, File png) throws IOException{
		File pictures=context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		if(pictures==null)
			throw new IOException("External files dir is not available");
		File dir=new File(pictures, ALBUM_DIR);
		if(!dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create "+dir);
		File dest=new File(dir, png.getName());
		try(OutputStream out=new FileOutputStream(dest)){
			copy(png, out);
		}
		return dest;
	}

	private static void copy(File src, OutputStream out) throws IOException{
		try(InputStream in=new FileInputStream(src)){
			byte[] buf=new byte[8192];
			int n;
			while((n=in.read(buf))>0)
				out.write(buf, 0, n);
		}
	}
}
