package org.joinmastodon.android.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.BuildConfig;
import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.events.SelfUpdateStateChangedEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.Keep;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Keep
public class GithubSelfUpdaterImpl extends GithubSelfUpdater{
	private static final long CHECK_PERIOD=6*3600*1000L;
	private static final String TAG="GithubSelfUpdater";

	private volatile UpdateState state=UpdateState.NO_UPDATE;
	private volatile UpdateInfo info;
	private volatile Call activeDownloadCall;
	private volatile long downloadedBytes;
	private volatile long totalBytes;

	public GithubSelfUpdaterImpl(){
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
		String checkedByDate=prefs.getString("checkedByDate", "1970-01-00");
		String currentVersionDate=BuildConfig.VERSION_NAME.substring(BuildConfig.VERSION_NAME.lastIndexOf("@")+1);
		if(prefs.contains("version") && checkedByBuild==BuildConfig.VERSION_CODE && checkedByDate.equals(currentVersionDate)){
			String url=prefs.getString("apkURL", null);
			if(SelfUpdateVerifier.isAllowedDownloadUrl(url)){
				info=new UpdateInfo();
				info.version=prefs.getString("version", null);
				info.size=prefs.getLong("apkSize", 0);
				File apk=getUpdateApkFile();
				if(apk.isFile() && SelfUpdateVerifier.verifyApkFile(MastodonApp.context, apk, null, false, true)){
					state=UpdateState.DOWNLOADED;
				}else{
					deleteUpdateFiles();
					state=UpdateState.UPDATE_AVAILABLE;
				}
			}else{
				removeInfo();
			}
		}else if((checkedByBuild!=BuildConfig.VERSION_CODE && checkedByBuild>0) || !checkedByDate.equals(currentVersionDate)){
			deleteUpdateFiles();
			removeInfo();
		}
	}

	private SharedPreferences getPrefs(){
		return MastodonApp.context.getSharedPreferences("githubUpdater", Context.MODE_PRIVATE);
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
		long timeSinceLastCheck=System.currentTimeMillis()-getPrefs().getLong("lastCheck", CHECK_PERIOD);
		if(timeSinceLastCheck>=CHECK_PERIOD){
			setState(UpdateState.CHECKING);
			MastodonAPIController.runInBackground(this::actuallyCheckForUpdates);
		}
	}

	private void actuallyCheckForUpdates(){
		Request req=new Request.Builder()
				.url("https://api.github.com/repos/jcrabapple/mammut/releases")
				.header("Accept", "application/vnd.github+json")
				.build();
		Call call=MastodonAPIController.getHttpClient().newCall(req);
		try(Response resp=call.execute()){
			if(!resp.isSuccessful() || resp.body()==null)
				return;
			JsonArray arr=JsonParser.parseReader(resp.body().charStream()).getAsJsonArray();
			for(JsonElement jsonElement:arr){
				JsonObject obj=jsonElement.getAsJsonObject();
				if(obj.get("prerelease").getAsBoolean() && !GlobalUserPreferences.enablePreReleases)
					continue;

				String tag=obj.get("tag_name").getAsString();
				String changelog=obj.has("body") && !obj.get("body").isJsonNull() ? obj.get("body").getAsString() : "";

				if(!tag.substring(tag.indexOf("-")+1).equals(BuildConfig.VERSION_NAME.substring(BuildConfig.VERSION_NAME.lastIndexOf("@")+1))){
					String version=tag.substring(tag.indexOf("-")+1);
					for(JsonElement el:obj.getAsJsonArray("assets")){
						JsonObject asset=el.getAsJsonObject();
						String url=asset.get("browser_download_url").getAsString();
						if("application/vnd.android.package-archive".equals(asset.get("content_type").getAsString())
								&& "uploaded".equals(asset.get("state").getAsString())
								&& SelfUpdateVerifier.isAllowedDownloadUrl(url)){
							long size=asset.get("size").getAsLong();

							UpdateInfo newInfo=new UpdateInfo();
							newInfo.size=size;
							newInfo.version=version;
							this.info=newInfo;

							getPrefs().edit()
									.putLong("apkSize", size)
									.putString("version", version)
									.putString("apkURL", url)
									.putString("changelog", changelog)
									.putString("checkedByDate", BuildConfig.VERSION_NAME.substring(BuildConfig.VERSION_NAME.lastIndexOf("@")+1))
									.putInt("checkedByBuild", BuildConfig.VERSION_CODE)
									.remove("downloadID")
									.apply();
							break;
						}
					}
				}else{
					removeInfo();
					setState(UpdateState.NO_UPDATE);
				}
				getPrefs().edit().putLong("lastCheck", System.currentTimeMillis()).apply();
				break;
			}
		}catch(Exception x){
			Log.w(TAG, "actuallyCheckForUpdates", x);
		}finally{
			if(info!=null && !info.version.substring(info.version.indexOf("-")+1).equals(BuildConfig.VERSION_NAME.substring(BuildConfig.VERSION_NAME.lastIndexOf("@")+1))){
				setState(UpdateState.UPDATE_AVAILABLE);
			}else{
				removeInfo();
				setState(UpdateState.NO_UPDATE);
			}
		}
	}

	private void removeInfo(){
		getPrefs().edit()
				.remove("apkSize")
				.remove("version")
				.remove("apkURL")
				.remove("checkedByBuild")
				.remove("checkedByDate")
				.remove("downloadID")
				.remove("changelog")
				.apply();
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
					if(!SelfUpdateVerifier.isAllowedDownloadUrl(chain.request().url().toString()))
						throw new IOException("Disallowed update download host");
					return chain.proceed(chain.request());
				})
				.build();
	}

	@Override
	public void downloadUpdate(){
		if(state==UpdateState.DOWNLOADING)
			throw new IllegalStateException();
		String apkUrl=getPrefs().getString("apkURL", null);
		if(!SelfUpdateVerifier.isAllowedDownloadUrl(apkUrl)){
			setState(UpdateState.NO_UPDATE);
			return;
		}
		downloadedBytes=0;
		totalBytes=info!=null ? info.size : 0;
		setState(UpdateState.DOWNLOADING);
		MastodonAPIController.runInBackground(()->performVerifiedDownload(apkUrl));
	}

	private void performVerifiedDownload(String apkUrl){
		OkHttpClient client=downloadClient();
		File tmp=getTempApkFile();
		File target=getUpdateApkFile();
		boolean succeeded=false;
		try{
			Call call=client.newCall(new Request.Builder().url(apkUrl).build());
			activeDownloadCall=call;
			try(Response resp=call.execute()){
				if(!resp.isSuccessful() || resp.body()==null)
					return;
				ResponseBody body=resp.body();
				long len=body.contentLength();
				if(len>0)
					totalBytes=len;
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
			if(!SelfUpdateVerifier.verifyApkFile(MastodonApp.context, tmp, null, false, true))
				return;
			if(target.exists()){
				//noinspection ResultOfMethodCallIgnored
				target.delete();
			}
			succeeded=tmp.renameTo(target);
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

	@Override
	public void installUpdate(Activity activity){
		if(state!=UpdateState.DOWNLOADED)
			throw new IllegalStateException();
		File apk=getUpdateApkFile();
		if(!SelfUpdateVerifier.verifyApkFile(activity, apk, null, false, true)){
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
