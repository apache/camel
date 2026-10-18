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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.BodyFacts.Need;
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
    public void testTheBodyInPlainWords() {
        assertEquals("null", BodyFacts.words(null));
        assertEquals("text (a String)", BodyFacts.words("{\"sku\": \"A1\"}"));
        assertEquals("empty text (a String)", BodyFacts.words(" "));
        assertEquals("bytes (a byte[])", BodyFacts.words(new byte[] { 1 }));
        assertEquals("a stream (ByteArrayInputStream)", BodyFacts.words(new ByteArrayInputStream(new byte[0])));
        assertEquals("a Map (LinkedHashMap)", BodyFacts.words(new LinkedHashMap<>()));
        assertEquals("a List (ArrayList)", BodyFacts.words(new ArrayList<>()));
        assertTrue(BodyFacts.words(List.of(1).iterator()).startsWith("an iterator ("));
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
    public void testTheDataFormatOfAContentType() {
        assertEquals("json", BodyFacts.dataFormatOf("application/json; charset=UTF-8"));
        assertEquals("json", BodyFacts.dataFormatOf("application/problem+json"));
        assertEquals("jacksonXml", BodyFacts.dataFormatOf("text/xml"));
        assertEquals("csv", BodyFacts.dataFormatOf("text/tab-separated-values"));
        assertEquals("yaml", BodyFacts.dataFormatOf("application/x-yaml"));
        assertNull(BodyFacts.dataFormatOf("text/plain"));
        assertNull(BodyFacts.dataFormatOf(null));
    }

    private Exchange exchange(Object body) {
        Exchange answer = new DefaultExchange(context);
        answer.getMessage().setBody(body);
        return answer;
    }
}
