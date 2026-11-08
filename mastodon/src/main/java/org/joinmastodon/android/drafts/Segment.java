package org.joinmastodon.android.drafts;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: Composer 2.0 (§1) — one post within a draft. A thread of three is a
 * ComposeDraft with three segments; a plain post is a draft of one. The poll
 * lives on the first segment only (§4).
 */
public class Segment{
	public String text;
	/** Nullable: the segment's own content warning text */
	public String spoilerText;
	/** True when the author flagged media sensitivity without CW text */
	public boolean sensitive;
	public List<DraftAttachment> attachments=new ArrayList<>();
	/** Nullable, first segment only */
	public PollDraft poll;
}
