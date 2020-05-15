package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asDouble;

public class DoubleSerializer extends JsonSerializer
{
	public static final DoubleSerializer INSTANCE = new DoubleSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Double fromJson(Object o)
	{
		return new Double(asDouble(o));
	}
}