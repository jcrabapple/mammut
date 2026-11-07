package org.joinmastodon.android.model;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

// MAMMUT: F2 — Mastodon 4.4 added a third filter action, blur.
// The explicit TypeAdapter is opt-in forward compatibility: an unrecognized wire value decodes
// as null (it never throws), and rendering code treats null as WARN. gson's stock enum handling
// only degrades this way by accident (it holds when every constant carries @SerializedName),
// so the adapter makes "unknown -> null" an explicit contract instead of a side effect.
@JsonAdapter(FilterAction.Adapter.class)
public enum FilterAction{
	WARN("warn"),
	BLUR("blur"),
	HIDE("hide");

	public final String wireName;

	FilterAction(String wireName){
		this.wireName=wireName;
	}

	public static class Adapter extends TypeAdapter<FilterAction>{
		@Override
		public FilterAction read(JsonReader in) throws IOException{
			if(in.peek()==JsonToken.NULL){
				in.nextNull();
				return null;
			}
			String name=in.nextString();
			for(FilterAction action:values()){
				if(action.wireName.equals(name))
					return action;
			}
			return null;
		}

		@Override
		public void write(JsonWriter out, FilterAction value) throws IOException{
			if(value==null){
				out.nullValue();
				return;
			}
			out.value(value.wireName);
		}
	}
}
