package org.joinmastodon.android.drafts;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: Composer 2.0 (§1) — one post within a draft. A thread of three is a
 * ComposeDraft with three segments; a plain post is a draft of one. The poll
 * lives on the first segment only (§4).
 */
public class Segment{
	@SerializedName("text")
	public String text;
	/** Nullable: the segment's own content warning text */
	@SerializedName("spoiler_text")
	public String spoilerText;
	/** True when the author flagged media sensitivity without CW text */
	@SerializedName("sensitive")
	public boolean sensitive;
	@SerializedName("attachments")
	public List<DraftAttachment> attachments=new ArrayList<>();
	/** Nullable, first segment only */
	@SerializedName("poll")
	public PollDraft poll;
	/**
	 * MAMMUT: Composer 2.0 (§4) — the Idempotency-Key for this segment's
	 * CreateStatus call. Generated once, when the segment is first
	 * snapshotted into a draft, and persisted so a retry from the resumed
	 * draft reuses it instead of minting a fresh key (a fresh key can
	 * double-post the one failure §4 names).
	 */
	@SerializedName("idempotency_key")
	public String idempotencyKey;
}
