package org.joinmastodon.android.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.model.Translation;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import androidx.annotation.Nullable;
import me.grishka.appkit.api.Callback;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MAMMUT: universal translation fallback. When the user's instance has no built-in
 * translator (or its translate endpoint fails), Mammut can translate through a
 * user-configured LibreTranslate endpoint instead. LibreTranslate is open-source and
 * self-hostable, so the app stays fully open-source and the user controls where the
 * text goes. The instance's own translator is still preferred when it works.
 *
 * The request-building, response-parsing and endpoint-normalization logic is pure and
 * unit-tested; only {@link #translate} touches the network, and it reports back on the
 * main thread through the appkit {@link Callback} contract.
 */
public class LibreTranslateClient{
	private static final Gson GSON=new Gson();
	private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
	static final int MAX_RESPONSE_BYTES=512*1024;

	private LibreTranslateClient(){}

	/** True when a usable fallback endpoint is configured. */
	public static boolean isConfigured(String url){
		return normalizeEndpoint(url)!=null;
	}

	/** The built-in Mammut Translate endpoint, available to every user out of the box. */
	public static final String MAMMUT_TRANSLATE_URL="https://translate.snakepit.us";

	/**
	 * Resolve the fallback endpoint for the selected translation service. MAMMUT uses the
	 * built-in endpoint; CUSTOM uses the user's normalized URL (null if invalid); OFF disables
	 * the fallback entirely (null). Pure so the routing decision is unit-testable.
	 */
	public static String effectiveUrl(GlobalUserPreferences.TranslationService service, String customUrl){
		switch(service){
			case MAMMUT: return MAMMUT_TRANSLATE_URL;
			case CUSTOM: return normalizeEndpoint(customUrl);
			default: return null;
		}
	}

	/** The API key only applies to a custom instance; Mammut Translate needs none. */
	public static String effectiveApiKey(GlobalUserPreferences.TranslationService service, String customKey){
		return service==GlobalUserPreferences.TranslationService.CUSTOM ? customKey : null;
	}

	/** True when the selected service yields a usable fallback endpoint. */
	public static boolean isFallbackEnabled(GlobalUserPreferences.TranslationService service, String customUrl){
		return effectiveUrl(service, customUrl)!=null;
	}

	/**
	 * Normalize a user-entered URL into a LibreTranslate {@code /translate} endpoint.
	 * Accepts either the service base URL or a full {@code /translate} URL. Strictly requires
	 * HTTPS and a valid {@link HttpUrl} with no embedded credentials, query, or fragment so
	 * malformed or cleartext entries never hit the network or crash {@link Request.Builder}.
	 */
	public static String normalizeEndpoint(String url){
		if(url==null)
			return null;
		String u=url.trim();
		if(!u.regionMatches(true, 0, "https://", 0, 8))
			return null;
		while(u.endsWith("/"))
			u=u.substring(0, u.length()-1);
		if(u.length()<=8)
			return null;
		HttpUrl parsed=HttpUrl.parse(u);
		if(parsed==null || !parsed.isHttps() || parsed.host().isEmpty())
			return null;
		if(!parsed.encodedUsername().isEmpty() || !parsed.encodedPassword().isEmpty())
			return null;
		if(parsed.encodedQuery()!=null || parsed.fragment()!=null)
			return null;
		if(parsed.encodedPath().endsWith("/translate"))
			return parsed.toString();
		return parsed.newBuilder().addPathSegment("translate").build().toString();
	}

	static String readCapped(InputStream in, int maxBytes) throws IOException{
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		byte[] buf=new byte[4096];
		int total=0;
		int read;
		while((read=in.read(buf))!=-1){
			total+=read;
			if(total>maxBytes)
				throw new IOException("LibreTranslate response exceeds "+maxBytes+" bytes");
			out.write(buf, 0, read);
		}
		return out.toString(StandardCharsets.UTF_8.name());
	}

	/** Build the LibreTranslate request body. HTML format preserves post markup. */
	public static String buildPayload(String content, String sourceLang, String targetLang, String apiKey){
		JsonObject o=new JsonObject();
		o.addProperty("q", content==null ? "" : content);
		o.addProperty("source", sourceLang==null || sourceLang.isEmpty() ? "auto" : sourceLang);
		o.addProperty("target", targetLang==null ? "" : targetLang);
		o.addProperty("format", "html");
		if(apiKey!=null && !apiKey.isEmpty())
			o.addProperty("api_key", apiKey);
		return GSON.toJson(o);
	}

	/**
	 * Parse a LibreTranslate response into the app's {@link Translation} model.
	 * {@code detectedSourceLanguage} is always non-null (the display footer runs it through
	 * {@code Locale.forLanguageTag}); when the service omits it we fall back to the language
	 * we asked to translate from, then to {@code und}.
	 */
	public static Translation parseTranslation(String json, String fallbackSourceLang){
		JsonObject o=JsonParser.parseString(json).getAsJsonObject();
		if(!o.has("translatedText") || o.get("translatedText").isJsonNull())
			throw new IllegalArgumentException("LibreTranslate response has no translatedText");
		Translation t=new Translation();
		t.content=o.get("translatedText").getAsString();
		t.provider="LibreTranslate";
		String detected=null;
		if(o.has("detectedLanguage") && o.get("detectedLanguage").isJsonObject()){
			JsonObject d=o.getAsJsonObject("detectedLanguage");
			if(d.has("language") && !d.get("language").isJsonNull())
				detected=d.get("language").getAsString();
		}
		if(detected==null || detected.isEmpty())
			detected=fallbackSourceLang;
		if(detected==null || detected.isEmpty())
			detected="und";
		t.detectedSourceLanguage=detected;
		return t;
	}

	/**
	 * Translate {@code content} (HTML) through the configured endpoint. Success and error
	 * callbacks both run on the main thread. A non-2xx response, a transport failure, an
	 * over-cap response or an unparseable body all report an error so the caller can surface
	 * a single failure path. Returns the enqueued {@link Call} so callers can cancel it.
	 */
	@Nullable
	public static Call translate(String endpointUrl, String apiKey, String content, String sourceLang, String targetLang, Callback<Translation> callback){
		String endpoint=normalizeEndpoint(endpointUrl);
		if(endpoint==null){
			UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("No LibreTranslate endpoint configured", -1, null)));
			return null;
		}
		Request req;
		try{
			req=new Request.Builder()
					.url(endpoint)
					.post(RequestBody.create(JSON, buildPayload(content, sourceLang, targetLang, apiKey)))
					.build();
		}catch(IllegalArgumentException e){
			UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("Invalid LibreTranslate endpoint", -1, e)));
			return null;
		}
		OkHttpClient client=MastodonAPIController.getHttpClient();
		Call call=client.newCall(req);
		call.enqueue(new okhttp3.Callback(){
			@Override
			public void onFailure(Call call, IOException e){
				if(call.isCanceled())
					return;
				UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate request failed", -1, e)));
			}

			@Override
			public void onResponse(Call call, Response response){
				try{
					if(call.isCanceled())
						return;
					if(!response.isSuccessful()){
						UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate HTTP "+response.code(), response.code(), null)));
						return;
					}
					String body=response.body()!=null ? readCapped(response.body().byteStream(), MAX_RESPONSE_BYTES) : "";
					Translation t=parseTranslation(body, sourceLang);
					UiUtils.runOnUiThread(()->{
						if(!call.isCanceled())
							callback.onSuccess(t);
					});
				}catch(Exception e){
					if(!call.isCanceled())
						UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate response unreadable", -1, e)));
				}finally{
					response.close();
				}
			}
		});
		return call;
	}
}