package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asFloat;

public class FloatSerializer  extends JsonSerializer
{
	public static final FloatSerializer INSTANCE = new FloatSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Float fromJson(Object o)
	{
		return new Float(asFloat(o));
	}
}
