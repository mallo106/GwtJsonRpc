package com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives;

import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import static jsinterop.base.Js.asChar;

public class CharacterSerializer extends JsonSerializer
{
	public static final CharacterSerializer INSTANCE = new CharacterSerializer();

	@Override
	public void printJson(StringBuilder sb, Object o)
	{
		sb.append(o != null ? o.toString() : JsonSerializer.JS_NULL);
	}

	@Override
	public Character fromJson(Object o)
	{
		return new Character(asChar(o));
	}
}
