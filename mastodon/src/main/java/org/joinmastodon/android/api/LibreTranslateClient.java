package org.joinmastodon.android.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.model.Translation;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.io.IOException;

import me.grishka.appkit.api.Callback;
import okhttp3.Call;
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

	private LibreTranslateClient(){}

	/** True when a usable fallback endpoint is configured. */
	public static boolean isConfigured(String url){
		return normalizeEndpoint(url)!=null;
	}

	/**
	 * Normalize a user-entered URL into a LibreTranslate {@code /translate} endpoint.
	 * Accepts either the service base URL or a full {@code /translate} URL. Returns null
	 * for anything that is not a non-empty http(s) URL, so a half-typed or invalid entry
	 * disables the fallback rather than producing a malformed request.
	 */
	public static String normalizeEndpoint(String url){
		if(url==null)
			return null;
		String u=url.trim();
		if(!u.startsWith("http://") && !u.startsWith("https://"))
			return null;
		while(u.endsWith("/"))
			u=u.substring(0, u.length()-1);
		// "https://" alone (or "http://") trims down to the scheme with no host
		if(u.equals("https:") || u.equals("http:") || u.isEmpty())
			return null;
		if(u.endsWith("/translate"))
			return u;
		return u+"/translate";
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
	 * callbacks both run on the main thread. A non-2xx response, a transport failure or an
	 * unparseable body all report an error so the caller can surface a single failure path.
	 */
	public static void translate(String endpointUrl, String apiKey, String content, String sourceLang, String targetLang, Callback<Translation> callback){
		String endpoint=normalizeEndpoint(endpointUrl);
		if(endpoint==null){
			UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("No LibreTranslate endpoint configured", -1, null)));
			return;
		}
		OkHttpClient client=MastodonAPIController.getHttpClient();
		Request req=new Request.Builder()
				.url(endpoint)
				.post(RequestBody.create(JSON, buildPayload(content, sourceLang, targetLang, apiKey)))
				.build();
		client.newCall(req).enqueue(new okhttp3.Callback(){
			@Override
			public void onFailure(Call call, IOException e){
				UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate request failed", -1, e)));
			}

			@Override
			public void onResponse(Call call, Response response){
				try{
					String body=response.body()!=null ? response.body().string() : "";
					if(!response.isSuccessful()){
						UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate HTTP "+response.code(), response.code(), null)));
						return;
					}
					Translation t=parseTranslation(body, sourceLang);
					UiUtils.runOnUiThread(()->callback.onSuccess(t));
				}catch(Exception e){
					UiUtils.runOnUiThread(()->callback.onError(new MastodonErrorResponse("LibreTranslate response unreadable", -1, e)));
				}finally{
					response.close();
				}
			}
		});
	}
}