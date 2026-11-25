package org.joinmastodon.android.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.BuildConfig;
import org.joinmastodon.android.E;
import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.events.SelfUpdateStateChangedEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Keep;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Keep
public class GithubSelfUpdaterImpl extends GithubSelfUpdater{
	private static final long CHECK_PERIOD=24*3600*1000L;
	private static final int MAX_SHA256_BYTES=4096;
	private static final String TAG="GithubSelfUpdater";
	private static final String LATEST_RELEASE_URL="https://api.github.com/repos/jcrabapple/mammut/releases/latest";

	private volatile UpdateState state=UpdateState.NO_UPDATE;
	private volatile UpdateInfo info;
	private volatile Call activeDownloadCall;
	private volatile long downloadedBytes;
	private volatile long totalBytes;

	public GithubSelfUpdaterImpl(){
		// Clean up any legacy world-writable external-cache APK left by older builds.
		File extCache=MastodonApp.context.getExternalCacheDir();
		if(extCache!=null){
			File legacyApk=new File(extCache, "update.apk");
			if(legacyApk.exists()){
				//noinspection ResultOfMethodCallIgnored
				legacyApk.delete();
			}
		}

		SharedPreferences prefs=getPrefs();
		int checkedByBuild=prefs.getInt("checkedByBuild", 0);
		if(prefs.contains("version") && checkedByBuild==BuildConfig.VERSION_CODE){
			String url=prefs.getString("apkURL", null);
			String shaUrl=prefs.getString("sha256URL", null);
			if(SelfUpdateVerifier.isAllowedDownloadUrl(url) && SelfUpdateVerifier.isAllowedDownloadUrl(shaUrl)){
				info=new UpdateInfo();
				info.version=prefs.getString("version", null);
				info.size=prefs.getLong("apkSize", 0);
				File apk=getUpdateApkFile();
				String expectedSha=prefs.getString("apkSha256", null);
				if(apk.isFile() && expectedSha!=null && SelfUpdateVerifier.verifyApkFile(MastodonApp.context, apk, expectedSha, true, forceUpdate)){
					state=UpdateState.DOWNLOADED;
				}else{
					if(apk.exists()){
						//noinspection ResultOfMethodCallIgnored
						apk.delete();
					}
					state=UpdateState.UPDATE_AVAILABLE;
				}
			}else{
				clearPrefs();
			}
		}else if(checkedByBuild!=BuildConfig.VERSION_CODE && checkedByBuild>0){
			// We are in a new version, running for the first time after update. Gotta clean things up.
			deleteUpdateFiles();
			clearPrefs();
		}
	}

	private SharedPreferences getPrefs(){
		return MastodonApp.context.getSharedPreferences("githubUpdater", Context.MODE_PRIVATE);
	}

	private void clearPrefs(){
		getPrefs().edit()
				.remove("apkSize")
				.remove("version")
				.remove("apkURL")
				.remove("sha256URL")
				.remove("apkSha256")
				.remove("checkedByBuild")
				.remove("downloadID")
				.apply();
	}

	private void deleteUpdateFiles(){
		File apk=getUpdateApkFile();
		if(apk.exists()){
			//noinspection ResultOfMethodCallIgnored
			apk.delete();
		}
		File tmp=getTempApkFile();
		if(tmp.exists()){
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
		}
	}

	@Override
	public void maybeCheckForUpdates(){
		if(state!=UpdateState.NO_UPDATE && state!=UpdateState.UPDATE_AVAILABLE)
			return;
		long timeSinceLastCheck=System.currentTimeMillis()-getPrefs().getLong("lastCheck", 0);
		if(timeSinceLastCheck>CHECK_PERIOD || forceUpdate){
			setState(UpdateState.CHECKING);
			MastodonAPIController.runInBackground(this::actuallyCheckForUpdates);
		}
	}

