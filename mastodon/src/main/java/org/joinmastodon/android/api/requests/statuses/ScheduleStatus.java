package org.joinmastodon.android.api.requests.statuses;

import com.google.gson.reflect.TypeToken;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.ScheduledStatus;

/**
 * MAMMUT: Composer 2.0 (§5) — schedule a single-segment post. The request
 * body is the plain {@link CreateStatus.Request}: <code>scheduledAt</code>
 * already lives on that model (spec: wire it, add no parallel path). Only
 * the response type differs from {@link CreateStatus}, because Mastodon
 * answers a schedule with a ScheduledStatus, not a Status.
 */
public class ScheduleStatus extends MastodonAPIRequest<ScheduledStatus>{
	public ScheduleStatus(CreateStatus.Request req, String uuid){
		super(HttpMethod.POST, "/statuses", new TypeToken<>(){});
		setRequestBody(req);
		addHeader("Idempotency-Key", uuid);
	}
}
