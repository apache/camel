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
package org.apache.camel.component.vertx;

import java.io.InputStream;
import java.util.Map;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxJsonObjectConverterTest extends CamelTestSupport {

    private static final String BODY = "{\"Hello\":\"World\"}";

    @Test
    public void testBufferToJsonObject() {
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, Buffer.buffer(BODY));
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testStringToJsonObject() {
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, BODY);
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testByteArrayToJsonObject() {
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, BODY.getBytes());
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testByteBufToJsonObject() {
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, Unpooled.wrappedBuffer(BODY.getBytes()));
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testMapArrayToJsonObject() {
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, new JsonObject(BODY).getMap());
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testInputStreamToJsonObject() {
        InputStream inputStream = context.getTypeConverter().convertTo(InputStream.class, BODY);
        JsonObject jsonObject = context.getTypeConverter().convertTo(JsonObject.class, inputStream);
        Assertions.assertEquals(BODY, jsonObject.toString());
    }

    @Test
    public void testJsonObjectToBuffer() {
        Buffer result = context.getTypeConverter().convertTo(Buffer.class, Buffer.buffer(BODY).toJsonObject());
        Assertions.assertEquals(BODY, result.toString());
    }

    @Test
    public void testJsonObjectToString() {
        String result = context.getTypeConverter().convertTo(String.class, Buffer.buffer(BODY).toJsonObject());
        Assertions.assertEquals(BODY, result);
    }

    @Test
    public void testJsonObjectToByteArray() {
        byte[] result = context.getTypeConverter().convertTo(byte[].class, Buffer.buffer(BODY.getBytes()).toJsonObject());
        Assertions.assertEquals(BODY, new String(result));
    }

    @Test
    public void testJsonObjectToMap() {
        Map<String, Object> result = context.getTypeConverter().convertTo(Map.class, Buffer.buffer(BODY).toJsonObject());
        Assertions.assertEquals(BODY, new JsonObject(result).toString());
    }
}
