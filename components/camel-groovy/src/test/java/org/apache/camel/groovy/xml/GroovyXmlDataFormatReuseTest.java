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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.xml.sax.SAXParseException;

import groovy.namespace.QName;
import groovy.util.Node;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The groovyXml data format reuses its SAX parser configuration and writes with the exchange charset.
 */
public class GroovyXmlDataFormatReuseTest {

    private static final String BOOKS = """
            <library>
              <book id="bk101"><title>No Title</title></book>
              <book id="bk102"><title>1984</title></book>
            </library>
            """;

    private static final String XXE = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <foo>&xxe;</foo>
            """;

    // the output of the data format before the marshal path was changed to write through a Writer
    private static final String EXPECTED_XML = """
            <library>
              <book id="bk101">
                <title>No Title</title>
                <year>1925</year>
                <available>true</available>
                <note>Ünïcödé € 中</note>
                <price>12.5</price>
              </book>
              <book id="bk102" lang="en">
                <title>1984</title>
                <tags>
                </tags>
                <tags>
                </tags>
                <name>Città</name>
                <country>
                  <code>IT</code>
                </country>
              </book>
              <name>Biblioteca</name>
            </library>
            """;

    private CamelContext context;
    private GroovyXmlDataFormat dataFormat;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        dataFormat = new GroovyXmlDataFormat();
        dataFormat.start();
    }

    @AfterEach
    public void tearDown() {
        dataFormat.stop();
        context.stop();
    }

    private Node unmarshal(String xml) throws Exception {
        return (Node) dataFormat.unmarshal(new DefaultExchange(context),
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testDoctypeIsRejectedLikeXmlParser() throws Exception {
        SAXParseException expected = assertThrows(SAXParseException.class, () -> new XmlParser().parseText(XXE));
        SAXParseException actual = assertThrows(SAXParseException.class, () -> unmarshal(XXE));
        assertEquals(expected.getMessage(), actual.getMessage());

        // the parser of the thread is still usable after a failed parse
        assertEquals(2, unmarshal(BOOKS).children().size());
    }

    @Test
    public void testParserIsReusedSequentially() throws Exception {
        for (int i = 0; i < 3; i++) {
            Node library = unmarshal(BOOKS);
            assertEquals(2, library.children().size());
            assertEquals("bk102", ((Node) library.children().get(1)).attribute("id"));
        }
    }

    @Test
    public void testUnmarshalConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    for (int j = 0; j < 20; j++) {
                        Node library = unmarshal(BOOKS);
                        assertEquals(2, library.children().size());
                        assertEquals("No Title", ((Node) library.children().get(0)).text());
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testMarshalMapIsByteIdentical() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        dataFormat.marshal(new DefaultExchange(context), library(), bos);
        assertArrayEquals(EXPECTED_XML.getBytes(StandardCharsets.UTF_8), bos.toByteArray());
    }

    @Test
    public void testNamespacePrefixesSurviveTheRoundTrip() throws Exception {
        // XmlParser(SAXParser) leaves namespaceAware false unless it is set: prefixes would be dropped from the QNames
        String xml = "<ns:library xmlns:ns=\"urn:x\"><ns:book>a</ns:book></ns:library>";
        Exchange exchange = new DefaultExchange(context);
        Node node = (Node) dataFormat.unmarshal(exchange, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("ns", ((QName) node.name()).getPrefix());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        dataFormat.marshal(exchange, node, bos);
        String out = bos.toString(StandardCharsets.UTF_8);
        assertEquals(new String(marshalWithGroovy(xml), StandardCharsets.UTF_8), out);
        assertTrue(out.contains("<ns:library xmlns:ns=\"urn:x\">"), out);
        assertTrue(out.contains("<ns:book>a</ns:book>"), out);
    }

    private static byte[] marshalWithGroovy(String xml) throws Exception {
        Node node = new XmlParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8));
        XmlNodePrinter printer = new XmlNodePrinter(pw);
        printer.setPreserveWhitespace(true);
        printer.print(node);
        return bos.toByteArray();
    }

    private static Map<String, Object> library() {
        Map<String, Object> b1 = new LinkedHashMap<>();
        b1.put("_id", "bk101");
        b1.put("title", "No Title");
        b1.put("year", 1925);
        b1.put("available", true);
        b1.put("note", "Ünïcödé € 中");
        b1.put("price", 12.5d);
        b1.put("empty", "  ");
        b1.put("nothing", null);
        Map<String, Object> b2 = new LinkedHashMap<>();
        b2.put("@id", "bk102");
        b2.put("@lang", " en ");
        b2.put("title", "1984");
        b2.put("tags", new ArrayList<>(Arrays.asList("dystopia", "classic")));
        Map<String, Object> publisher = new LinkedHashMap<>();
        publisher.put("name", "Città");
        publisher.put("country", List.of(Map.of("code", "IT")));
        b2.put("publisher", publisher);
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("book", new ArrayList<>(Arrays.asList(b1, b2)));
        library.put("name", "Biblioteca");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("library", library);
        return root;
    }
}
