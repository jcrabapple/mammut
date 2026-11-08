package org.joinmastodon.android.api;

import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okio.Okio;
import okio.Source;

public class ContentUriRequestBody extends CountingRequestBody{
	private final Uri uri;

	public ContentUriRequestBody(Uri uri, ProgressListener progressListener){
		super(progressListener);
		this.uri=uri;
		// MAMMUT: draft media restored from the store is a file:// URI into
		// app-private storage (no FileProvider — see ComposeMediaViewController
		// addRestoredAttachment); stat it directly instead of querying
		// OpenableColumns, which only content:// URIs answer.
		if("file".equals(uri.getScheme())){
			length=(int)Math.min(new File(uri.getPath()).length(), Integer.MAX_VALUE);
		}else{
			try(Cursor cursor=MastodonApp.context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)){
				cursor.moveToFirst();
				length=cursor.getInt(0);
			}
		}
	}

	@Override
	public MediaType contentType(){
		if("file".equals(uri.getScheme())){
			MediaType type=UiUtils.getFileMediaType(new File(uri.getPath()));
			return type!=null ? type : MediaType.get("application/octet-stream");
		}
		return MediaType.get(MastodonApp.context.getContentResolver().getType(uri));
	}

	@Override
	protected Source openSource() throws IOException{
		return Okio.source(MastodonApp.context.getContentResolver().openInputStream(uri));
	}
}
