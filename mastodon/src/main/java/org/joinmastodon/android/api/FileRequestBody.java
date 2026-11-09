package org.joinmastodon.android.api;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okio.Okio;
import okio.Source;

/**
 * Request body over a verified file on disk (t_54414ac0 req 1, 2).
 * {@link #contentLength()} is {@code file.length()} and {@link #writeTo}
 * streams exactly those bytes, for every write — the body re-opens the file,
 * so OkHttp-level retries send the same bytes instead of re-reading a
 * possibly-expired content URI. The constructor refuses to build a body over
 * a zero-byte file, so a 0-byte multipart part can never be constructed.
 */
public class FileRequestBody extends CountingRequestBody{
	private final File file;
	private final MediaType contentType;

	public FileRequestBody(File file, MediaType contentType, ProgressListener progressListener) throws IOException{
		super(progressListener);
		if(file==null || !file.isFile())
			throw new IOException("Upload file does not exist");
		if(file.length()==0)
			throw new EmptyUploadException("Refusing to build an upload body over an empty file");
		this.file=file;
		this.contentType=contentType;
		length=file.length();
	}

	@Override
	public MediaType contentType(){
		return contentType;
	}

	@Override
	protected Source openSource() throws IOException{
		return Okio.source(file);
	}
}
