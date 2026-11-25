package org.joinmastodon.android.api.requests.notifications;

import android.net.Uri;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.Notification;

public class GetNotificationByID extends MastodonAPIRequest<Notification>{
	public GetNotificationByID(String id){
		super(HttpMethod.GET, "/notifications/"+sanitizeId(id), Notification.class);
	}

	/** Encodes a single path segment so untrusted push IDs cannot traverse API paths. */
	static String sanitizeId(String id){
		if(id==null || id.isEmpty() || id.contains("/") || id.contains("\\") || id.equals(".") || id.equals(".."))
			throw new IllegalArgumentException("Invalid notification id");
		return Uri.encode(id);
	}
}
