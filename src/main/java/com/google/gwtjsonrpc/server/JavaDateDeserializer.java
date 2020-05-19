package com.google.gwtjsonrpc.server;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Date;

public class JavaDateDeserializer
        extends TypeAdapter<Date> {
    @Override
    public void write(JsonWriter out, Date value) throws IOException {
        if (value != null) {
            out.value(value.getTime());
        }
        else {
            out.nullValue();
        }
    }

    @Override
    public Date read(JsonReader in) throws IOException {
        if (in == null || in.peek() == JsonToken.NULL) {
            return null;
        }
        return new Date(in.nextLong());
    }
}
