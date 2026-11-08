package org.joinmastodon.android.ui.share;

/**
 * Pure decision for sensitive media on a share card: whether a given tile must be
 * blurred before it enters the PNG. No Android types, so it is unit-testable on
 * the JVM.
 *
 * Rule (spec §5): a tile is blurred when the status is sensitive or the attachment
 * itself is sensitive. This build's {@code Attachment} model has no per-attachment
 * flag, so the renderer currently passes the status flag for every attachment;
 * the per-attachment input exists so the rule (and its tests) cover both cases.
 */
public class SensitiveMedia{

	/**
	 * @param statusSensitive {@code status.sensitive}
	 * @param attachmentSensitive per-attachment sensitive flag when the model has one
	 */
	public static boolean isBlurred(boolean statusSensitive, boolean attachmentSensitive){
		return statusSensitive || attachmentSensitive;
	}

	/**
	 * Aggregate rule: any sensitive attachment in the set means that attachment is
	 * blurred. Returns the per-attachment blur flags.
	 */
	public static boolean[] blurFlags(boolean statusSensitive, boolean[] attachmentSensitive){
		boolean[] flags=new boolean[attachmentSensitive.length];
		for(int i=0;i<attachmentSensitive.length;i++)
			flags[i]=isBlurred(statusSensitive, attachmentSensitive[i]);
		return flags;
	}

	/**
	 * True when at least one tile in the set will be blurred.
	 */
	public static boolean anyBlurred(boolean statusSensitive, boolean[] attachmentSensitive){
		for(boolean flag:blurFlags(statusSensitive, attachmentSensitive)){
			if(flag)
				return true;
		}
		return false;
	}
}
