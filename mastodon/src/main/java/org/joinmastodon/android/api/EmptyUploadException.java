package org.joinmastodon.android.api;

import java.io.IOException;

/**
 * Thrown before any upload request is built when the file to upload has no
 * usable bytes (t_54414ac0 req 2). A zero-byte body must never reach the
 * server: servers 500 on it, and the generic message makes a length bug look
 * like a format problem.
 */
public class EmptyUploadException extends IOException{
	public EmptyUploadException(String message){
		super(message);
	}
}