	private void actuallyCheckForUpdates(){
		Request req=new Request.Builder()
				.url(LATEST_RELEASE_URL)
				.header("Accept", "application/vnd.github+json")
				.build();
		Call call=MastodonAPIController.getHttpClient().newCall(req);
		try(Response resp=call.execute()){
			if(!resp.isSuccessful() || resp.body()==null){
				Log.w(TAG, "actuallyCheckForUpdates: HTTP "+resp.code());
				return;
			}
			JsonElement root=JsonParser.parseReader(resp.body().charStream());
			if(!root.isJsonObject()){
				Log.w(TAG, "actuallyCheckForUpdates: unexpected JSON root");
				return;
			}
			JsonObject obj=root.getAsJsonObject();
			if(!obj.has("tag_name") || obj.get("tag_name").isJsonNull())
				return;
			String tag=obj.get("tag_name").getAsString();
			long newVersion=SelfUpdateVerifier.parseSemanticVersion(tag);
			if(newVersion<0){
				Log.w(TAG, "actuallyCheckForUpdates: release tag has wrong format: "+tag);
				return;
			}
			long curVersion=SelfUpdateVerifier.parseSemanticVersion(BuildConfig.VERSION_NAME);
			if(curVersion<0){
				Log.w(TAG, "actuallyCheckForUpdates: current version has wrong format: "+BuildConfig.VERSION_NAME);
				return;
			}
			if(newVersion>curVersion || forceUpdate){
				boolean forced=forceUpdate;
				forceUpdate=false;
				Pattern pattern=Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
				Matcher matcher=pattern.matcher(tag);
				String version=matcher.find()
						? matcher.group(1)+"."+matcher.group(2)+(matcher.group(3)!=null ? "."+matcher.group(3) : "")
						: tag;
				String apkName=null;
				String apkUrl=null;
				long apkSize=0;
				String sha256Url=null;
				if(obj.has("assets") && obj.get("assets").isJsonArray()){
					for(JsonElement el:obj.getAsJsonArray("assets")){
						if(!el.isJsonObject())
							continue;
						JsonObject asset=el.getAsJsonObject();
						String name=asset.has("name") && !asset.get("name").isJsonNull() ? asset.get("name").getAsString() : "";
						String contentType=asset.has("content_type") && !asset.get("content_type").isJsonNull() ? asset.get("content_type").getAsString() : "";
						String assetState=asset.has("state") && !asset.get("state").isJsonNull() ? asset.get("state").getAsString() : "";
						String downloadUrl=asset.has("browser_download_url") && !asset.get("browser_download_url").isJsonNull() ? asset.get("browser_download_url").getAsString() : null;
						if("application/vnd.android.package-archive".equals(contentType) && "uploaded".equals(assetState) && SelfUpdateVerifier.isAllowedDownloadUrl(downloadUrl)){
							apkName=name;
							apkUrl=downloadUrl;
							apkSize=asset.has("size") ? asset.get("size").getAsLong() : 0L;
							break;
						}
					}
					if(apkName!=null){
						String expectedShaAssetName=apkName+".sha256";
						for(JsonElement el:obj.getAsJsonArray("assets")){
							if(!el.isJsonObject())
								continue;
							JsonObject asset=el.getAsJsonObject();
							String name=asset.has("name") && !asset.get("name").isJsonNull() ? asset.get("name").getAsString() : "";
							String assetState=asset.has("state") && !asset.get("state").isJsonNull() ? asset.get("state").getAsString() : "";
							String downloadUrl=asset.has("browser_download_url") && !asset.get("browser_download_url").isJsonNull() ? asset.get("browser_download_url").getAsString() : null;
							if(expectedShaAssetName.equals(name) && "uploaded".equals(assetState) && SelfUpdateVerifier.isAllowedDownloadUrl(downloadUrl)){
								sha256Url=downloadUrl;
								break;
							}
						}
					}
				}
				if(apkUrl!=null && sha256Url!=null){
					if(BuildConfig.DEBUG)
						Log.d(TAG, "actuallyCheckForUpdates: new version: "+version+(forced ? " (forced)" : ""));
					UpdateInfo newInfo=new UpdateInfo();
					newInfo.size=apkSize;
					newInfo.version=version;
					this.info=newInfo;

					getPrefs().edit()
							.putLong("apkSize", apkSize)
							.putString("version", version)
							.putString("apkURL", apkUrl)
							.putString("sha256URL", sha256Url)
							.remove("apkSha256")
							.putInt("checkedByBuild", BuildConfig.VERSION_CODE)
							.remove("downloadID")
							.apply();
				}
			}
			getPrefs().edit().putLong("lastCheck", System.currentTimeMillis()).apply();
		}catch(Exception x){
			Log.w(TAG, "actuallyCheckForUpdates", x);
		}finally{
			setState(info==null ? UpdateState.NO_UPDATE : UpdateState.UPDATE_AVAILABLE);
		}
	}

