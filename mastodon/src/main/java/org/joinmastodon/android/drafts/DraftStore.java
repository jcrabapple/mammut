package org.joinmastodon.android.drafts;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.joinmastodon.android.api.MastodonAPIController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MAMMUT: Composer 2.0 (§2) — the single drafts store, shared by the composer
 * and the drafts screen (§6: no second implementation inside the composer).
 * Drafts live in {@code getFilesDir()/drafts/<accountID>/<draftId>/} with
 * {@code draft.json} and the copied-in media files; switching accounts never
 * sees another account's drafts.
 * <p>
 * The API is deliberately synchronous and boring: callers run it on a
 * background thread (MastodonAPIController.runInBackground). All such tasks
 * execute on one worker thread, so a save started before a delete is always
 * written before the delete runs.
 */
public class DraftStore{
	private static final String TAG="DraftStore";
	private static final String DRAFT_JSON="draft.json";
	private static final String MEDIA_PREFIX="media-";

	private final Context context;
	private final File accountRoot;

	public DraftStore(Context context, String accountID){
		this.context=context.getApplicationContext();
		this.accountRoot=new File(this.context.getFilesDir(), "drafts/"+accountID);
	}

	/** {@code drafts/<accountID>/<draftId>/} */
	public File draftDir(String draftId){
		return new File(accountRoot, draftId);
	}

	/**
	 * Copies a media file into the draft directory, naming it
	 * {@code media-<n>.<ext>} (§2: copy on add/edit, keep it alongside
	 * draft.json). Returns the absolute path of the private copy.
	 */
	public String importMedia(String draftId, Uri source, String mimeType) throws IOException{
		File dir=draftDir(draftId);
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		String ext="bin";
		if(mimeType!=null && mimeType.contains("/")){
			String sub=mimeType.substring(mimeType.indexOf('/')+1);
			if(sub.matches("[a-zA-Z0-9]+"))
				ext=sub;
		}
		File dest;
		int n=0;
		do{
			dest=new File(dir, MEDIA_PREFIX+n+"."+ext);
			n++;
		}while(dest.exists());
		try(InputStream in=context.getContentResolver().openInputStream(source)){
			if(in==null)
				throw new IOException("Unable to open "+source);
			try(FileOutputStream out=new FileOutputStream(dest)){
				byte[] buf=new byte[8192];
				int read;
				while((read=in.read(buf))>0)
					out.write(buf, 0, read);
			}
		}
		return dest.getAbsolutePath();
	}

	/**
	 * Writes draft.json atomically (temp file then rename) and removes media
	 * copies no longer referenced by the draft, so repeat saves of one draft
	 * do not accumulate orphans.
	 */
	public void save(ComposeDraft draft) throws IOException{
		File dir=draftDir(draft.id);
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		File tmp=new File(dir, DRAFT_JSON+".tmp");
		boolean moved=false;
		try{
			try(FileOutputStream fos=new FileOutputStream(tmp);
					Writer w=new OutputStreamWriter(fos, StandardCharsets.UTF_8)){
				MastodonAPIController.gsonWithoutDeserializer.toJson(draft, w);
				w.flush();
				fos.getFD().sync();
			}
			File target=new File(dir, DRAFT_JSON);
			// Same directory, so rename is atomic on POSIX; API 23-safe fallback avoids java.nio.file.Files (API 26+).
			if(!tmp.renameTo(target)){
				if(target.exists() && !target.delete())
					throw new IOException("Unable to replace "+target);
				if(!tmp.renameTo(target))
					throw new IOException("Unable to rename "+tmp+" to "+target);
			}
			moved=true;
		}finally{
			if(!moved && tmp.exists()){
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
		}
		deleteOrphanedMedia(dir, draft);
	}

	/** Returns the draft, or null if absent or unreadable. */
	public ComposeDraft load(String draftId){
		File f=new File(draftDir(draftId), DRAFT_JSON);
		if(!f.exists())
			return null;
		try(InputStream in=new FileInputStream(f);
				InputStreamReader r=new InputStreamReader(in, StandardCharsets.UTF_8)){
			return MastodonAPIController.gsonWithoutDeserializer.fromJson(r, ComposeDraft.class);
		}catch(Exception x){
			Log.w(TAG, "load: failed to read draft "+draftId, x);
			return null;
		}
	}

	/** All drafts for this account, newest edit first. */
	public List<ComposeDraft> list(){
		File[] dirs=accountRoot.listFiles(File::isDirectory);
		List<ComposeDraft> result=new ArrayList<>();
		if(dirs!=null){
			for(File dir:dirs){
				ComposeDraft d=load(dir.getName());
				if(d!=null)
					result.add(d);
			}
		}
		result.sort(Comparator.comparingLong((ComposeDraft d)->d.updatedAt).reversed());
		return result;
	}

	/** Removes the draft directory with everything inside it, media included. */
	public void delete(String draftId){
		deleteRecursively(draftDir(draftId));
	}

	private void deleteOrphanedMedia(File dir, ComposeDraft draft){
		List<String> referenced=new ArrayList<>();
		for(Segment seg:draft.segments){
			for(DraftAttachment att:seg.attachments){
				if(att.filePath!=null)
					referenced.add(att.filePath);
			}
		}
		File[] files=dir.listFiles();
		if(files==null)
			return;
		for(File f:files){
			String name=f.getName();
			if(name.startsWith(MEDIA_PREFIX) && !referenced.contains(f.getAbsolutePath())){
				//noinspection ResultOfMethodCallIgnored
				f.delete();
			}
		}
	}

	private void deleteRecursively(File f){
		if(f==null || !f.exists())
			return;
		File[] children=f.listFiles();
		if(children!=null){
			for(File child:children)
				deleteRecursively(child);
		}
		//noinspection ResultOfMethodCallIgnored
		f.delete();
	}
}
