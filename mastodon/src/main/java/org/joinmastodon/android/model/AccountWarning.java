package org.joinmastodon.android.model;

import com.google.gson.annotations.SerializedName;

import org.joinmastodon.android.api.ObjectValidationException;
import org.joinmastodon.android.api.RequiredField;
import org.parceler.Parcel;

@Parcel
public class AccountWarning extends BaseModel{
	@RequiredField
	public String id;
	@RequiredField
	public Action action=Action.NONE;
	public String text;

	@Override
	public void postprocess() throws ObjectValidationException{
		if(action==null)
			action=Action.NONE;
		super.postprocess();
	}

	public enum Action{
		@SerializedName("none")
		NONE,
		@SerializedName("disable")
		DISABLE,
		@SerializedName("mark_statuses_as_sensitive")
		MARK_STATUSES_AS_SENSITIVE,
		@SerializedName("delete_statuses")
		DELETE_STATUSES,
		@SerializedName("sensitive")
		SENSITIVE,
		@SerializedName("silence")
		SILENCE,
		@SerializedName("suspend")
		SUSPEND
	}
}
