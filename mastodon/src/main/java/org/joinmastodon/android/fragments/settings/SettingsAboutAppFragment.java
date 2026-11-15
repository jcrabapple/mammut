package org.joinmastodon.android.fragments.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.joinmastodon.android.BuildConfig;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.session.AccountLocalPreferences;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.ContentType;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.Snackbar;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;

import me.grishka.appkit.imageloader.ImageCache;
import me.grishka.appkit.imageloader.disklrucache.DiskLruCache;
import me.grishka.appkit.utils.MergeRecyclerAdapter;
import me.grishka.appkit.utils.SingleViewRecyclerAdapter;
import me.grishka.appkit.utils.V;

public class SettingsAboutAppFragment extends BaseSettingsFragment<Void>{
	private ListItem<Void> mediaCacheItem;

	// MOSHIDON
	private ListItem<Void> clearRecentEmojisItem, exportItem, importItem;
	private static final int IMPORT_RESULT=314;
	private static final int EXPORT_RESULT=271;
	private static final String TAG="SettingsAboutAppFragment";

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.about_app, getString(R.string.app_name)));
		AccountSession s=AccountSessionManager.get(accountID);
		onDataLoaded(List.of(
				new ListItem<>(R.string.settings_even_more, 0, i->UiUtils.launchWebBrowser(getActivity(), "https://"+s.domain+"/auth/edit")),
				new ListItem<>(R.string.settings_contribute, 0, i->UiUtils.launchWebBrowser(getActivity(), getString(R.string.github_url))),
				new ListItem<>(R.string.settings_tos, 0, i->UiUtils.launchWebBrowser(getActivity(), "https://"+s.domain+"/terms")),
				new ListItem<>(R.string.settings_privacy_policy, 0, i->UiUtils.launchWebBrowser(getActivity(), getString(R.string.privacy_policy_url)), 0, true),
				// MOSHIDON: export and import settings
				exportItem=new ListItem<>(R.string.export_settings_title, R.string.export_settings_summary, R.drawable.ic_fluent_arrow_export_24_filled, this::onExportClick, 0, false),
				importItem=new ListItem<>(R.string.import_settings_title, R.string.import_settings_summary, R.drawable.ic_fluent_arrow_import_24_filled, this::onImportClick, 0, true),

				mediaCacheItem=new ListItem<>(R.string.settings_clear_cache, 0, this::onClearMediaCacheClick)
		));

		updateMediaCacheItem();
	}

	@Override
	protected void doLoadData(int offset, int count){}

	@Override
	protected RecyclerView.Adapter<?> getAdapter(){
		MergeRecyclerAdapter adapter=new MergeRecyclerAdapter();
		adapter.addAdapter(super.getAdapter());

		TextView versionInfo=new TextView(getActivity());
		versionInfo.setSingleLine();
		versionInfo.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, V.dp(32)));
		versionInfo.setTextAppearance(R.style.m3_label_medium);
		versionInfo.setTextColor(UiUtils.getThemeColor(getActivity(), R.attr.colorM3Outline));
		versionInfo.setGravity(Gravity.CENTER);
		versionInfo.setText(getString(R.string.settings_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
		versionInfo.setOnClickListener(v->{
			getActivity().getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("", BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")"));
			if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.S_V2){
				new Snackbar.Builder(getActivity())
						.setText(R.string.app_version_copied)
						.show();
			}
		});
		adapter.addAdapter(new SingleViewRecyclerAdapter(versionInfo));

		return adapter;
	}

	// MOSHIDON:
	private void onExportClick(ListItem<?> item){
		// The magic will happen on the onActivityResult Method
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.setType("application/json");
		intent.putExtra(Intent.EXTRA_TITLE,"moshidon-exported-settings.json");
		startActivityForResult(intent, EXPORT_RESULT);
	}

	// MOSHIDON:
	private void onImportClick(ListItem<?> item){
		new M3AlertDialogBuilder(getContext())
				.setTitle(R.string.import_settings_confirm)
				.setIcon(R.drawable.ic_fluent_warning_24_regular)
				.setMessage(R.string.import_settings_confirm_body)
				.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent.setType("application/json");
					startActivityForResult(intent, IMPORT_RESULT);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	// MOSHIDON:
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		if(requestCode==IMPORT_RESULT && resultCode==Activity.RESULT_OK){
			Uri uri=data.getData();
			if(uri==null){
				return;
			}
			// MAMMUT: audit R4 F2/S1.3 — the import path was the app's worst crash vector:
			// any malformed JSON, wrong-typed value, or out-of-range ordinal either crashed
			// here or poisoned SharedPreferences so the next startup loop-crashed in load().
			// Everything now goes through the pure SettingsImportValidator against a versioned
			// schema allowlist; NOTHING is written until the whole document validates, and any
			// failure lands on the failure toast with prefs untouched.
			try(InputStream inputStream=getContext().getContentResolver().openInputStream(uri)){
				if(inputStream==null)
					throw new IOException("null input stream");
				String jsonString=readCapped(inputStream, SettingsSchema.MAX_IMPORT_BYTES);

				Gson gson=new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

				//check if json is not null
				if(jsonString.isEmpty()){
					throw new IOException("empty document");
				}

				JsonObject jsonObject=JsonParser.parseString(jsonString).getAsJsonObject();

				//check if json has required attributes
				if(!(jsonObject.has("versionName") && jsonObject.has("versionCode") && jsonObject.has("GlobalUserPreferences"))){
					Toast.makeText(getContext(), getContext().getString(R.string.import_settings_failed), Toast.LENGTH_SHORT).show();
					return;
				}
				// MAMMUT: a document from a newer schema is rejected rather than half-applied
				if(jsonObject.has("schemaVersion") && jsonObject.get("schemaVersion").getAsInt()>SettingsSchema.VERSION){
					throw new SettingsImportException("unsupported schemaVersion "+jsonObject.get("schemaVersion").getAsInt());
				}
				String versionName=jsonObject.get("versionName").getAsString();
				int versionCode=jsonObject.get("versionCode").getAsInt();
				Log.i(TAG, "onActivityResult: Reading exported settings ("+versionName+" "+versionCode+")");

				// validate GlobalUserPreferences before any write
				Map<String, ?> jsonGlobalPrefs=gson.fromJson(jsonObject.getAsJsonObject("GlobalUserPreferences"), Map.class);
				LinkedHashMap<String, Object> validatedGlobal=SettingsImportValidator.validate(jsonGlobalPrefs, SettingsSchema.GLOBAL);

				// validate LocalPreferences for all logged in accounts before any write
				//TODO: maybe show a dialog for which accounts to import?
				LinkedHashMap<AccountSession, LinkedHashMap<String, Object>> validatedAccounts=new LinkedHashMap<>();
				for(AccountSession accountSession : AccountSessionManager.getInstance().getLoggedInAccounts()){
					JsonObject accountBlock=SettingsSchema.findAccountBlock(jsonObject, accountSession.domain, accountSession.self.id);
					if(accountBlock==null)
						continue;
					Map<String, ?> prefs=gson.fromJson(accountBlock, Map.class);
					validatedAccounts.put(accountSession, SettingsImportValidator.validate(prefs, SettingsSchema.ACCOUNT));
				}

				// everything validated — apply in one commit per prefs file
				boolean ok=applyValidated(GlobalUserPreferences.getPrefs().edit(), validatedGlobal);
				for(Map.Entry<AccountSession, LinkedHashMap<String, Object>> accountEntry : validatedAccounts.entrySet()){
					ok&=applyValidated(accountEntry.getKey().getRawLocalPreferences().edit(), accountEntry.getValue());
				}
				if(!ok){
					Toast.makeText(getContext(), getContext().getString(R.string.import_settings_failed), Toast.LENGTH_SHORT).show();
					return;
				}

				// restart app to apply new preferences
				// https://stackoverflow.com/a/46848226
				PackageManager packageManager=getContext().getPackageManager();
				Intent intent=packageManager.getLaunchIntentForPackage(getContext().getPackageName());
				ComponentName componentName=intent.getComponent();
				Intent mainIntent=Intent.makeRestartActivityTask(componentName);
				// Required for API 34 and later
				// Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
				mainIntent.setPackage(getContext().getPackageName());
				getContext().startActivity(mainIntent);
				Runtime.getRuntime().exit(0);
			}catch(IOException | SettingsImportException e){
				Log.w(TAG, "Could not import settings", e);
				Toast.makeText(getContext(), getContext().getString(R.string.import_settings_failed), Toast.LENGTH_SHORT).show();
			}catch(RuntimeException e){
				// malformed JSON, non-object documents, type surprises from getAs* accessors
				Log.w(TAG, "Could not import settings", e);
				Toast.makeText(getContext(), getContext().getString(R.string.import_settings_failed), Toast.LENGTH_SHORT).show();
			}
		}

		if(requestCode==EXPORT_RESULT && resultCode==Activity.RESULT_OK){
			if(data.getData()==null){
				Toast.makeText(getContext(), R.string.error_saving_file, Toast.LENGTH_SHORT).show();
				return;
			}
			try{
				JsonObject jsonObject=SettingsSchema.buildExportDocument(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
						GlobalUserPreferences.getPrefs().getAll(), collectAccountPrefs());

				File file=new File(getContext().getCacheDir(), "moshidon-exported-settings.json");
				// MAMMUT: audit R4 F11/S1#5 — try-with-resources on every stream, a null provider
				// stream is a failure (not a silent NPE), and the cache copy is always deleted
				try(FileWriter writer=new FileWriter(file)){
					writer.write(jsonObject.toString());
					writer.flush();
				}

				// Got this from stackoverflow at https://stackoverflow.com/a/67046741
				OutputStream os=getContext().getContentResolver().openOutputStream(data.getData());
				if(os==null){
					file.delete();
					throw new IOException("null output stream");
				}
				try(InputStream is=new FileInputStream(file); OutputStream out=os){
					byte[] buffer=new byte[1024];
					int length;
					while((length=is.read(buffer))>0){
						out.write(buffer, 0, length);
					}
					out.flush();
				}finally{
					file.delete();
				}
			}catch(IOException e){
				Log.e(TAG, "Could not export settings", e);
				// The stack trace is in logcat; the user gets the fact, not a Java message.
				Toast.makeText(getContext(), R.string.error_saving_file, Toast.LENGTH_SHORT).show();
			}
		}
	}

	// MAMMUT: audit R4 F2/S1.3 — read at most maxBytes so a hostile/malformed provider can't
	// make us buffer an unbounded document; over-cap input is an import failure
	private static String readCapped(InputStream inputStream, int maxBytes) throws IOException{
		StringBuilder sb=new StringBuilder();
		char[] buffer=new char[4096];
		try(BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream))){
			int total=0;
			int read;
			while((read=reader.read(buffer))!=-1){
				total+=read;
				if(total>maxBytes)
					throw new IOException("import document exceeds "+maxBytes+" bytes");
				sb.append(buffer, 0, read);
			}
		}
		return sb.toString();
	}

	// MAMMUT: account blocks keyed by the S1#3 composite (see SettingsSchema.accountBlockKey)
	private LinkedHashMap<String, Map<String, ?>> collectAccountPrefs(){
		LinkedHashMap<String, Map<String, ?>> accounts=new LinkedHashMap<>();
		for(AccountSession accountSession : AccountSessionManager.getInstance().getLoggedInAccounts()){
			accounts.put(SettingsSchema.accountBlockKey(accountSession.domain, accountSession.self.id), accountSession.getRawLocalPreferences().getAll());
		}
		return accounts;
	}

	// MAMMUT: the validator only emits Boolean/Integer/Long/Float/String, so the apply step
	// preserves int-vs-long storage exactly (the pre-fix importer squashed every long into
	// a truncated int, which is what broke catchUpLastAt round-trips)
	private static boolean applyValidated(SharedPreferences.Editor editor, LinkedHashMap<String, Object> values){
		for(Map.Entry<String, Object> entry : values.entrySet()){
			String key=entry.getKey();
			Object value=entry.getValue();
			if(value instanceof Boolean b)
				editor.putBoolean(key, b);
			else if(value instanceof Integer i)
				editor.putInt(key, i);
			else if(value instanceof Long l)
				editor.putLong(key, l);
			else if(value instanceof Float f)
				editor.putFloat(key, f);
			else
				editor.putString(key, (String) value);
		}
		//explicitly immediately since the app will restarted soon after
		// and it may not have the time to write the values in the background
		return editor.commit();
	}

	// MAMMUT: audit R4 F2/S1.3 — import validation, extracted as a pure class for JVM tests.
	public static class SettingsImportException extends Exception{
		public SettingsImportException(String message){
			super(message);
		}
	}

	public enum PrefType{
		BOOLEAN, INT, LONG, FLOAT, STRING, ENUM
	}

	public static final class PrefRule{
		public final PrefType type;
		public final Class<? extends Enum<?>> enumType;
		public final long min, max;
		public final boolean hasMin, hasMax;

		private PrefRule(PrefType type, Class<? extends Enum<?>> enumType, long min, long max, boolean hasMin, boolean hasMax){
			this.type=type;
			this.enumType=enumType;
			this.min=min;
			this.max=max;
			this.hasMin=hasMin;
			this.hasMax=hasMax;
		}

		public static PrefRule bool(){
			return new PrefRule(PrefType.BOOLEAN, null, 0, 0, false, false);
		}

		public static PrefRule anInt(){
			return new PrefRule(PrefType.INT, null, 0, 0, false, false);
		}

		public static PrefRule anInt(long min, long max){
			return new PrefRule(PrefType.INT, null, min, max, true, true);
		}

		public static PrefRule aLong(){
			return new PrefRule(PrefType.LONG, null, 0, 0, false, false);
		}

		public static PrefRule aFloat(){
			return new PrefRule(PrefType.FLOAT, null, 0, 0, false, false);
		}

		public static PrefRule string(){
			return new PrefRule(PrefType.STRING, null, 0, 0, false, false);
		}

		public static PrefRule enumValue(Class<? extends Enum<?>> enumType){
			return new PrefRule(PrefType.ENUM, enumType, 0, 0, false, false);
		}
	}

	// MAMMUT: audit R4 F2/S1.3 — versioned schema allowlist for settings import. Every key
	// that import may write must be listed here with its expected type (plus enum membership
	// and range where applicable); anything else in the document is ignored.
	public static final class SettingsSchema{
		public static final int VERSION=1;
		public static final int MAX_IMPORT_BYTES=1024*1024;

		public static final Map<String, PrefRule> GLOBAL=buildGlobalSchema();
		public static final Map<String, PrefRule> ACCOUNT=buildAccountSchema();

		private SettingsSchema(){}

		private static Map<String, PrefRule> buildGlobalSchema(){
			LinkedHashMap<String, PrefRule> m=new LinkedHashMap<>();
			m.put("playGifs", PrefRule.bool());
			m.put("useCustomTabs", PrefRule.bool());
			m.put("theme", PrefRule.anInt(0, GlobalUserPreferences.ThemePreference.values().length-1));
			m.put("altTextReminders", PrefRule.bool());
			m.put("confirmUnfollow", PrefRule.bool());
			m.put("confirmBoost", PrefRule.bool());
			m.put("confirmDeletePost", PrefRule.bool());
			m.put("useDynamicColors", PrefRule.bool());
			m.put("interactionCounts", PrefRule.bool());
			m.put("emojiInNames", PrefRule.bool());
			m.put("showCWs", PrefRule.bool());
			m.put("hideSensitive", PrefRule.bool());
			m.put("loadNewPosts", PrefRule.bool());
			m.put("showNewPostsButton", PrefRule.bool());
			m.put("trueBlackTheme", PrefRule.bool());
			m.put("toolbarMarquee", PrefRule.bool());
			m.put("disableSwipe", PrefRule.bool());
			m.put("enableDeleteNotifications", PrefRule.bool());
			m.put("translateButtonOpenedOnly", PrefRule.bool());
			m.put("uniformNotificationIcon", PrefRule.bool());
			m.put("reduceMotion", PrefRule.bool());
			m.put("showAltIndicator", PrefRule.bool());
			m.put("showNoAltIndicator", PrefRule.bool());
			m.put("showInlineAltText", PrefRule.bool());
			m.put("enablePreReleases", PrefRule.bool());
			m.put("prefixReplies", PrefRule.enumValue(GlobalUserPreferences.PrefixRepliesMode.class));
			m.put("collapseLongPosts", PrefRule.bool());
			m.put("spectatorMode", PrefRule.bool());
			m.put("autoHideFab", PrefRule.bool());
			m.put("allowRemoteLoading", PrefRule.bool());
			m.put("autoRevealEqualSpoilers", PrefRule.enumValue(GlobalUserPreferences.AutoRevealMode.class));
			m.put("disableM3PillActiveIndicator", PrefRule.bool());
			m.put("displayPronounsInTimelines", PrefRule.bool());
			m.put("displayPronounsInThreads", PrefRule.bool());
			m.put("displayPronounsInUserListings", PrefRule.bool());
			m.put("overlayMedia", PrefRule.bool());
			m.put("showSuicideHelp", PrefRule.bool());
			m.put("underlinedLinks", PrefRule.bool());
			m.put("color", PrefRule.enumValue(AccountLocalPreferences.ColorPreference.class));
			m.put("likeIcon", PrefRule.bool());
			m.put("defaultToUnlistedReplies", PrefRule.bool());
			m.put("doubleTapToSwipe", PrefRule.bool());
			m.put("replyLineAboveHeader", PrefRule.bool());
			m.put("confirmBeforeReblog", PrefRule.bool());
			m.put("swapBookmarkWithBoostAction", PrefRule.bool());
			m.put("hapticFeedback", PrefRule.bool());
			m.put("mentionRebloggerAutomatically", PrefRule.bool());
			m.put("showDividers", PrefRule.bool());
			m.put("fullWidthPosts", PrefRule.bool());
			m.put("relocatePublishButton", PrefRule.bool());
			m.put("showPostsWithoutAlt", PrefRule.bool());
			m.put("showMediaPreview", PrefRule.bool());
			m.put("removeTrackingParams", PrefRule.bool());
			m.put("enhanceTextSize", PrefRule.bool());
			m.put("fontFamily", PrefRule.enumValue(GlobalUserPreferences.FontFamily.class));
			m.put("fabOnLeft", PrefRule.bool());
			m.put("libreTranslateUrl", PrefRule.string());
			m.put("libreTranslateApiKey", PrefRule.string());
			m.put("translationService", PrefRule.enumValue(GlobalUserPreferences.TranslationService.class));
			m.put("perAccountMigrationDone", PrefRule.bool());
			return java.util.Collections.unmodifiableMap(m);
		}

		private static Map<String, PrefRule> buildAccountSchema(){
			LinkedHashMap<String, PrefRule> m=new LinkedHashMap<>();
			m.put("serverSideFilters", PrefRule.bool());
			m.put("showReplies", PrefRule.bool());
			m.put("showBoosts", PrefRule.bool());
			m.put("recentLanguages", PrefRule.string());
			m.put("bottomEncoding", PrefRule.bool());
			m.put("defaultContentType", PrefRule.enumValue(ContentType.class));
			m.put("contentTypesEnabled", PrefRule.bool());
			m.put("timelines", PrefRule.string());
			m.put("localOnlySupported", PrefRule.bool());
			m.put("glitchInstance", PrefRule.bool());
			m.put("publishButtonText", PrefRule.string());
			m.put("timelineReplyVisibility", PrefRule.string());
			m.put("keepOnlyLatestNotification", PrefRule.bool());
			m.put("emojiReactionsEnabled", PrefRule.bool());
			m.put("showEmojiReactions", PrefRule.enumValue(AccountLocalPreferences.ShowEmojiReactions.class));
			m.put("color", PrefRule.string());
			m.put("recentCustomEmoji", PrefRule.string());
			m.put("notificationFilters", PrefRule.string());
			m.put("catchUpLastAt", PrefRule.aLong());
			m.put("notificationsPauseTime", PrefRule.aLong());
			m.put("notificationsMarker", PrefRule.string());
			m.put("notificationsMentionsOnly", PrefRule.bool());
			m.put("notificationChannelsVersion", PrefRule.anInt(1, 1000));
			return java.util.Collections.unmodifiableMap(m);
		}

		// MAMMUT: audit S1#3 — account blocks are keyed by domain_id, never the bare numeric
		// self.id (which collides across instances)
		public static String accountBlockKey(String domain, String accountId){
			return domain+"_"+accountId;
		}

		// Composite key wins; the bare numeric self.id is still accepted so exports written by
		// older builds keep importing.
		public static JsonObject findAccountBlock(JsonObject root, String domain, String accountId){
			String composite=accountBlockKey(domain, accountId);
			if(root.has(composite) && root.get(composite).isJsonObject())
				return root.getAsJsonObject(composite);
			if(root.has(accountId) && root.get(accountId).isJsonObject())
				return root.getAsJsonObject(accountId);
			return null;
		}

		// MAMMUT: audit R4 F11 — the export document builder is pure so tests can assert the
		// document is complete (all sections survive a serialize/parse/validate round-trip)
		public static JsonObject buildExportDocument(String versionName, int versionCode, Map<String, ?> globalPrefs, LinkedHashMap<String, Map<String, ?>> accountPrefs){
			JsonObject jsonObject=new JsonObject();
			jsonObject.addProperty("schemaVersion", VERSION);
			jsonObject.addProperty("versionName", versionName);
			jsonObject.addProperty("versionCode", versionCode);
			jsonObject.add("GlobalUserPreferences", new Gson().toJsonTree(globalPrefs));
			for(Map.Entry<String, Map<String, ?>> account : accountPrefs.entrySet()){
				jsonObject.add(account.getKey(), new Gson().toJsonTree(account.getValue()));
			}
			return jsonObject;
		}
	}

	// MAMMUT: audit R4 F2/S1.3 — pure validator: converts an untyped JSON-decoded map into
	// exactly-typed preference values, rejecting (before any write) arrays, wrong types,
	// unknown enum names, and out-of-range numbers.
	public static class SettingsImportValidator{
		private SettingsImportValidator(){}

		public static LinkedHashMap<String, Object> validate(Map<String, ?> values, Map<String, PrefRule> schema) throws SettingsImportException{
			if(values==null)
				throw new SettingsImportException("missing preferences object");
			LinkedHashMap<String, Object> out=new LinkedHashMap<>();
			for(Map.Entry<String, ?> entry : values.entrySet()){
				String key=entry.getKey();
				Object value=entry.getValue();
				PrefRule rule=schema.get(key);
				if(rule==null)
					continue; // unknown keys are ignored so older/newer exports stay importable
				if(value==null)
					continue;
				if(value instanceof List || value instanceof Map)
					throw new SettingsImportException(key+": arrays and objects are not allowed");
				switch(rule.type){
					case BOOLEAN -> {
						if(value instanceof Boolean b) out.put(key, b);
						else throw new SettingsImportException(key+": expected boolean");
					}
					case INT -> {
						long v=integralValue(key, value);
						if(v<Integer.MIN_VALUE || v>Integer.MAX_VALUE)
							throw new SettingsImportException(key+": out of int range");
						if(rule.hasMin && v<rule.min)
							throw new SettingsImportException(key+": below minimum "+rule.min);
						if(rule.hasMax && v>rule.max)
							throw new SettingsImportException(key+": above maximum "+rule.max);
						out.put(key, (int) v);
					}
					case LONG -> {
						out.put(key, integralValue(key, value));
					}
					case FLOAT -> {
						if(value instanceof Double d) out.put(key, d.floatValue());
						else if(value instanceof Float f) out.put(key, f);
						else throw new SettingsImportException(key+": expected number");
					}
					case STRING -> {
						if(value instanceof String s) out.put(key, s);
						else throw new SettingsImportException(key+": expected string");
					}
					case ENUM -> {
						if(!(value instanceof String s))
							throw new SettingsImportException(key+": expected enum name");
						try{
							Enum.valueOf((Class) rule.enumType, s);
						}catch(IllegalArgumentException ex){
							throw new SettingsImportException(key+": unknown enum value "+s);
						}
						out.put(key, s);
					}
				}
			}
			return out;
		}

		private static long integralValue(String key, Object value) throws SettingsImportException{
			if(value instanceof Long l) return l;
			if(value instanceof Integer i) return i;
			throw new SettingsImportException(key+": expected integer");
		}
	}

	private void onClearMediaCacheClick(ListItem<?> item){
		MastodonAPIController.runInBackground(()->{
			Activity activity=getActivity();
			ImageCache.getInstance(getActivity()).clear();
			activity.runOnUiThread(()->{
				Toast.makeText(activity, R.string.media_cache_cleared, Toast.LENGTH_SHORT).show();
				updateMediaCacheItem();
			});
		});
	}

	private void updateMediaCacheItem(){
		DiskLruCache cache=ImageCache.getInstance(getActivity()).getDiskCache();
		long size=cache==null ? 0 : cache.size();
		mediaCacheItem.subtitle=UiUtils.formatFileSize(getActivity(), size, false);
		mediaCacheItem.isEnabled=size>0;
		rebindItem(mediaCacheItem);
	}
}
