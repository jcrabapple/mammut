package org.joinmastodon.android.drafts;

import java.util.ArrayList;
import java.util.List;

/**
 * MAMMUT: Composer 2.0 (§1) — the poll shape, matching the composer's existing
 * poll state (options, expiry in seconds, multiple, hide totals). hideTotals
 * has no composer control in slice 1; the field is carried for the model's
 * completeness so a draft written by a later version round-trips losslessly.
 */
public class PollDraft{
	public List<String> options=new ArrayList<>();
	public int expiresIn;
	public boolean multiple;
	public boolean hideTotals;
}
