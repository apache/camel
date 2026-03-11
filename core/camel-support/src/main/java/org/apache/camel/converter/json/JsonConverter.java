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
package org.apache.camel.converter.json;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

/**
 * A set of {@link Converter} methods for camel-util-json objects.
 */
@Converter(generateBulkLoader = true)
public final class JsonConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private JsonConverter() {
    }

    @Converter(order = 1)
    public static JsonObject convertToJsonObject(String json, Exchange exchange) throws Exception {
        return Jsoner.deserialize(json, (JsonObject) null);
    }

    @Converter(order = 2)
    public static JsonArray convertToJsonArray(String json, Exchange exchange) throws Exception {
        return (JsonArray) Jsoner.deserialize(json);
    }

    @Converter(order = 3)
    public static Jsonable convertToJson(String json, Exchange exchange) throws Exception {
        return (Jsonable) Jsoner.deserialize(json);
    }

    @Converter(order = 4)
    public static JsonObject convertToJsonObject(byte[] json, Exchange exchange) throws Exception {
        return Jsoner.deserialize(new String(json), (JsonObject) null);
    }

    @Converter(order = 5)
    public static JsonArray convertToJsonArray(byte[] json, Exchange exchange) throws Exception {
        return (JsonArray) Jsoner.deserialize(new String(json));
    }

    @Converter(order = 6)
    public static Jsonable convertToJson(byte[] json, Exchange exchange) throws Exception {
        return (Jsonable) Jsoner.deserialize(new String(json));
    }

    @Converter(order = 7)
    public static JsonObject convertToJsonObject(InputStream is, Exchange exchange) throws Exception {
        Reader reader = IOHelper.buffered(new InputStreamReader(is, ExchangeHelper.getCharset(exchange)));
        return (JsonObject) Jsoner.deserialize(reader);
    }

    @Converter(order = 8)
    public static JsonArray convertToJsonArray(InputStream is, Exchange exchange) throws Exception {
        Reader reader = IOHelper.buffered(new InputStreamReader(is, ExchangeHelper.getCharset(exchange)));
        return (JsonArray) Jsoner.deserialize(reader);
    }

    @Converter(order = 9)
    public static Jsonable convertToJson(InputStream is, Exchange exchange) throws Exception {
        Reader reader = IOHelper.buffered(new InputStreamReader(is, ExchangeHelper.getCharset(exchange)));
        return (Jsonable) Jsoner.deserialize(reader);
    }

    @Converter(order = 10)
    public static JsonObject convertToJsonObject(File file, Exchange exchange) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return convertToJsonObject(is, exchange);
        }
    }

    @Converter(order = 11)
    public static JsonArray convertToJsonArray(File file, Exchange exchange) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return convertToJsonArray(is, exchange);
        }
    }

    @Converter(order = 12)
    public static Jsonable convertToJson(File file, Exchange exchange) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            return convertToJson(is, exchange);
        }
    }

    @Converter(order = 13)
    public static JsonObject convertToJsonObject(WrappedFile wf, Exchange exchange) throws Exception {
        Object body = wf.getFile();
        if (body == null) {
            body = wf.getBody();
        }
        return exchange.getContext().getTypeConverter().convertTo(JsonObject.class, exchange, body);
    }

    @Converter(order = 14)
    public static JsonArray convertToJsonArray(WrappedFile wf, Exchange exchange) throws Exception {
        Object body = wf.getFile();
        if (body == null) {
            body = wf.getBody();
        }
        return exchange.getContext().getTypeConverter().convertTo(JsonArray.class, exchange, body);
    }

    @Converter(order = 15)
    public static Jsonable convertToJson(WrappedFile wf, Exchange exchange) throws Exception {
        Object body = wf.getFile();
        if (body == null) {
            body = wf.getBody();
        }
        return exchange.getContext().getTypeConverter().convertTo(Jsonable.class, exchange, body);
    }

}
