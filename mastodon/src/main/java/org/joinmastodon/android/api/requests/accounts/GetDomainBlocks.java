package org.joinmastodon.android.api.requests.accounts;

import com.google.gson.reflect.TypeToken;

import org.joinmastodon.android.api.requests.HeaderPaginationRequest;

public class GetDomainBlocks extends HeaderPaginationRequest<String>{
	public GetDomainBlocks(String maxID, int limit){
		super(HttpMethod.GET, "/domain_blocks", new TypeToken<>(){});
		if(maxID!=null)
			addQueryParameter("max_id", maxID);
		if(limit>0)
			addQueryParameter("limit", limit+"");
	}
}
