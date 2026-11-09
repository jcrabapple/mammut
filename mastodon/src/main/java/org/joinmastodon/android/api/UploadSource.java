package org.joinmastodon.android.api;

/**
 * Pure decision object for the media-upload pipeline (t_54414ac0).
 *
 * Encodes what the pipeline may trust about a to-be-uploaded file:
 * <ul>
 * <li>A {@code file://} length is a fact — it is stat'ed from disk and may gate
 * the upload (zero bytes means nothing readable).</li>
 * <li>A provider-reported {@code OpenableColumns.SIZE} for a {@code content://}
 * URI is a hint at best — the column is frequently NULL, and reading it through
 * {@code Cursor.getInt} turns NULL into 0, which once rode into the request as a
 * declared length of zero and the server received an empty file part. Content
 * sources are therefore always staged through a verified local copy, whatever
 * the provider claims.</li>
 * <li>HEIC/HEIF is transcoded to JPEG before upload so both the raw and resize
 * paths send the same kind of bytes, and instances without HEIC support can
 * still process the media.</li>
 * </ul>
 *
 * This class deliberately touches no Android types so the whole decision matrix
 * runs as a plain JVM unit test.
 */
public class UploadSource{
	public enum Kind{FILE, CONTENT}
	public enum Stage{USE_FILE, COPY_TO_CACHE}
	public enum Format{TRANSCODE_TO_JPEG, SEND_AS_IS, REJECT_EMPTY}

	/** Provider reported no size (NULL column) — treated as unknown, never as zero bytes. */
	public static final long SIZE_UNKNOWN=-1L;

	private final Kind kind;
	private final long size;
	private final String mime;
	private final String name;

	/**
	 * @param kind FILE or CONTENT (any non-{@code file} scheme is staged like content)
	 * @param size for FILE the real length on disk; for CONTENT the provider hint or {@link #SIZE_UNKNOWN}
	 * @param mime content type, lowercase-insensitive, may be null
	 * @param name display/file name, used only as a secondary HEIC signal, may be null
	 */
	public UploadSource(Kind kind, long size, String mime, String name){
		this.kind=kind;
		this.size=size;
		this.mime=mime==null ? null : mime.toLowerCase();
		this.name=name;
	}

	public Kind kind(){
		return kind;
	}

	public long size(){
		return size;
	}

	public String mime(){
		return mime;
	}

	public String name(){
		return name;
	}

	public boolean isImage(){
		return mime!=null && mime.startsWith("image/");
	}

	public static boolean isHeicMime(String mime){
		if(mime==null)
			return false;
		String lower=mime.toLowerCase();
		return lower.equals("image/heic") || lower.equals("image/heif");
	}

	/** HEIC by declared type, or — when the provider gave no useful type — by file extension. */
	public boolean isHeic(){
		if(isHeicMime(mime))
			return true;
		if(name==null)
			return false;
		String lower=name.toLowerCase();
		return lower.endsWith(".heic") || lower.endsWith(".heif");
	}

	/**
	 * A file on disk is a fact: zero bytes there means nothing readable, reject before
	 * any request is built. A provider's 0 is ambiguous (NULL columns surface as 0),
	 * so content sources are never rejected here — the post-copy verification decides.
	 */
	public boolean isEmptyFile(){
		return kind==Kind.FILE && size==0;
	}

	public Stage stage(){
		return kind==Kind.FILE ? Stage.USE_FILE : Stage.COPY_TO_CACHE;
	}

	public Format format(){
		if(isEmptyFile())
			return Format.REJECT_EMPTY;
		return isHeic() ? Format.TRANSCODE_TO_JPEG : Format.SEND_AS_IS;
	}
}
