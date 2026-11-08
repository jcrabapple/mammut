package org.joinmastodon.android.drafts;

import org.joinmastodon.android.model.StatusPrivacy;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: Composer 2.0 (§1) — the durable local form of a post-in-progress.
 * A draft is a small document: one or more segments, each carrying its own
 * text, content warning, media and descriptions. Slice 1 ships single-segment
 * drafts, but the model is the multi-segment shape slices 2 (threads) and
 * 3 (scheduling) consume — nothing here assumes a single post.
 * <p>
 * Serialized to {@code draft.json} inside the draft's directory by
 * {@link DraftStore}; {@link #id} is the directory name.
 */
public class ComposeDraft{
	/** UUID, also the directory name under drafts/&lt;accountID&gt;/ */
	public String id;
	/** Epoch millis, for ordering the drafts list */
	public long updatedAt;
	/** Nullable: the id of the post this draft replies to */
	public String replyToStatusId;
	/** Applies to the whole draft; per-segment visibility does not exist (§6) */
	public StatusPrivacy visibility;
	public List<Segment> segments=new ArrayList<>();
}
