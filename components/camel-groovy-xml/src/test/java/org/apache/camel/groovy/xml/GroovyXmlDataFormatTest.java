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
package org.apache.camel.groovy.xml;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

public class GroovyXmlDataFormatTest extends CamelTestSupport {

    private static final String BOOKS
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

    private static final String BOOKS_NO_ATTR
            = """
                    <library>
                      <book>
                        <title>No Title</title>
                        <author>F. Scott Fitzgerald</author>
                        <year>1925</year>
                        <genre>Classic</genre>
                      </book>
                      <book>
                        <title>1984</title>
                        <author>George Orwell</author>
                        <year>1949</year>
                        <genre>Dystopian</genre>
                      </book>
                    </library>
                    """;

    private static final String BOOKS_JSON
            = """
                    {
                        "library": {
                            "book": [
                                {
                                    "title": "No Title",
                                    "author": "F. Scott Fitzgerald",
                                    "year": "1925",
                                    "genre": "Classic",
                                    "_id": "bk101"
                                },
                                {
                                    "title": "1984",
                                    "author": "George Orwell",
                                    "year": "1949",
                                    "genre": "Dystopian",
                                    "_id": "bk102"
                                }
                            ]
                        }
                    }
                    """;

    private static final String COUNTRIES
            = """
                    <countries>
                      <country>Norway</country>
                      <country>Denmark</country>
                      <country>Sweden</country>
                      <country>Germany</country>
                      <country>Finland</country>
                    </countries>
                    """;

    private static final String COUNTRIES_JSON
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

    @Test
    public void testUnmarshal() throws Exception {
        getMockEndpoint("mock:unmarshal").expectedMessageCount(1);

        Object out = template.requestBody("direct:unmarshal", BOOKS);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(Node.class, out);

        Node n = (Node) out;
        Assertions.assertEquals(2, n.children().size());
        Node c = (Node) n.children().get(0);
        Assertions.assertEquals("bk101", c.attribute("id"));
        c = (Node) n.children().get(1);
        Assertions.assertEquals("bk102", c.attribute("id"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshal() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        XmlParser parser = new XmlParser();
        parser.setTrimWhitespace(false);
        Node n = parser.parseText(BOOKS);

        Object out = template.requestBody("direct:marshal", n);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalDOM() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        Document dom = context.getTypeConverter().convertTo(Document.class, BOOKS);

        Object out = template.requestBody("direct:marshal", dom);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        xml = xml + System.lineSeparator();
        Assertions.assertEquals(BOOKS, xml);

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
        b1.put("year", 1925);
        b1.put("genre", "Classic");
        b1.put("@id", "bk101");

        JsonObject b2 = new JsonObject();
        b2.put("title", "1984");
        b2.put("author", "George Orwell");
        b2.put("year", 1949);
        b2.put("genre", "Dystopian");
        b2.put("@id", "bk102");

        arr.add(b1);
        arr.add(b2);
        JsonObject books = new JsonObject();
        books.put("book", arr);
        root.put("library", books);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalCamelJSonNoAttr() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        JsonObject b1 = new JsonObject();
        b1.put("title", "No Title");
        b1.put("author", "F. Scott Fitzgerald");
        b1.put("year", 1925);
        b1.put("genre", "Classic");

        JsonObject b2 = new JsonObject();
        b2.put("title", "1984");
        b2.put("author", "George Orwell");
        b2.put("year", 1949);
        b2.put("genre", "Dystopian");

        arr.add(b1);
        arr.add(b2);
        JsonObject books = new JsonObject();
        books.put("book", arr);
        root.put("library", books);

        Object out = template.requestBody("direct:marshalNoAttr", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS_NO_ATTR, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalJacksonJSon() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        ObjectMapper om = new JsonMapper();
        JsonNode root = om.readTree(BOOKS_JSON);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalJacksonJSonNoAttr() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        ObjectMapper om = new JsonMapper();
        JsonNode root = om.readTree(BOOKS_JSON);

        Object out = template.requestBody("direct:marshalNoAttr", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(BOOKS_NO_ATTR, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUnmarshalArray() throws Exception {
        getMockEndpoint("mock:unmarshal").expectedMessageCount(1);

        Object out = template.requestBody("direct:unmarshal", COUNTRIES);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(Node.class, out);

        Node n = (Node) out;
        Assertions.assertEquals(5, n.children().size());
        Assertions.assertEquals("country[attributes={}; value=[Norway]]", n.children().get(0).toString());
        Assertions.assertEquals("country[attributes={}; value=[Denmark]]", n.children().get(1).toString());
        Assertions.assertEquals("country[attributes={}; value=[Sweden]]", n.children().get(2).toString());
        Assertions.assertEquals("country[attributes={}; value=[Germany]]", n.children().get(3).toString());
        Assertions.assertEquals("country[attributes={}; value=[Finland]]", n.children().get(4).toString());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshalArrayJacksonJSon() throws Exception {
        getMockEndpoint("mock:marshal").expectedMessageCount(1);

        ObjectMapper om = new JsonMapper();
        JsonNode root = om.readTree(COUNTRIES_JSON);

        Object out = template.requestBody("direct:marshal", root);
        Assertions.assertNotNull(out);
        Assertions.assertInstanceOf(byte[].class, out);

        String xml = context.getTypeConverter().convertTo(String.class, out);
        Assertions.assertEquals(COUNTRIES, xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:marshal").streamCache(false)
                        .marshal().groovyXml()
                        .to("mock:marshal");

                from("direct:marshalNoAttr").streamCache(false)
                        .marshal(dataFormat().groovyXml().attributeMapping(false).end())
                        .to("mock:marshal");

                from("direct:unmarshal")
                        .unmarshal().groovyXml()
                        .to("mock:unmarshal");
            }
        };
    }
}
