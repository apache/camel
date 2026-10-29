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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonConverterTest extends ContextTestSupport {

    private static final String BOOKS
            = """
                    {
                        "library": {
                            "book": [
                                {
                                    "title": "No Title",
                                    "author": "F. Scott Fitzgerald",
                                    "year": "1925",
                                    "genre": "Classic",
                                    "id": "bk101"
                                },
                                {
                                    "title": "1984",
                                    "author": "George Orwell",
                                    "year": "1949",
                                    "genre": "Dystopian",
                                    "id": "bk102"
                                }
                            ]
                        }
                    }
                    """;

    private static final String BOOKS_ARR = """
                [
                    {
                        "title": "No Title",
                        "author": "F. Scott Fitzgerald",
                        "year": "1925",
                        "genre": "Classic",
                        "id": "bk101"
                    },
                    {
                        "title": "1984",
                        "author": "George Orwell",
                        "year": "1949",
                        "genre": "Dystopian",
                        "id": "bk102"
                    }
                ]
            """;

    @Test
    public void testConvertObject() throws Exception {
        JsonObject jo = context.getTypeConverter().convertTo(JsonObject.class, BOOKS);
        Assertions.assertNotNull(jo);

        JsonArray arr = jo.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertArray() throws Exception {
        JsonArray arr = context.getTypeConverter().convertTo(JsonArray.class, BOOKS_ARR);
        Assertions.assertNotNull(arr);

        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertObjectShorthand() throws Exception {
        RouteDefinition rd = new RouteDefinition();
        rd.setInput(new FromDefinition("direct:start"));
        rd.addOutput(new ConvertBodyDefinition("JsonObject")); // use shorthand name instead of full FQN
        context.addRouteDefinition(rd);

        JsonObject jo = (JsonObject) template.requestBody("direct:start", BOOKS);
        Assertions.assertNotNull(jo);

        JsonArray arr = jo.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertArrayShorthand() throws Exception {
        RouteDefinition rd = new RouteDefinition();
        rd.setInput(new FromDefinition("direct:start"));
        rd.addOutput(new ConvertBodyDefinition("JsonArray")); // use shorthand name instead of full FQN
        context.addRouteDefinition(rd);

        JsonArray arr = (JsonArray) template.requestBody("direct:start", BOOKS_ARR);
        Assertions.assertNotNull(arr);

        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertObjectFromByteArray() throws Exception {
        JsonObject jo = context.getTypeConverter().convertTo(JsonObject.class, BOOKS.getBytes());
        Assertions.assertNotNull(jo);

        JsonArray arr = jo.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertObjectFromInputStream() throws Exception {
        JsonObject jo = context.getTypeConverter().convertTo(JsonObject.class, new ByteArrayInputStream(BOOKS.getBytes()));
        Assertions.assertNotNull(jo);

        JsonArray arr = jo.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));
    }

    @Test
    public void testConvertObjectFromStreamCacheInputStream() throws Exception {
        ByteArrayInputStreamCache sc = new ByteArrayInputStreamCache(new ByteArrayInputStream(BOOKS.getBytes()));
        JsonObject jo = context.getTypeConverter().convertTo(JsonObject.class, sc);
        Assertions.assertNotNull(jo);
        JsonArray arr = jo.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        JsonObject b1 = arr.getJsonObject(0);
        JsonObject b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));

        // should be able to reuse the stream cache again if we reset
        sc.reset();
        JsonObject jo2 = context.getTypeConverter().convertTo(JsonObject.class, sc);
        Assertions.assertNotSame(jo, jo2); // not same instance
        arr = jo2.getJsonObject("library").getJsonArray("book");
        Assertions.assertEquals(2, arr.size());
        b1 = arr.getJsonObject(0);
        b2 = arr.getJsonObject(1);
        Assertions.assertEquals("No Title", b1.getString("title"));
        Assertions.assertEquals(1925, b1.getInteger("year"));
        Assertions.assertEquals("1984", b2.getString("title"));
        Assertions.assertEquals(1949, b2.getInteger("year"));

    }

    @Test
    public void testConvertObjectToString() throws Exception {
        JsonObject jo = new JsonObject();
        jo.put("sku", "CAMEL-MUG");
        jo.put("qty", 3);
        jo.put("tags", new JsonArray(List.of("a", "b")));

        String json = context.getTypeConverter().convertTo(String.class, jo);
        Assertions.assertEquals("{\"sku\":\"CAMEL-MUG\",\"qty\":3,\"tags\":[\"a\",\"b\"]}", json);

        // and back again
        JsonObject jo2 = context.getTypeConverter().convertTo(JsonObject.class, json);
        Assertions.assertEquals("CAMEL-MUG", jo2.getString("sku"));
        Assertions.assertEquals(3, jo2.getInteger("qty"));
        Assertions.assertEquals(2, jo2.getCollection("tags").size());
    }

    @Test
    public void testConvertArrayToString() throws Exception {
        JsonArray arr = context.getTypeConverter().convertTo(JsonArray.class, BOOKS_ARR);

        String json = context.getTypeConverter().convertTo(String.class, arr);
        Assertions.assertTrue(json.startsWith("[{\"title\":\"No Title\""), json);

        JsonArray arr2 = context.getTypeConverter().convertTo(JsonArray.class, json);
        Assertions.assertEquals(arr, arr2);
    }

    @Test
    public void testConvertObjectToByteArray() throws Exception {
        JsonObject jo = new JsonObject();
        jo.put("city", "Århus");

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
        byte[] data = context.getTypeConverter().convertTo(byte[].class, exchange, jo);
        Assertions.assertArrayEquals("{\"city\":\"Århus\"}".getBytes(StandardCharsets.ISO_8859_1), data);

        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
        data = context.getTypeConverter().convertTo(byte[].class, exchange, jo);
        Assertions.assertArrayEquals("{\"city\":\"Århus\"}".getBytes(StandardCharsets.UTF_8), data);
    }

    @Test
    public void testConvertArrayToByteArray() throws Exception {
        JsonArray arr = new JsonArray(List.of(1, 2, 3));

        byte[] data = context.getTypeConverter().convertTo(byte[].class, arr);
        Assertions.assertEquals("[1,2,3]", new String(data));
    }

    @Test
    public void testConvertObjectToInputStream() throws Exception {
        JsonObject jo = new JsonObject();
        jo.put("sku", "CAMEL-MUG");

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, jo);
        Assertions.assertEquals("{\"sku\":\"CAMEL-MUG\"}", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testConvertArrayToInputStream() throws Exception {
        JsonArray arr = new JsonArray(List.of("a", "b"));

        InputStream is = context.getTypeConverter().convertTo(InputStream.class, arr);
        Assertions.assertEquals("[\"a\",\"b\"]", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testConvertObjectWithPojoValue() throws Exception {
        Object pojo = new Object() {
            @Override
            public String toString() {
                return "MyPojo";
            }
        };
        JsonObject jo = new JsonObject();
        jo.put("item", pojo);
        JsonArray arr = new JsonArray(List.of(pojo));

        // not JSON so the toString as before
        Assertions.assertEquals("{item=MyPojo}", context.getTypeConverter().convertTo(String.class, jo));
        Assertions.assertEquals("[MyPojo]", context.getTypeConverter().convertTo(String.class, arr));

        // and no conversion to bytes or a stream as before
        Assertions.assertNull(context.getTypeConverter().tryConvertTo(byte[].class, jo));
        Assertions.assertNull(context.getTypeConverter().tryConvertTo(InputStream.class, arr));
    }

    @Test
    public void testConvertBodyToStringInRoute() throws Exception {
        RouteDefinition rd = new RouteDefinition();
        rd.setInput(new FromDefinition("direct:start"));
        rd.addOutput(new ConvertBodyDefinition("JsonObject"));
        rd.addOutput(new ConvertBodyDefinition("String"));
        context.addRouteDefinition(rd);

        String out = template.requestBody("direct:start", "{ \"sku\" : \"CAMEL-MUG\", \"qty\" : 3 }", String.class);
        Assertions.assertEquals("{\"sku\":\"CAMEL-MUG\",\"qty\":3}", out);
    }

}
