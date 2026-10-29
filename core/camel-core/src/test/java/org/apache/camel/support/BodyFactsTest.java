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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BodyTypeRegistry.Kind;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.BodyFacts.Need;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25749: what the body is, the step that set it, and what to write so that a step gets the body it needs.
 */
public class BodyFactsTest extends ContextTestSupport {

    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.setMessageHistory(true);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unchanged").process(e -> seen.add(BodyFacts.describe(e)));
                from("direct:setBy").setBody(constant(new LinkedHashMap<>(Map.of("sku", "A1")))).log("next")
                        .process(e -> seen.add(BodyFacts.describe(e)));
                from("direct:emptied").setBody(constant(null)).log("next")
                        .process(e -> seen.add(BodyFacts.hint(e, Need.DATA)));
                from("direct:marshalled").marshal(new TextDataFormat())
                        .process(e -> seen.add(BodyFacts.describe(e) + ": " + BodyFacts.hint(e, Need.DATA)));
            }
        };
    }

    @Test
    public void testTheConsumerGaveTheBody() {
        template.sendBody("direct:unchanged", "hello");
        assertEquals(List.of("the body is text (a String), unchanged since from: direct://unchanged"), seen);
    }

    @Test
    public void testTheStepThatSetTheBody() {
        template.sendBody("direct:setBy", "hello");
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).startsWith("the body is a Map (LinkedHashMap), set by setBody"), seen.get(0));
    }

    @Test
    public void testAFieldReadAfterASetBodyThatEmptiedIt() {
        // r23 (http-client s1): the route set the body to ${null} and then read ${body[sku]}
        template.sendBody("direct:emptied", "hello");
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).startsWith("the setBody"), seen.get(0));
        assertTrue(seen.get(0).contains("emptied it: read what is needed before that step, for example into a variable"
                                        + " with setVariable, or write a fixed value as a constant where it is used"),
                seen.get(0));
    }

    @Test
    public void testAFieldReadAfterAMarshalOfData() {
        // what the benchmark wrote (r22): the ticket is marshalled for a file, and a field is read after it
        template.sendBody("direct:marshalled", new LinkedHashMap<>(Map.of("ticketId", "T-1")));
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains(", set by marshal"), seen.get(0));
        assertTrue(seen.get(0).contains("wrote it from data (LinkedHashMap): read the field before the marshal, for"
                                        + " example into a variable with setVariable, or keep the data in the body and"
                                        + " marshal into a variable"),
                seen.get(0));
        // nested classes, whose canonical names cannot be loaded back as classes
        seen.clear();
        template.sendBody("direct:marshalled", Map.of("ticketId", "T-1"));
        assertTrue(seen.get(0).contains("wrote it from data (Map): read the field before the marshal"), seen.get(0));
        seen.clear();
        template.sendBody("direct:marshalled", List.of("T-1", "T-2"));
        assertTrue(seen.get(0).contains("wrote it from data (List): read the field before the marshal"), seen.get(0));
        // a marshal of text: unmarshal was meant
        seen.clear();
        template.sendBody("direct:marshalled", "{\"ticketId\": \"T-1\"}");
        assertTrue(seen.get(0).contains("unmarshal it first"), seen.get(0));
    }

    @Test
    public void testTheBodyInPlainWords() {
        assertEquals("null", BodyFacts.words(null));
        assertEquals("text (a String)", BodyFacts.words("{\"sku\": \"A1\"}"));
        assertEquals("empty text (a String)", BodyFacts.words(" "));
        assertEquals("bytes (a byte[])", BodyFacts.words(new byte[] { 1 }));
        assertEquals("a stream (ByteArrayInputStream)", BodyFacts.words(new ByteArrayInputStream(new byte[0])));
        assertEquals("a Map (LinkedHashMap)", BodyFacts.words(new LinkedHashMap<>()));
        assertEquals("a List (ArrayList)", BodyFacts.words(new ArrayList<>()));
        // a class the JDK keeps to itself goes by the interface it implements
        assertEquals("a Map", BodyFacts.words(Map.of("sku", "A1")));
        assertEquals("a List", BodyFacts.words(List.of("A1")));
        assertEquals("a List", BodyFacts.words(Arrays.asList("A1")));
        assertEquals("a collection (Set)", BodyFacts.words(Collections.unmodifiableSet(new HashSet<>())));
        assertEquals("a Map", BodyFacts.words(Collections.unmodifiableMap(new HashMap<>())));
        assertEquals("an iterator", BodyFacts.words(List.of(1).iterator()));
        assertEquals("an iterator (Scanner)", BodyFacts.words(new Scanner("A1")));
        assertEquals("an object of class java.lang.Object", BodyFacts.words(new Object()));
        // without the message history the step is not known
        assertEquals("the body is a Map (LinkedHashMap)", BodyFacts.describe(exchange(new LinkedHashMap<>())));
    }

    @Test
    public void testDataHints() {
        assertEquals("unmarshal it first (unmarshal: json) to read its fields",
                BodyFacts.hint(exchange("{\"sku\": \"A1\"}"), Need.DATA));
        Exchange xml = exchange("<order/>".getBytes(StandardCharsets.UTF_8));
        xml.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");
        assertEquals("unmarshal it first (unmarshal: jacksonXml, for the Content-Type application/xml) to read its"
                     + " fields, or read them with xpath",
                BodyFacts.hint(xml, Need.DATA));
        Exchange csv = exchange("A1,2");
        csv.getMessage().setHeader(Exchange.FILE_NAME, "orders.csv");
        assertEquals("unmarshal it first (unmarshal: csv, for the file name orders.csv) to read its rows: a List of rows,"
                     + " or a Map per row with useMaps: true",
                BodyFacts.hint(csv, Need.DATA));
        assertEquals("unmarshal it first with the data format of the payload (such as unmarshal: json, unmarshal:"
                     + " jacksonXml or unmarshal: csv) to read its fields",
                BodyFacts.hint(exchange("sku=A1"), Need.DATA));
        assertTrue(BodyFacts.hint(exchange(null), Need.DATA).startsWith("read the data first"));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("sku", "A1");
        line.put("qty", 2);
        assertEquals("its keys are: sku, qty", BodyFacts.hint(exchange(line), Need.DATA));
        assertEquals("it is empty", BodyFacts.hint(exchange(new LinkedHashMap<>()), Need.DATA));
        assertTrue(BodyFacts.hint(exchange(List.of(1).iterator()), Need.DATA).startsWith("it has no size"));
        // a wrong name on a List or an object is not a body type mistake
        assertNull(BodyFacts.hint(exchange(new ArrayList<>()), Need.DATA));
        assertNull(BodyFacts.hint(exchange(new Object()), Need.DATA));
    }

    @Test
    public void testBytesHints() {
        Map<String, Object> order = new LinkedHashMap<>(Map.of("orderId", "1001"));
        assertEquals("marshal it first (marshal: jacksonXml, for the Content-Type application/xml), or set the body to"
                     + " the text",
                BodyFacts.hint(exchange(order), Need.BYTES, "application/xml"));
        assertEquals("marshal it first (marshal: csv, for the file name lines.csv), or set the body to the text",
                BodyFacts.hint(exchange(new ArrayList<>()), Need.BYTES, "lines.csv"));
        assertEquals("marshal it first with the data format to write (such as marshal: json, marshal: jacksonXml or"
                     + " marshal: csv), or set the body to the text",
                BodyFacts.hint(exchange(order), Need.BYTES, null));
        assertEquals("split it, or convert it first", BodyFacts.hint(exchange(List.of(1).iterator()), Need.BYTES, null));
        // text, bytes and no body at all are not why a write fails
        assertNull(BodyFacts.hint(exchange("text"), Need.BYTES, "application/json"));
        assertNull(BodyFacts.hint(exchange(null), Need.BYTES, "application/json"));
    }

    @Test
    public void testABodyHints() {
        assertTrue(BodyFacts.hint(exchange(null), Need.A_BODY).startsWith("read the data first - setBody with constant:"));
        assertTrue(BodyFacts.hint(exchange(""), Need.A_BODY).startsWith("read the data first"));
        assertNull(BodyFacts.hint(exchange("x"), Need.A_BODY));
    }

    @Test
    public void testTheKindOfABody() {
        assertEquals(Kind.NULL, BodyFacts.kindOf(null));
        // the payload as it is, and a single value, which converts to text
        assertEquals(Kind.PAYLOAD, BodyFacts.kindOf("text"));
        assertEquals(Kind.PAYLOAD, BodyFacts.kindOf(new byte[] { 1 }));
        assertEquals(Kind.PAYLOAD, BodyFacts.kindOf(new ByteArrayInputStream(new byte[0])));
        assertEquals(Kind.PAYLOAD, BodyFacts.kindOf(42));
        assertEquals(Kind.PAYLOAD, BodyFacts.kindOf(Boolean.TRUE));
        // parsed data: fields to read, and a marshal to write it
        assertEquals(Kind.DATA, BodyFacts.kindOf(new LinkedHashMap<>(Map.of("id", 1))));
        // a collection of objects: elements, and something a split walks
        assertEquals(Kind.COLLECTION, BodyFacts.kindOf(new ArrayList<>(List.of(1, 2))));
        assertEquals(Kind.COLLECTION, BodyFacts.kindOf(new String[] { "a" }));
        assertEquals(Kind.DATA, BodyFacts.kindOf(new Object()));
        assertEquals(Kind.COLLECTION, BodyFacts.kindOf(List.of(1, 2).iterator()));
    }

    @Test
    public void testAMapThatIsIterableIsData() {
        // such as the response of solr (a SimpleOrderedMap), which describe calls a Map too
        IterableMap map = new IterableMap();
        map.put("id", 1);

        assertEquals(Kind.DATA, BodyFacts.kindOf(map));
        assertTrue(BodyFacts.describe(exchange(map)).contains("a Map (IterableMap)"), BodyFacts.describe(exchange(map)));
    }

    private static final class IterableMap extends LinkedHashMap<String, Object>
            implements Iterable<Map.Entry<String, Object>> {
        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return entrySet().iterator();
        }
    }

    @Test
    public void testTheDataFormatOfAContentType() {
        assertEquals("json", BodyFacts.dataFormatOf("application/json; charset=UTF-8"));
        assertEquals("json", BodyFacts.dataFormatOf("application/problem+json"));
        assertEquals("jacksonXml", BodyFacts.dataFormatOf("text/xml"));
        assertEquals("csv", BodyFacts.dataFormatOf("text/tab-separated-values"));
        assertEquals("yaml", BodyFacts.dataFormatOf("application/x-yaml"));
        assertNull(BodyFacts.dataFormatOf("text/plain"));
        assertNull(BodyFacts.dataFormatOf(null));
    }

    /** Writes the body as its text. */
    private static final class TextDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            stream.write(String.valueOf(graph).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) {
            throw new UnsupportedOperationException();
        }
    }

    private Exchange exchange(Object body) {
        Exchange answer = new DefaultExchange(context);
        answer.getMessage().setBody(body);
        return answer;
    }
}
