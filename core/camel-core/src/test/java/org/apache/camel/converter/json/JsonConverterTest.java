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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
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

}
