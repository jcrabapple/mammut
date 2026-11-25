package org.joinmastodon.android.drafts;

import com.google.gson.annotations.SerializedName;

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
	@SerializedName("id")
	public String id;
	/** Epoch millis, for ordering the drafts list */
	@SerializedName("updated_at")
	public long updatedAt;
	/** Nullable: the id of the post this draft replies to */
	@SerializedName("reply_to_status_id")
	public String replyToStatusId;
	/** Nullable: the id of the post this draft quotes */
	@SerializedName("quoted_status_id")
	public String quotedStatusId;
	/** Applies to the whole draft; per-segment visibility does not exist (§6) */
	@SerializedName("visibility")
	public StatusPrivacy visibility;
	/**
	 * MAMMUT (§6, audit R1 F6): the BCP-47 tag chosen with the language
	 * picker; null = never set. Restored on resume so a draft republishes
	 * in the language its author picked.
	 */
	@SerializedName("language")
	public String language;
	/**
	 * MAMMUT (§6, audit R1 F6): {@link org.joinmastodon.android.model.StatusQuotePolicy}
	 * name(); null = never set (the composer default applies).
	 */
	@SerializedName("quote_policy")
	public String quotePolicy;
	@SerializedName("segments")
	public List<Segment> segments=new ArrayList<>();
	/**
	 * MAMMUT (§4, audit R1 F2): the payload fingerprint each segment's
	 * persisted idempotency key was last bound to, parallel to
	 * {@link #segments}. A null/absent entry means "unknown": the first
	 * publish after a resume binds the key to the payload then, and an
	 * edited payload mints a fresh key — an idempotency key must never ride
	 * a request it was not minted for. Older drafts simply have no list.
	 */
	@SerializedName("segment_fingerprints")
	public List<String> segmentFingerprints=new ArrayList<>();
}
