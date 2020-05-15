// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gwtjsonrpc.client.impl.ser;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.JsonSerializer;
import com.google.gwtjsonrpc.client.impl.ResultDeserializer;
import java.util.HashMap;
import java.util.Map;

/**
 * Serialization for a {@link java.util.Map} using any Object key.
 *
 * <p>The JSON format is an array with even length, alternating key and value elements. For example:
 * <code>[k1, v1, k2, v2, k3, v3, ...]</code>. The keys and values may be any Object type.
 *
 * <p>When deserialized from JSON the Map implementation is always a {@link HashMap}. When
 * serializing to JSON any Map is permitted.
 */
public class ObjectMapSerializer<K, V> extends JsonSerializer<java.util.Map<K, V>>
    implements ResultDeserializer<java.util.Map<K, V>> {
  private final JsonSerializer<K> keySerializer;
  private final JsonSerializer<V> valueSerializer;

  public ObjectMapSerializer(final JsonSerializer<K> k, final JsonSerializer<V> v) {
    keySerializer = k;
    valueSerializer = v;
  }

  @Override
  public void printJson(final StringBuilder sb, final java.util.Map<K, V> o) {
    sb.append('{');
    boolean first = true;
    sb.append("\"keys\":[");
    for (K aKey : o.keySet()) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      encode(sb, keySerializer, aKey);
    }
    sb.append("],");

    sb.append("\"values\":[");
    first = true;
    for (V aValue : o.values()) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      encode(sb, valueSerializer, aValue);
    }
    sb.append(']');
    sb.append('}');
  }

  private static <T> void encode(
      final StringBuilder sb, final JsonSerializer<T> serializer, final T item) {
    if (item != null) {
      serializer.printJson(sb, item);
    } else {
      sb.append(JS_NULL);
    }
  }

  @Override
  public java.util.Map<K, V> fromJson(final Object o) {
    if (o == null) {
      return null;
    }

    final JavaScriptObject jso = (JavaScriptObject) o;
    final HashMap<K, V> r = new HashMap<>();
    copy(r, jso);
    return r;
  }

  @Override
  public java.util.Map<K, V> fromResult(final JavaScriptObject response) {
    final JavaScriptObject result = ObjectSerializer.objectResult(response);
    return result == null ? null : fromJson(result);
  }

  private native void copy(Map<K, V> r, JavaScriptObject jsObject)/*-{
    for (var i in jsObject.keys) {
      this.@com.google.gwtjsonrpc.client.impl.ser.ObjectMapSerializer::copyOne(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)(r, jsObject.keys[i], jsObject.values[i]);
    }
  }-*/ ;

  void copyOne(final Map<K, V> r, final Object k, final Object o) {
    r.put(keySerializer.fromJson(k), valueSerializer.fromJson(o));
  }
}
