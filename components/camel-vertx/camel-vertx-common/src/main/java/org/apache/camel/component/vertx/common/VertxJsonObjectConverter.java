/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Converter;
import org.apache.camel.util.IOHelper;

/**
 * Converter methods to convert from / to Vert.x JsonObject
 */
@Converter(generateBulkLoader = true)
public final class VertxJsonObjectConverter {

    private VertxJsonObjectConverter() {
    }

    @Converter
    public static JsonObject toJsonObject(Buffer buffer) {
        return new JsonObject(buffer);
    }

    @Converter
    public static JsonObject toJsonObject(String string) {
        return new JsonObject(string);
    }

    @Converter
    public static JsonObject toJsonObject(byte[] bytes) {
        return Buffer.buffer(bytes).toJsonObject();
    }

    @Converter
    public static JsonObject toJsonObject(ByteBuf byteBuf) {
        return Buffer.buffer(byteBuf).toJsonObject();
    }

    @Converter
    public static JsonObject toJsonObject(Map<String, Object> map) {
        return new JsonObject(map);
    }

    @Converter
    public static JsonObject toJsonObject(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copy(IOHelper.buffered(inputStream), bos);
            return Buffer.buffer(bos.toByteArray()).toJsonObject();
        } finally {
            IOHelper.close(inputStream);
        }
    }

    @Converter
    public static Buffer toBuffer(JsonObject jsonObject) {
        return jsonObject.toBuffer();
    }

    @Converter
    public static String toString(JsonObject jsonObject) {
        return jsonObject.encode();
    }

    @Converter
    public static byte[] toBytes(JsonObject jsonObject) {
        return jsonObject.toBuffer().getBytes();
    }

    @Converter
    public static Map<String, Object> toMap(JsonObject jsonObject) {
        return jsonObject.getMap();
    }

    @Converter
    public static InputStream toInputStream(JsonObject jsonObject) {
        return new ByteArrayInputStream(jsonObject.toBuffer().getBytes());
    }
}
