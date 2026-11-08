package org.joinmastodon.android.drafts;

/**
 * MAMMUT: Composer 2.0 (§1) — one piece of media inside a draft segment.
 * {@link #filePath} is always an app-private copy inside the draft directory
 * (§2: media is copied in, never referenced — a draft holding a content:// URI
 * would one day open to a deleted file, and the failure would be silent).
 */
public class DraftAttachment{
	/** Absolute path of the app-private copy, alongside draft.json */
	public String filePath;
	public String mimeType;
	/** Caption Track text; may be empty, never a metadata suggestion on restore */
	public String description;
	/** Focal point, "-1,-1" when unset */
	public String focus="-1,-1";
}
