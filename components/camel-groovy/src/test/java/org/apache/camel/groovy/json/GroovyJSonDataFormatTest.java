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
package org.apache.camel.groovy.json;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import groovy.json.JsonSlurper;
import groovy.util.Node;
import groovy.xml.XmlParser;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroovyJSonDataFormatTest extends CamelTestSupport {

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

    private static final String BOOKS_XML
            = """
                    <library>
                      <book id="bk101">
                        <title>No Title</title>
                        <author>F. Scott Fitzgerald</author>
                        <year>1925</year>
                        <genre>Classic</genre>
                      </book>
                      <book id="bk102">
                        <title>1984</title>
                        <author>George Orwell</author>
                        <year>1949</year>
                        <genre>Dystopian</genre>
                      </book>
                    </library>
                    """;

    private static final String BOOKS_ID_TOP
            = """
                    {
                        "library": {
                            "book": [
                                {
                                    "id": "bk101",
                                    "title": "No Title",
                                    "author": "F. Scott Fitzgerald",
                                    "year": "1925",
                                    "genre": "Classic"
                                },
                                {
                                    "id": "bk102",
                                    "title": "1984",
                                    "author": "George Orwell",
                                    "year": "1949",
                                    "genre": "Dystopian"
                                }
                            ]
                        }
                    }
                    """;

    private static final String BOOKS_NOT_PRETTY
            = """
                    "{\\"library\\":{\\"book\\":[{\\"title\\":\\"No Title\\",\\"author\\":\\"F. Scott Fitzgerald\\",\\"year\\":\\"1925\\",\\"genre\\":\\"Classic\\",\\"id\\":\\"bk101\\"},{\\"title\\":\\"1984\\",\\"author\\":\\"George Orwell\\",\\"year\\":\\"1949\\",\\"genre\\":\\"Dystopian\\",\\"id\\":\\"bk102\\"}]}}"
                    """;

    private static final String COUNTRIES
            = """
                    {
                        "countries": [
                          {
                            "country": "Norway"
                          },
                          {
                            "country": "Denmark"
                          },
                          {
                            "country": "Sweden"
                          },
                          {
                            "country": "Germany"
                          },
                          {
                            "country": "Finland"
                          }
                        ]
                    }
                    """;

    private static final String JACKSON_COUNTRIES = """
            {
                "countries": [
                    {
                        "country": "Norway"
                    },
                    {
                        "country": "Denmark"
                    },
                    {
                        "country": "Sweden"
                    },
                    {
                        "country": "Germany"
                    },
                    {
                        "country": "Finland"
                    }
                ]
            }
            """;

    @Test
    public void testUnmarshal() throws Exception {
        getMockEndpoint("mock:unmarshal").expectedMessageCount(1);

        Object out = template.requestBody("direct:unmarshal", BOOKS);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(Map.class, out);

        Map n = (Map) out;
        n = (Map) n.get("library");
        List books = (List) n.get("book");
        Assertions.assertEquals(2, books.size());
        Map b1 = (Map) books.get(0);
        Assertions.assertEquals("bk101", b1.get("id"));
        Map b2 = (Map) books.get(1);
        Assertions.assertEquals("bk102", b2.get("id"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshal() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        JsonSlurper parser = new JsonSlurper();
        Map n = (Map) parser.parseText(BOOKS);

        Object out = template.requestBody("direct:marshal", n);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalNotPretty() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        JsonSlurper parser = new JsonSlurper();
        Map n = (Map) parser.parseText(BOOKS);

        Object out = template.requestBody("direct:marshal-not-pretty", n);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS_NOT_PRETTY, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalCamelJSon() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        JsonObject b1 = new JsonObject();
        b1.put("title", "No Title");
        b1.put("author", "F. Scott Fitzgerald");
        b1.put("year", "1925");
        b1.put("genre", "Classic");
        b1.put("id", "bk101");

        JsonObject b2 = new JsonObject();
        b2.put("title", "1984");
        b2.put("author", "George Orwell");
        b2.put("year", "1949");
        b2.put("genre", "Dystopian");
        b2.put("id", "bk102");

        arr.add(b1);
        arr.add(b2);
        JsonObject books = new JsonObject();
        books.put("book", arr);
        root.put("library", books);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalGroovyNode() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        XmlParser parser = new XmlParser();
        Node root = parser.parseText(BOOKS_XML);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS_ID_TOP, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalJacksonJSon() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        ObjectMapper om = new JsonMapper();
        JsonNode root = om.readTree(BOOKS);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUnmarshalArray() throws Exception {
        getMockEndpoint("mock:unmarshal").expectedMessageCount(1);

        Object out = template.requestBody("direct:unmarshal", COUNTRIES);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(Map.class, out);

        Map n = (Map) out;
        List list = (List) n.get("countries");
        Assertions.assertEquals(5, list.size());
        Assertions.assertEquals("{country=Norway}", list.get(0).toString());
        Assertions.assertEquals("{country=Denmark}", list.get(1).toString());
        Assertions.assertEquals("{country=Sweden}", list.get(2).toString());
        Assertions.assertEquals("{country=Germany}", list.get(3).toString());
        Assertions.assertEquals("{country=Finland}", list.get(4).toString());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalArrayJacksonJSon() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        ObjectMapper om = new JsonMapper();
        JsonNode root = om.readTree(COUNTRIES);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String json = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(JACKSON_COUNTRIES, json + "\n");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:marshal").streamCache(false)
                        .marshal().groovyJson()
                        .to("mock:marshal");

                from("direct:unmarshal")
                        .unmarshal().groovyJson()
                        .to("mock:unmarshal");

                dataFormat().groovyJson().prettyPrint(false).end();

                from("direct:marshal-not-pretty").streamCache(false)
                        .marshal(dataFormat().groovyJson().prettyPrint(false).end())
                        .to("mock:marshal");

            }
        };
    }
}
