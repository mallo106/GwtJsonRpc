package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asByte;

public class ByteSerializer extends JsonSerializer
{
	public static final ByteSerializer INSTANCE = new ByteSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Byte fromJson(Object o)
	{
		return new Byte(asByte(o));
	}
}
