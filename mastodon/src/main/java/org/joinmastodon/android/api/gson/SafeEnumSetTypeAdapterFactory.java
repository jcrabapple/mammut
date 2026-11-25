package org.joinmastodon.android.api.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumSet;

/**
 * Forward-compatible Gson {@link TypeAdapterFactory} for {@link EnumSet}.
 * Unknown server enum values decode to {@code null} via Gson's default enum adapter,
 * which normally throws {@link NullPointerException} inside {@link EnumSet#add(Object)}.
 * This adapter silently skips unknown values instead of failing the enclosing model or page.
 */
public class SafeEnumSetTypeAdapterFactory implements TypeAdapterFactory{
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken){
		if(!EnumSet.class.isAssignableFrom(typeToken.getRawType()))
			return null;
		Type type=typeToken.getType();
		if(!(type instanceof ParameterizedType pt))
			return null;
		Type[] args=pt.getActualTypeArguments();
		if(args.length!=1 || !(args[0] instanceof Class<?> rawArg) || !rawArg.isEnum())
			return null;
		Class<? extends Enum> enumClass=(Class<? extends Enum>) rawArg;
		TypeAdapter<Enum> elementAdapter=(TypeAdapter<Enum>) gson.getAdapter(rawArg);
		return (TypeAdapter<T>) new EnumSetAdapter(enumClass, elementAdapter);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final class EnumSetAdapter<E extends Enum<E>> extends TypeAdapter<EnumSet<E>>{
		private final Class<E> enumClass;
		private final TypeAdapter<E> elementAdapter;

		EnumSetAdapter(Class<E> enumClass, TypeAdapter<E> elementAdapter){
			this.enumClass=enumClass;
			this.elementAdapter=elementAdapter;
		}

		@Override
		public void write(JsonWriter out, EnumSet<E> value) throws IOException{
			if(value==null){
				out.nullValue();
				return;
			}
			out.beginArray();
			for(E elem:value){
				if(elem!=null)
					elementAdapter.write(out, elem);
			}
			out.endArray();
		}

		@Override
		public EnumSet<E> read(JsonReader in) throws IOException{
			if(in.peek()==JsonToken.NULL){
				in.nextNull();
				return null;
			}
			EnumSet<E> result=EnumSet.noneOf(enumClass);
			in.beginArray();
			while(in.hasNext()){
				E elem=elementAdapter.read(in);
				if(elem!=null)
					result.add(elem);
			}
			in.endArray();
			return result;
		}
	}
}
