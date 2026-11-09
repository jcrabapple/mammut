package org.joinmastodon.android.api;

import android.net.Uri;

import org.joinmastodon.android.MastodonApp;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okio.Okio;
import okio.Source;

/**
 * Request body for an avatar/cover-style upload straight from a URI (t_54414ac0).
 * The URI is staged through {@link UploadStaging} once at construction: content
 * sources are copied into a verified cache temp file whose length is a fact
 * rather than an {@code OpenableColumns.SIZE} hint (NULL size columns once rode
 * in as a declared length of zero and the server received an empty file part),
 * and a zero-byte file fails locally with {@link EmptyUploadException} before
 * any request is built.
 */
public class ContentUriRequestBody extends CountingRequestBody{
	private final File file;
	private final MediaType mediaType;

	public ContentUriRequestBody(Uri uri, ProgressListener progressListener) throws IOException{
		super(progressListener);
		String mime="file".equals(uri.getScheme()) ? null : MastodonApp.context.getContentResolver().getType(uri);
		UploadStaging.StagedUpload staged=UploadStaging.stage(MastodonApp.context, uri, UploadStaging.sourceFor(uri, mime));
		this.file=staged.file;
		this.mediaType=staged.mediaType;
		length=file.length();
	}

	@Override
	public MediaType contentType(){
		return mediaType;
	}

	@Override
	protected Source openSource() throws IOException{
		return Okio.source(file);
	}
}
