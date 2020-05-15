package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.*;

public class BooleanSerializer extends JsonSerializer
{
	public static final BooleanSerializer INSTANCE = new BooleanSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Boolean fromJson(Object o)
	{
		return new Boolean(asBoolean(o));
	}
}
