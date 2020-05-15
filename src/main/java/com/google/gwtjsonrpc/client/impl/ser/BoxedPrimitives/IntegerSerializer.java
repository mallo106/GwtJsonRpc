package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asInt;

public class IntegerSerializer extends JsonSerializer
{
	public static final IntegerSerializer INSTANCE = new IntegerSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Integer fromJson(Object o)
	{
		return new Integer(asInt(o));
	}
}