	private void setState(UpdateState state){
		this.state=state;
		E.post(new SelfUpdateStateChangedEvent(state));
	}

	@Override
	public UpdateState getState(){
		return state;
	}

	@Override
	public UpdateInfo getUpdateInfo(){
		return info;
	}

	/** Private internal cache path — never world-writable external storage. */
	public File getUpdateApkFile(){
		File dir=new File(MastodonApp.context.getCacheDir(), "self_update");
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		return new File(dir, "update.apk");
	}

	private File getTempApkFile(){
		File dir=new File(MastodonApp.context.getCacheDir(), "self_update");
		//noinspection ResultOfMethodCallIgnored
		dir.mkdirs();
		return new File(dir, "update.apk.tmp");
	}

	private OkHttpClient downloadClient(){
		return MastodonAPIController.getHttpClient().newBuilder()
				.addNetworkInterceptor(chain->{
					String url=chain.request().url().toString();
					if(!SelfUpdateVerifier.isAllowedDownloadUrl(url))
						throw new IOException("Disallowed update download host: "+chain.request().url().host());
					return chain.proceed(chain.request());
				})
				.build();
	}

	@Override
	public void downloadUpdate(){
		if(state==UpdateState.DOWNLOADING)
			throw new IllegalStateException();
		String apkUrl=getPrefs().getString("apkURL", null);
		String shaUrl=getPrefs().getString("sha256URL", null);
		if(!SelfUpdateVerifier.isAllowedDownloadUrl(apkUrl) || !SelfUpdateVerifier.isAllowedDownloadUrl(shaUrl)){
			Log.w(TAG, "downloadUpdate: refusing unverified update URLs");
			setState(UpdateState.NO_UPDATE);
			return;
		}
		downloadedBytes=0;
		totalBytes=info!=null ? info.size : 0;
		setState(UpdateState.DOWNLOADING);
		MastodonAPIController.runInBackground(()->performVerifiedDownload(apkUrl, shaUrl));
	}

