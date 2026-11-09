package org.joinmastodon.android.api.requests.statuses;

import android.net.Uri;
import android.text.TextUtils;

import org.joinmastodon.android.api.FileRequestBody;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.api.ProgressListener;
import org.joinmastodon.android.api.ResizedImageRequestBody;
import org.joinmastodon.android.api.UploadStaging;
import org.joinmastodon.android.model.Attachment;

import java.io.IOException;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads one media attachment (t_54414ac0). Takes a {@link UploadStaging.StagedUpload}
 * rather than a URI: the caller stages the file once (verified copy, HEIC
 * transcode) and reuses it across retries, so a retried request neither
 * re-reads a possibly-expired content URI nor re-derives its length from a
 * provider column.
 */
public class UploadAttachment extends MastodonAPIRequest<Attachment>{
	private UploadStaging.StagedUpload staged;
	private ProgressListener progressListener;
	private int maxImageSize;
	private String description;
	private String fileName;

	public UploadAttachment(UploadStaging.StagedUpload staged, int maxImageSize, String description, String fileName){
		super(HttpMethod.POST, "/media", Attachment.class);
		this.staged=staged;
		this.maxImageSize=maxImageSize;
		this.description=description;
		this.fileName=fileName;
	}

	public UploadAttachment setProgressListener(ProgressListener progressListener){
		this.progressListener=progressListener;
		return this;
	}

	@Override
	protected String getPathPrefix(){
		return "/api/v2";
	}

	@Override
	public void validateAndPostprocessResponse(Attachment respObj, Response httpResponse) throws IOException{
		if(respObj.url==null)
			respObj.url="";
		super.validateAndPostprocessResponse(respObj, httpResponse);
	}

	@Override
	public RequestBody getRequestBody() throws IOException{
		RequestBody fileBody;
		if(maxImageSize>0 && staged.mediaType!=null && "image".equals(staged.mediaType.type()))
			fileBody=new ResizedImageRequestBody(Uri.fromFile(staged.file), maxImageSize, progressListener);
		else
			fileBody=new FileRequestBody(staged.file, staged.mediaType, progressListener);
		MultipartBody.Builder builder=new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("file", fileName, fileBody);
		if(!TextUtils.isEmpty(description))
			builder.addFormDataPart("description", description);
		return builder.build();
	}
}
