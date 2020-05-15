package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asShort;

public class ShortSerializer extends JsonSerializer
{
	public static final ShortSerializer INSTANCE = new ShortSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Short fromJson(Object o)
	{
		return new Short(asShort(o));
	}
}