	private void performVerifiedDownload(String apkUrl, String shaUrl){
		OkHttpClient client=downloadClient();
		File tmp=getTempApkFile();
		File target=getUpdateApkFile();
		boolean succeeded=false;
		try{
			String expectedSha=fetchExpectedSha256(client, shaUrl);
			if(expectedSha==null){
				Log.w(TAG, "performVerifiedDownload: invalid or missing SHA-256 checksum asset");
				return;
			}

			Call call=client.newCall(new Request.Builder().url(apkUrl).build());
			activeDownloadCall=call;
			try(Response resp=call.execute()){
				if(!resp.isSuccessful() || resp.body()==null){
					Log.w(TAG, "performVerifiedDownload: APK HTTP "+resp.code());
					return;
				}
				ResponseBody body=resp.body();
				long contentLen=body.contentLength();
				if(contentLen>0)
					totalBytes=contentLen;
				try(InputStream in=body.byteStream(); FileOutputStream out=new FileOutputStream(tmp)){
					byte[] buf=new byte[16384];
					int read;
					long done=0;
					while((read=in.read(buf))>0){
						if(call.isCanceled())
							return;
						out.write(buf, 0, read);
						done+=read;
						downloadedBytes=done;
					}
					out.getFD().sync();
				}
			}finally{
				activeDownloadCall=null;
			}

			if(!SelfUpdateVerifier.verifyApkFile(MastodonApp.context, tmp, expectedSha, true, forceUpdate)){
				Log.w(TAG, "performVerifiedDownload: downloaded APK failed integrity/signature verification");
				return;
			}
			if(target.exists()){
				//noinspection ResultOfMethodCallIgnored
				target.delete();
			}
			if(!tmp.renameTo(target)){
				Log.w(TAG, "performVerifiedDownload: failed moving verified APK into place");
				return;
			}
			getPrefs().edit().putString("apkSha256", expectedSha).apply();
			succeeded=true;
		}catch(Exception x){
			Log.w(TAG, "performVerifiedDownload failed", x);
		}finally{
			activeDownloadCall=null;
			if(!succeeded && tmp.exists()){
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
			if(succeeded){
				setState(UpdateState.DOWNLOADED);
			}else if(state==UpdateState.DOWNLOADING){
				setState(UpdateState.UPDATE_AVAILABLE);
			}
		}
	}

	private String fetchExpectedSha256(OkHttpClient client, String shaUrl) throws IOException{
		Call call=client.newCall(new Request.Builder().url(shaUrl).build());
		activeDownloadCall=call;
		try(Response resp=call.execute()){
			if(!resp.isSuccessful() || resp.body()==null)
				return null;
			ByteArrayOutputStream out=new ByteArrayOutputStream();
			byte[] buf=new byte[1024];
			int total=0;
			int read;
			try(InputStream in=resp.body().byteStream()){
				while((read=in.read(buf))>0){
					total+=read;
					if(total>MAX_SHA256_BYTES)
						return null;
					out.write(buf, 0, read);
				}
			}
			return SelfUpdateVerifier.parseSha256File(out.toString(StandardCharsets.UTF_8.name()));
		}finally{
			activeDownloadCall=null;
		}
	}

	@Override
	public void installUpdate(Activity activity){
		if(state!=UpdateState.DOWNLOADED)
			throw new IllegalStateException();
		File apk=getUpdateApkFile();
		String expectedSha=getPrefs().getString("apkSha256", null);
		if(!SelfUpdateVerifier.verifyApkFile(activity, apk, expectedSha, true, forceUpdate)){
			deleteUpdateFiles();
			setState(UpdateState.UPDATE_AVAILABLE);
			Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show();
			return;
		}
		Uri uri=new Uri.Builder().scheme("content").authority(activity.getPackageName()+".self_update_provider").path("update.apk").build();
		Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
		intent.setDataAndType(uri, "application/vnd.android.package-archive");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		activity.startActivity(intent);
	}

	@Override
	public float getDownloadProgress(){
		if(state!=UpdateState.DOWNLOADING)
			throw new IllegalStateException();
		long total=totalBytes;
		return total>0 ? Math.min(1f, (float) downloadedBytes/total) : 0f;
	}

	@Override
	public void cancelDownload(){
		if(state!=UpdateState.DOWNLOADING)
			throw new IllegalStateException();
		Call call=activeDownloadCall;
		if(call!=null)
			call.cancel();
		activeDownloadCall=null;
		setState(UpdateState.UPDATE_AVAILABLE);
	}

	@Override
	public void handleIntentFromInstaller(Intent intent, Activity activity){
		if(intent==null)
			return;
		int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
		if(status!=PackageInstaller.STATUS_SUCCESS && status!=PackageInstaller.STATUS_PENDING_USER_ACTION){
			String msg=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
			if(msg!=null)
				Toast.makeText(activity, activity.getString(R.string.error)+":\n"+msg, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void reset(){
		Call call=activeDownloadCall;
		if(call!=null)
			call.cancel();
		activeDownloadCall=null;
		getPrefs().edit().clear().apply();
		deleteUpdateFiles();
		info=null;
		state=UpdateState.NO_UPDATE;
	}
}
