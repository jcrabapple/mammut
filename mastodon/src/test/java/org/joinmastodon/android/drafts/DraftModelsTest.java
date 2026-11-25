package org.joinmastodon.android.drafts;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import org.joinmastodon.android.model.StatusPrivacy;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression guards for Wave 1 draft persistence fixes:
 * 1) Every persisted draft model field carries an explicit @SerializedName matching
 *    LOWER_CASE_WITH_UNDERSCORES so R8 obfuscation cannot break saved drafts across releases.
 * 2) proguard-rules.pro explicitly keeps org.joinmastodon.android.drafts.**
 * 3) DraftStore does not rely on API 26+ java.nio.file.Files (minSdk 23).
 */
public class DraftModelsTest{

	@Test
	public void everyPersistedDraftFieldHasSerializedName(){
		Class<?>[] models={ComposeDraft.class, Segment.class, DraftAttachment.class, PollDraft.class};
		for(Class<?> cls:models){
			for(Field f:cls.getDeclaredFields()){
				if(Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.isSynthetic())
					continue;
				SerializedName ann=f.getAnnotation(SerializedName.class);
				assertNotNull(cls.getSimpleName()+"."+f.getName()+" must have @SerializedName", ann);
				assertFalse(ann.value().isEmpty());
			}
		}
	}

	@Test
	public void plainGsonMatchesSnakeCasePolicyForDraftRoundTrip(){
		ComposeDraft d=new ComposeDraft();
		d.id="draft-1";
		d.updatedAt=1732500000000L;
		d.replyToStatusId="reply-9";
		d.quotedStatusId="quote-7";
		d.visibility=StatusPrivacy.UNLISTED;
		d.language="en";
		d.quotePolicy="PUBLIC";
		d.segmentFingerprints=List.of("fp-0");

		Segment s=new Segment();
		s.text="Hello";
		s.spoilerText="CW";
		s.sensitive=true;
		s.idempotencyKey="key-1";

		DraftAttachment a=new DraftAttachment();
		a.filePath="/data/user/0/app.mammut.android/files/drafts/a/draft-1/media-0.jpg";
		a.mimeType="image/jpeg";
		a.description="Alt";
		a.focus="0.1,0.2";
		s.attachments.add(a);

		PollDraft p=new PollDraft();
		p.options=List.of("Yes", "No");
		p.expiresIn=3600;
		p.multiple=true;
		p.hideTotals=true;
		s.poll=p;

		d.segments.add(s);

		Gson plain=new Gson();
		Gson snake=new GsonBuilder()
				.disableHtmlEscaping()
				.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
				.create();

		String fromSnake=snake.toJson(d);
		String fromPlain=plain.toJson(d);
		assertEquals(fromSnake, fromPlain);

		ComposeDraft restored=plain.fromJson(fromSnake, ComposeDraft.class);
		assertEquals("draft-1", restored.id);
		assertEquals(1732500000000L, restored.updatedAt);
		assertEquals("reply-9", restored.replyToStatusId);
		assertEquals("quote-7", restored.quotedStatusId);
		assertEquals("PUBLIC", restored.quotePolicy);
		assertEquals(1, restored.segments.size());
		assertEquals("key-1", restored.segments.get(0).idempotencyKey);
		assertEquals("image/jpeg", restored.segments.get(0).attachments.get(0).mimeType);
		assertTrue(restored.segments.get(0).poll.hideTotals);
	}

	@Test
	public void proguardKeepsDraftsPackage() throws Exception{
		File proguard=findProjectFile("proguard-rules.pro");
		String content=new String(Files.readAllBytes(proguard.toPath()), StandardCharsets.UTF_8);
		assertTrue("proguard-rules.pro must keep org.joinmastodon.android.drafts.**",
				content.contains("-keep public class org.joinmastodon.android.drafts.**"));
	}

	@Test
	public void draftStoreDoesNotUseApi26NioFiles() throws Exception{
		File store=findProjectFile("src/main/java/org/joinmastodon/android/drafts/DraftStore.java");
		String content=new String(Files.readAllBytes(store.toPath()), StandardCharsets.UTF_8);
		assertFalse("DraftStore must not import java.nio.file (requires API 26 without NIO desugaring)",
				content.contains("import java.nio.file."));
		assertFalse("DraftStore must not call File.toPath() (API 26+)",
				content.contains(".toPath()"));
	}

	private static File findProjectFile(String relativePath){
		File direct=new File(relativePath);
		if(direct.exists())
			return direct;
		File underMastodon=new File("mastodon", relativePath);
		if(underMastodon.exists())
			return underMastodon;
		throw new AssertionError("Could not locate "+relativePath+" from "+new File(".").getAbsolutePath());
	}
}
