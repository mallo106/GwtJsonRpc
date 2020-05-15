package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asLong;

public class LongSerializer extends JsonSerializer
{
	public static final LongSerializer INSTANCE = new LongSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Long fromJson(Object o)
	{
		return new Long(asLong(o));
	}
}
