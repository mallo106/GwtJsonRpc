package com.google.gwtjsonrpc.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.JsonReaderInternalAccess;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.Streams;
import com.google.gson.internal.bind.MapTypeAdapterFactory;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author cbelizon
 */
public class MBMapTypeAdapterFactory implements TypeAdapterFactory {
	private final ConstructorConstructor constructorConstructor;

	public MBMapTypeAdapterFactory(ConstructorConstructor constructorConstructor) {
		this.constructorConstructor = constructorConstructor;
	}

	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
		Type type = typeToken.getType();

		Class<? super T> rawType = typeToken.getRawType();
		if (!Map.class.isAssignableFrom(rawType)) {
			return null;
		}

		Class<?> rawTypeOfSrc = $Gson$Types.getRawType(type);
		Type[] keyAndValueTypes = $Gson$Types.getMapKeyAndValueTypes(type, rawTypeOfSrc);
		TypeAdapter<?> keyAdapter = getKeyAdapter(gson, keyAndValueTypes[0]);
		TypeAdapter<?> valueAdapter = gson.getAdapter(TypeToken.get(keyAndValueTypes[1]));
		ObjectConstructor<T> constructor = constructorConstructor.get(typeToken);

		// we don't define a type parameter for the key or value types
		TypeAdapter<T> result = new Adapter(gson, keyAndValueTypes[0], keyAdapter, keyAndValueTypes[1], valueAdapter, constructor);
		return result;
	}

	/**
	 * Returns a type adapter that writes the value as a string.
	 */
	private TypeAdapter<?> getKeyAdapter(Gson context, Type keyType) {
		return (keyType == boolean.class || keyType == Boolean.class) ? TypeAdapters.BOOLEAN_AS_STRING : context.getAdapter(TypeToken.get(keyType));
	}

	private final class Adapter<K, V> extends TypeAdapter<Map<K, V>> {
		private final TypeAdapter<K> keyTypeAdapter;
		private final TypeAdapter<V> valueTypeAdapter;
		private final Type myKeyType;
		private final Type myValueType;
		private final ObjectConstructor<? extends Map<K, V>> constructor;

		public Adapter(Gson context, Type keyType, TypeAdapter<K> keyTypeAdapter, Type valueType, TypeAdapter<V> valueTypeAdapter, ObjectConstructor<? extends Map<K, V>> constructor) {
			this.keyTypeAdapter = new CustomTypeAdapterRuntimeTypeWrapper<K>(context, keyTypeAdapter, keyType);
			this.valueTypeAdapter = new CustomTypeAdapterRuntimeTypeWrapper<V>(context, valueTypeAdapter, valueType);
			myKeyType = keyType;
			myValueType = valueType;
			this.constructor = constructor;
		}

		public Map<K, V> read(JsonReader in) throws IOException {
			JsonToken peek = in.peek();
			if (peek == JsonToken.NULL) {
				in.nextNull();
				return null;
			} else {
				Map<K, V> map = (Map)this.constructor.construct();
				Object replaced;
				in.beginObject();
				if (in.peek() == JsonToken.END_OBJECT)
				{
					in.endObject();
					return map;
				}
				String aFirstKey = in.nextName();
				if (aFirstKey.equals("keys")) {
					in.beginArray();
					List<K> aKeys = new ArrayList<>();
					List<V> aValues = new ArrayList<>();
					while(in.hasNext()) {
						aKeys.add(this.keyTypeAdapter.read(in));
					}
					in.endArray();
					in.nextName(); //"values" name
					in.beginArray(); // "values" array
					while(in.hasNext()) {
						aValues.add(this.valueTypeAdapter.read(in));
					}
					in.endArray();
					for(int i = 0; i < aKeys.size(); i++)
					{
						map.put(aKeys.get(i), aValues.get(i));
					}
				} else {
					boolean aFirstOnly = true;
					while(in.hasNext()) {
						K key;
						if (aFirstOnly) {
							key = this.keyTypeAdapter.fromJsonTree(new JsonPrimitive(aFirstKey));
							aFirstOnly = false;
						} else {
							JsonReaderInternalAccess.INSTANCE.promoteNameToValue(in);
							key = this.keyTypeAdapter.read(in);
						}
						V value = this.valueTypeAdapter.read(in);
						replaced = map.put(key, value);
						if (replaced != null) {
							throw new JsonSyntaxException("duplicate key: " + key);
						}
					}
				}
				in.endObject();

				return map;
			}
		}

		public void write(JsonWriter out, Map<K, V> map) throws IOException {
			if (map == null) {
				out.nullValue();
				return;
			}
			boolean hasComplexKeys = false;
			List<JsonElement> keys = new ArrayList(map.size());
			List<V> values = new ArrayList(map.size());
			JsonElement keyElementx;
			for(Iterator var6 = map.entrySet().iterator(); var6.hasNext(); hasComplexKeys |= keyElementx.isJsonArray() || keyElementx.isJsonObject()) {
				Map.Entry<K, V> entry = (Map.Entry)var6.next();
				keyElementx = this.keyTypeAdapter.toJsonTree(entry.getKey());
				hasComplexKeys = hasComplexKeys ? hasComplexKeys : entry.getKey().getClass().isEnum();
				keys.add(keyElementx);
				values.add(entry.getValue());
			}
			int i;
			if (hasComplexKeys) {
				out.beginObject();
				out.name("keys");
				out.beginArray();
				for(i = 0; i < keys.size(); ++i) {
					Streams.write((JsonElement)keys.get(i), out);
				}
				out.endArray();
				out.name("values");
				out.beginArray();
				for(i = 0; i < keys.size(); ++i) {
					this.valueTypeAdapter.write(out, values.get(i));
				}
				out.endArray();
				out.endObject();
			} else {
				out.beginObject();
				for(i = 0; i < keys.size(); ++i) {
					JsonElement keyElement = (JsonElement)keys.get(i);
					out.name(this.keyToString(keyElement));
					this.valueTypeAdapter.write(out, values.get(i));
				}
				out.endObject();
			}
		}
		private String keyToString(JsonElement keyElement) {
			if (keyElement.isJsonPrimitive()) {
				JsonPrimitive primitive = keyElement.getAsJsonPrimitive();
				if (primitive.isNumber()) {
					return String.valueOf(primitive.getAsNumber());
				} else if (primitive.isBoolean()) {
					return Boolean.toString(primitive.getAsBoolean());
				} else if (primitive.isString()) {
					return primitive.getAsString();
				} else {
					throw new AssertionError();
				}
			} else if (keyElement.isJsonNull()) {
				return "null";
			} else {
				throw new AssertionError();
			}
		}
	}

	private class CustomTypeAdapterRuntimeTypeWrapper<T> extends TypeAdapter<T> {
		private final Gson context;
		private final TypeAdapter<T> delegate;
		private final Type type;

		CustomTypeAdapterRuntimeTypeWrapper(Gson context, TypeAdapter<T> delegate, Type type) {
			this.context = context;
			this.delegate = delegate;
			this.type = type;
		}

		public T read(JsonReader in) throws IOException {
			return this.delegate.read(in);
		}

		public void write(JsonWriter out, T value) throws IOException {
			TypeAdapter chosen = this.delegate;
			Type runtimeType = this.getRuntimeTypeIfMoreSpecific(this.type, value);
			if (runtimeType != this.type) {
				TypeAdapter runtimeTypeAdapter = this.context.getAdapter(TypeToken.get(runtimeType));
				if (!(runtimeTypeAdapter instanceof ReflectiveTypeAdapterFactory.Adapter)) {
					chosen = runtimeTypeAdapter;
				} else if (!(this.delegate instanceof ReflectiveTypeAdapterFactory.Adapter)) {
					chosen = this.delegate;
				} else {
					chosen = runtimeTypeAdapter;
				}
			}

			chosen.write(out, value);
		}

		private Type getRuntimeTypeIfMoreSpecific(Type type, Object value) {
			if (value != null && (type == Object.class || type instanceof TypeVariable || type instanceof Class)) {
				type = value.getClass();
			}

			return (Type) type;
		}
	}
}