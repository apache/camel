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
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import org.apache.camel.Converter;
import org.apache.camel.util.IOHelper;

/**
 * Converter methods to convert from / to Vert.x JsonArray
 */
@Converter(generateBulkLoader = true)
public final class VertxJsonArrayConverter {

    private VertxJsonArrayConverter() {
    }

    @Converter
    public static JsonArray toJsonArray(Buffer buffer) {
        return new JsonArray(buffer);
    }

    @Converter
    public static JsonArray toJsonArray(String string) {
        return new JsonArray(string);
    }

    @Converter
    public static JsonArray toJsonArray(byte[] bytes) {
        return Buffer.buffer(bytes).toJsonArray();
    }

    @Converter
    public static JsonArray toJsonArray(ByteBuf byteBuf) {
        return Buffer.buffer(byteBuf).toJsonArray();
    }

    @Converter
    public static JsonArray toJsonArray(List<?> list) {
        return new JsonArray(list);
    }

    @Converter
    public static JsonArray toJsonArray(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copy(IOHelper.buffered(inputStream), bos);
            return Buffer.buffer(bos.toByteArray()).toJsonArray();
        } finally {
            IOHelper.close(inputStream);
        }
    }

    @Converter
    public static Buffer toBuffer(JsonArray jsonArray) {
        return jsonArray.toBuffer();
    }

    @Converter
    public static String toString(JsonArray jsonArray) {
        return jsonArray.encode();
    }

    @Converter
    public static byte[] toBytes(JsonArray jsonArray) {
        return jsonArray.toBuffer().getBytes();
    }

    @Converter
    public static List<?> toList(JsonArray jsonArray) {
        return jsonArray.getList();
    }

    @Converter
    public static InputStream toInputStream(JsonArray jsonArray) {
        return new ByteArrayInputStream(jsonArray.toBuffer().getBytes());
    }
}
