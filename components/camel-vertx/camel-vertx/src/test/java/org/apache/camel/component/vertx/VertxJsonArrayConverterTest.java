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
import java.util.List;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxJsonArrayConverterTest extends CamelTestSupport {

    private static final String BODY = "[\"Hello\",\"World\"]";

    @Test
    public void testBufferToJsonArray() {
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, Buffer.buffer(BODY));
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testStringToJsonArray() {
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, BODY);
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testByteArrayToJsonArray() {
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, BODY.getBytes());
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testByteBufToJsonArray() {
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, Unpooled.wrappedBuffer(BODY.getBytes()));
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testListArrayToJsonArray() {
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, new JsonArray(BODY).getList());
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testInputStreamToJsonArray() {
        InputStream inputStream = context.getTypeConverter().convertTo(InputStream.class, BODY);
        JsonArray jsonArray = context.getTypeConverter().convertTo(JsonArray.class, inputStream);
        Assertions.assertEquals(BODY, jsonArray.toString());
    }

    @Test
    public void testJsonArrayToBuffer() {
        Buffer result = context.getTypeConverter().convertTo(Buffer.class, Buffer.buffer(BODY).toJsonArray());
        Assertions.assertEquals(BODY, result.toString());
    }

    @Test
    public void testJsonArrayToString() {
        String result = context.getTypeConverter().convertTo(String.class, Buffer.buffer(BODY).toJsonArray());
        Assertions.assertEquals(BODY, result);
    }

    @Test
    public void testJsonArrayToByteArray() {
        byte[] result = context.getTypeConverter().convertTo(byte[].class, Buffer.buffer(BODY.getBytes()).toJsonArray());
        Assertions.assertEquals(BODY, new String(result));
    }

    @Test
    public void testJsonArrayToList() {
        List result = context.getTypeConverter().convertTo(List.class, Buffer.buffer(BODY).toJsonArray());
        Assertions.assertEquals(BODY, new JsonArray(result).toString());
    }
}
