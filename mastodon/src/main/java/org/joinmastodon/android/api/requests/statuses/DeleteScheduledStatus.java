package org.joinmastodon.android.api.requests.statuses;

import org.joinmastodon.android.api.ResultlessMastodonAPIRequest;

/**
 * MAMMUT: Composer 2.0 (§5) — cancel a scheduled post on the server. The
 * text is not recovered anywhere locally, and the confirmation says so.
 */
public class DeleteScheduledStatus extends ResultlessMastodonAPIRequest{
	public DeleteScheduledStatus(String id){
		super(HttpMethod.DELETE, "/scheduled_statuses/"+id);
	}
}
