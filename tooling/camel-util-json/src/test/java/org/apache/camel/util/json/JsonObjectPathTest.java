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
package org.apache.camel.util.json;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class JsonObjectPathTest {

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
                                    "movie": false,
                                    "id": "bk101"
                                },
                                {
                                    "title": "1984",
                                    "author": "George Orwell",
                                    "year": "1949",
                                    "genre": "Dystopian",
                                    "movie": true,
                                    "id": "bk102"
                                }
                            ]
                        }
                    }
                    """;

    private static final String COUNTRIES
            = """
                    {
                      "countries": [ "Denmark", "Sweden", "Norway" ]
                    }
                    """;

    private static final String ARRAY_ONLY
            = """
                    [ "Red", "Green", "Blue" ]
                    """;

    @Test
    public void testPath() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);

        JsonObject obj = jo.pathJsonObject("library.book[0]");
        Assertions.assertNotNull(obj);
        Assertions.assertEquals("No Title", obj.getString("title"));

        Assertions.assertNull(obj.path("?cheese"));
        try {
            Assertions.assertNull(obj.path("cheese"));
            fail();
        } catch (Exception e) {
            // expected
        }

        obj = jo.pathJsonObject("library.book[1]");
        Assertions.assertNotNull(obj);
        Assertions.assertEquals("1984", obj.getString("title"));
    }

    @Test
    public void testPathAttribute() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);
        Assertions.assertNotNull(jo);

        Assertions.assertEquals("No Title", jo.pathString("library.book[0].title"));
        Assertions.assertEquals("No Title", jo.path("library.book[0].title"));
        Assertions.assertEquals(1925, jo.pathInteger("library.book[0].year"));
        Assertions.assertEquals("1925", jo.path("library.book[0].year"));
        Assertions.assertFalse(jo.pathBoolean("library.book[0].movie"));
        Assertions.assertEquals(Boolean.FALSE, jo.path("library.book[0].movie"));
        Assertions.assertEquals("1984", jo.pathString("library.book[1].title"));
        Assertions.assertEquals("1984", jo.path("library.book[1].title"));
        Assertions.assertEquals(1949, jo.pathInteger("library.book[1].year"));
        Assertions.assertEquals("1949", jo.path("library.book[1].year"));
        Assertions.assertTrue(jo.pathBoolean("library.book[1].movie"));
        Assertions.assertEquals(Boolean.TRUE, jo.path("library.book[1].movie"));

        try {
            Assertions.assertNull(jo.pathJsonObject("library.book[1].unknown"));
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testPathOptional() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);
        Assertions.assertNotNull(jo);

        Assertions.assertNotNull(jo.pathJsonObject("library?.book[0]"));
        Assertions.assertNull(jo.pathJsonObject("library?.book[2]"));
        try {
            Assertions.assertNull(jo.pathJsonObject("library.book[2]"));
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testPathAttributeOptional() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);
        Assertions.assertNotNull(jo);

        Assertions.assertNull(jo.pathString("library?.book[2]?.subTitle"));
        Assertions.assertEquals("No Title", jo.pathString("library.book[0].title"));
        Assertions.assertNull(jo.pathString("library.book[0]?.subTitle"));
        Assertions.assertNull(jo.pathString("?unknown?.book[0].title"));

        Assertions.assertNull(jo.pathString("library?.book[2].title"));
        Assertions.assertNull(jo.pathInteger("library?.book[2].year"));
        Assertions.assertNull(jo.pathBoolean("library?.book[2].movie"));
    }

    @Test
    public void testPathAttributeArrayLast() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);
        Assertions.assertNotNull(jo);

        Assertions.assertEquals("No Title", jo.pathString("library.book[0].title"));
        Assertions.assertEquals(1925, jo.pathInteger("library.book[0].year"));
        Assertions.assertEquals("1984", jo.pathString("library.book[last].title"));
        Assertions.assertEquals(1949, jo.pathInteger("library.book[last].year"));
    }

    @Test
    public void testPathAttributeLeafNode() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(BOOKS);
        Assertions.assertNotNull(jo);

        jo = (JsonObject) jo.getJsonObject("library").getJsonArray("book").get(0);
        Assertions.assertEquals("No Title", jo.pathString("title"));
        Assertions.assertEquals(1925, jo.pathInteger("year"));
    }

    @Test
    public void testPathArray() throws Exception {
        JsonObject jo = (JsonObject) Jsoner.deserialize(COUNTRIES);
        Assertions.assertNotNull(jo);

        Assertions.assertEquals("Denmark", jo.path("countries[0]"));
        Assertions.assertEquals("Sweden", jo.path("countries[1]"));
        Assertions.assertEquals("Norway", jo.path("countries[2]"));
        Assertions.assertEquals("Norway", jo.path("countries[last]"));
        Assertions.assertNull(jo.path("?countries[3]"));
        try {
            jo.path("countries[3]");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testPathArrayOnly() throws Exception {
        JsonArray jo = (JsonArray) Jsoner.deserialize(ARRAY_ONLY);
        Assertions.assertNotNull(jo);
        Assertions.assertEquals(3, jo.size());

        // wrap in root
        JsonObject wrap = new JsonObject();
        wrap.put("_root_", jo);

        Assertions.assertEquals("Red", wrap.path("_root_.[0]"));
        Assertions.assertEquals("Green", wrap.path("_root_.[1]"));
        Assertions.assertEquals("Blue", wrap.path("_root_.[2]"));
        Assertions.assertEquals("Blue", wrap.path("_root_.[last]"));
        Assertions.assertNull(wrap.path("_root_?.[3]"));
        try {
            wrap.path("_root_.[3]");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

    }

}
