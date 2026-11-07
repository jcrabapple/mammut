package org.joinmastodon.android.api.requests.accounts;

import org.joinmastodon.android.api.MastodonAPIRequest;

// G10: POST /api/v1/accounts/:id/remove_from_followers (Mastodon 3.5+)
public class RemoveFromFollowers extends MastodonAPIRequest<Object>{
	public RemoveFromFollowers(String id){
		super(HttpMethod.POST, "/accounts/"+id+"/remove_from_followers", Object.class);
		setRequestBody(new Object());
	}
}
