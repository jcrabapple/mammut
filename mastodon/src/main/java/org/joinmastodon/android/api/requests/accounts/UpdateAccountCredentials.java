package org.joinmastodon.android.api.requests.accounts;

import android.net.Uri;

import org.joinmastodon.android.api.AvatarResizedImageRequestBody;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.api.ResizedImageRequestBody;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.AccountField;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class UpdateAccountCredentials extends MastodonAPIRequest<Account>{
	private String displayName, bio;
	private Uri avatar, cover;
	private File avatarFile, coverFile;
	private List<AccountField> fields;
	private Boolean discoverable, indexable;
	private final Object bodyLock=new Object();
	private ResizedImageRequestBody avatarBody, coverBody;

	public UpdateAccountCredentials(String displayName, String bio, Uri avatar, Uri cover, List<AccountField> fields){
		super(HttpMethod.PATCH, "/accounts/update_credentials", Account.class);
		this.displayName=displayName;
		this.bio=bio;
		this.avatar=avatar;
		this.cover=cover;
		this.fields=fields;
	}

	public UpdateAccountCredentials(String displayName, String bio, File avatar, File cover, List<AccountField> fields){
		super(HttpMethod.PATCH, "/accounts/update_credentials", Account.class);
		this.displayName=displayName;
		this.bio=bio;
		this.avatarFile=avatar;
		this.coverFile=cover;
		this.fields=fields;
	}

	public UpdateAccountCredentials setDiscoverableIndexable(boolean discoverable, boolean indexable){
		this.discoverable=discoverable;
		this.indexable=indexable;
		return this;
	}

	@Override
	public RequestBody getRequestBody() throws IOException{
		releaseRequestResources();
		MultipartBody.Builder bldr=new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("display_name", displayName)
				.addFormDataPart("note", bio);

		boolean ok=false;
		try{
			if(avatar!=null){
				ResizedImageRequestBody body=new AvatarResizedImageRequestBody(avatar, null);
				synchronized(bodyLock){ avatarBody=body; }
				bldr.addFormDataPart("avatar", UiUtils.getFileName(avatar), body);
			}else if(avatarFile!=null){
				ResizedImageRequestBody body=new AvatarResizedImageRequestBody(Uri.fromFile(avatarFile), null);
				synchronized(bodyLock){ avatarBody=body; }
				bldr.addFormDataPart("avatar", avatarFile.getName(), body);
			}
			if(cover!=null){
				ResizedImageRequestBody body=new ResizedImageRequestBody(cover, 1500*500, null);
				synchronized(bodyLock){ coverBody=body; }
				bldr.addFormDataPart("header", UiUtils.getFileName(cover), body);
			}else if(coverFile!=null){
				ResizedImageRequestBody body=new ResizedImageRequestBody(Uri.fromFile(coverFile), 1500*500, null);
				synchronized(bodyLock){ coverBody=body; }
				bldr.addFormDataPart("header", coverFile.getName(), body);
			}
			if(fields!=null){
				if(fields.isEmpty()){
					bldr.addFormDataPart("fields_attributes[0][name]", "").addFormDataPart("fields_attributes[0][value]", "");
				}else{
					int i=0;
					for(AccountField field:fields){
						bldr.addFormDataPart("fields_attributes["+i+"][name]", field.name).addFormDataPart("fields_attributes["+i+"][value]", field.value);
						i++;
					}
				}
			}
			if(discoverable!=null)
				bldr.addFormDataPart("discoverable", discoverable.toString());
			if(indexable!=null)
				bldr.addFormDataPart("indexable", indexable.toString());

			RequestBody built=bldr.build();
			ok=true;
			return built;
		}finally{
			if(!ok)
				releaseRequestResources();
		}
	}

	@Override
	protected void releaseRequestResources(){
		ResizedImageRequestBody a, c;
		synchronized(bodyLock){
			a=avatarBody;
			c=coverBody;
			avatarBody=null;
			coverBody=null;
		}
		if(a!=null)
			a.release();
		if(c!=null)
			c.release();
	}
}
