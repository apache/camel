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
package org.apache.camel.component.jackson.converter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JacksonJSonNodeConverterTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void stringToJsonNode() {
        Exchange exchange = new DefaultExchange(context);

        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, "{ \"message\": \"Hello World\" }");
        assertNotNull(node);

        Assertions.assertEquals("\"Hello World\"", node.get("message").toString());
    }

    @Test
    public void byteArrayToJsonNode() {
        Exchange exchange = new DefaultExchange(context);

        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange,
                "{ \"message\": \"Bye World\" }".getBytes(StandardCharsets.UTF_8));
        assertNotNull(node);

        Assertions.assertEquals("\"Bye World\"", node.get("message").toString());
    }

    @Test
    public void inputStreamToJsonNode() {
        Exchange exchange = new DefaultExchange(context);

        ByteArrayInputStream bis = new ByteArrayInputStream("{ \"message\": \"Bye World\" }".getBytes(StandardCharsets.UTF_8));
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, bis);
        assertNotNull(node);

        Assertions.assertEquals("\"Bye World\"", node.get("message").toString());
    }

    @Test
    public void mapToJsonNode() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> map = Map.of("message", "Hello Camel");
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, map);
        assertNotNull(node);

        Assertions.assertEquals("\"Hello Camel\"", node.get("message").toString());
    }

    @Test
    public void jsonTextNodeToString() {
        Exchange exchange = new DefaultExchange(context);

        TextNode node = new TextNode("Hello World");
        String text = context.getTypeConverter().convertTo(String.class, exchange, node);
        assertNotNull(text);

        Assertions.assertEquals("Hello World", text);
    }

    @Test
    public void jsonNodeToString() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> map = Map.of("message", "Hello Camel");
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, map);

        String text = context.getTypeConverter().convertTo(String.class, exchange, node);
        assertNotNull(text);

        Assertions.assertEquals("{\n  \"message\" : \"Hello Camel\"\n}", text);
    }

    @Test
    public void jsonNodeToByteArray() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> map = Map.of("message", "Hello Camel");
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, map);

        byte[] arr = context.getTypeConverter().convertTo(byte[].class, exchange, node);
        assertNotNull(arr);
        String s = context.getTypeConverter().convertTo(String.class, exchange, arr);

        Assertions.assertEquals("{\"message\":\"Hello Camel\"}", s);
    }

    @Test
    public void jsonNodeToInputStream() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> map = Map.of("message", "Hello Camel");
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, map);

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, exchange, node);
        assertNotNull(is);
        String s = context.getTypeConverter().convertTo(String.class, exchange, is);

        Assertions.assertEquals("{\"message\":\"Hello Camel\"}", s);
    }

    @Test
    public void jsonNodeToMap() {
        Exchange exchange = new DefaultExchange(context);

        Map<String, Object> map = Map.of("message", "Bye Camel", "age", 44);
        JsonNode node = context.getTypeConverter().convertTo(JsonNode.class, exchange, map);

        Map out = context.getTypeConverter().convertTo(Map.class, exchange, node);
        assertNotNull(out);

        Assertions.assertEquals(2, out.size());
        Assertions.assertEquals("Bye Camel", out.get("message"));
        Assertions.assertEquals(44, out.get("age"));
    }

    @Test
    public void convertToInt() {
        int value = context.getTypeConverter().convertTo(Integer.class, new IntNode(42));
        Assertions.assertEquals(42, value);

        value = context.getTypeConverter().convertTo(int.class, new IntNode(43));
        Assertions.assertEquals(43, value);
    }

    @Test
    public void convertToLong() {
        long value = context.getTypeConverter().convertTo(Long.class, new LongNode(1234567890));
        Assertions.assertEquals(1234567890L, value);

        value = context.getTypeConverter().convertTo(long.class, new LongNode(44448888));
        Assertions.assertEquals(44448888L, value);
    }

    @Test
    public void convertToDouble() {
        double value = context.getTypeConverter().convertTo(Double.class, new DoubleNode(1.23d));
        Assertions.assertEquals(1.23d, value);

        value = context.getTypeConverter().convertTo(double.class, new DoubleNode(1.99d));
        Assertions.assertEquals(1.99d, value);
    }

    @Test
    public void convertToFloat() {
        float value = context.getTypeConverter().convertTo(Float.class, new DoubleNode(2.23f));
        Assertions.assertEquals(2.23f, value);

        value = context.getTypeConverter().convertTo(float.class, new DoubleNode(2.99f));
        Assertions.assertEquals(2.99f, value);
    }

    @Test
    public void convertToBoolean() {
        boolean value = context.getTypeConverter().convertTo(Boolean.class, BooleanNode.TRUE);
        Assertions.assertTrue(value);
    }

}
