package org.joinmastodon.android.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import okio.Buffer;

/**
 * The zero-byte gate and the length contract behind t_54414ac0, asserted against real
 * files on a plain JVM. This is the pair of tests that would have caught the original
 * bug: the old {@code ContentUriRequestBody} took its length from
 * {@code OpenableColumns.SIZE} via {@code cursor.getInt(0)} — a NULL column read as 0 —
 * so the declared length (0) and the bytes {@code writeTo} would have streamed disagreed,
 * and nothing refused to build the body. Test 1 pins the gate that did not exist; test 3
 * pins contentLength()==bytes-written, which the old provider-size path violated.
 */
public class FileRequestBodyTest{
	@Test
	public void emptyFileRefusedBeforeAnyRequestCanBeBuilt() throws IOException{
		File empty=File.createTempFile("mammut-test-empty", ".bin");
		empty.deleteOnExit();
		// The gate: a zero-byte file must throw before a request can be built, not
		// ride to the server as an empty multipart part (servers 500 on it).
		assertThrows(EmptyUploadException.class, ()->new FileRequestBody(empty, null, null));
	}

	@Test
	public void missingFileThrowsPlainIOException() throws IOException{
		File missing=File.createTempFile("mammut-test-missing", ".bin");
		assertTrue(missing.delete());
		missing.deleteOnExit();
		// Not EmptyUploadException — a file that does not exist is a different failure
		// from one that exists but has no bytes.
		IOException thrown=assertThrows(IOException.class, ()->new FileRequestBody(missing, null, null));
		assertEquals(IOException.class, thrown.getClass());
	}

	@Test
	public void contentLengthEqualsFileLengthAndWriteToBytes() throws IOException{
		File file=File.createTempFile("mammut-test-bytes", ".bin");
		file.deleteOnExit();
		byte[] data=new byte[1234];
		new Random(20261108L).nextBytes(data);
		Files.write(file.toPath(), data);

		FileRequestBody body=new FileRequestBody(file, null, null);
		assertEquals(file.length(), body.contentLength());
		assertEquals(1234, body.contentLength());

		Buffer sink=new Buffer();
		body.writeTo(sink);
		assertEquals("writeTo must emit exactly contentLength() bytes", body.contentLength(), sink.size());
		assertArrayEquals("writeTo must emit the file's bytes", data, sink.readByteArray());

		// The body re-opens the file on every write so OkHttp-level retries send the
		// same bytes — a second write must produce the same result.
		Buffer retrySink=new Buffer();
		body.writeTo(retrySink);
		assertEquals(body.contentLength(), retrySink.size());
		assertArrayEquals(data, retrySink.readByteArray());
	}

	@Test
	public void verifyNonEmptyRejectsEmptyFile() throws IOException{
		File empty=File.createTempFile("mammut-test-verify-empty", ".bin");
		empty.deleteOnExit();
		assertThrows(EmptyUploadException.class, ()->UploadStaging.verifyNonEmpty(empty));
	}

	@Test
	public void verifyNonEmptyAcceptsNonEmptyFile() throws IOException{
		File file=File.createTempFile("mammut-test-verify-ok", ".bin");
		file.deleteOnExit();
		Files.write(file.toPath(), new byte[]{1, 2, 3});
		UploadStaging.verifyNonEmpty(file);
		assertEquals(3, file.length());
	}
}
