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
package org.apache.camel.dataformat.bindy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Marshal of a body that is neither an object of the model class, a List of them, nor a Map of the model class names to
 * the objects fails, instead of writing an empty line.
 */
public class BindyMarshalNotModelBodyTest extends CamelTestSupport {

    // bindyCsv

    @Test
    public void csvMarshalsModelObject() {
        String out = template.requestBody("direct:csv", new CsvItem("1", "Camel"), String.class);
        assertEquals(List.of("1,Camel"), out.lines().toList());
    }

    @Test
    public void csvMarshalsListOfModelObjects() {
        String out = template.requestBody("direct:csv", List.of(new CsvItem("1", "Camel"), new CsvItem("2", "Bindy")),
                String.class);
        assertEquals(List.of("1,Camel", "2,Bindy"), out.lines().toList());
    }

    @Test
    public void csvMarshalsMapOfModelClassNames() {
        String out = template.requestBody("direct:csv", Map.of(CsvItem.class.getName(), new CsvItem("1", "Camel")),
                String.class);
        assertEquals(List.of("1,Camel"), out.lines().toList());
    }

    @Test
    public void csvFailsForMapOfFieldValues() {
        assertInvalidPayload("direct:csv", fields(), "[id, name]");
    }

    @Test
    public void csvFailsForString() {
        assertInvalidPayload("direct:csv", "1,Camel", "[java.lang.String]");
    }

    @Test
    public void csvFailsForListElementThatIsNotModelObject() {
        assertInvalidPayload("direct:csv", List.of(new CsvItem("1", "Camel"), "2,Bindy"), "[java.lang.String]");
    }

    // bindyFixed

    @Test
    public void fixedMarshalsModelObject() {
        String out = template.requestBody("direct:fixed", new FixedItem("01", "Camel"), String.class);
        assertEquals(List.of("01Camel"), out.lines().toList());
    }

    @Test
    public void fixedMarshalsMapOfModelClassNames() {
        String out = template.requestBody("direct:fixed", Map.of(FixedItem.class.getName(), new FixedItem("01", "Camel")),
                String.class);
        assertEquals(List.of("01Camel"), out.lines().toList());
    }

    @Test
    public void fixedMarshalsListOfMapOfModelClassNames() {
        String out = template.requestBody("direct:fixed",
                List.of(Map.of(FixedItem.class.getName(), new FixedItem("01", "Camel"))), String.class);
        assertEquals(List.of("01Camel"), out.lines().toList());
    }

    @Test
    public void fixedFailsForMapOfFieldValues() {
        assertInvalidPayload("direct:fixed", fields(), "[id, name]");
    }

    @Test
    public void fixedFailsForString() {
        assertInvalidPayload("direct:fixed", "01Camel", "[java.lang.String]");
    }

    @Test
    public void fixedFailsForListOfMapsOfFieldValues() {
        // looks like a prepared list of rows, as the first map has one String key
        assertInvalidPayload("direct:fixed", List.of(Map.of("id", "01")), "[id]");
    }

    // bindyKvp

    @Test
    public void kvpMarshalsModelObject() {
        String out = template.requestBody("direct:kvp", new KvpItem("1", "Camel"), String.class);
        // the pair separator ends every pair
        assertEquals(List.of("1=1 2=Camel "), out.lines().toList());
    }

    @Test
    public void kvpFailsForMapOfFieldValues() {
        assertInvalidPayload("direct:kvp", fields(), "[id, name]");
    }

    @Test
    public void kvpFailsForString() {
        assertInvalidPayload("direct:kvp", "1=1 2=Camel", "[java.lang.String]");
    }

    private void assertInvalidPayload(String uri, Object body, String keys) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(uri, body, String.class));
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertTrue(cause.getMessage().contains("but a row has the keys: " + keys), cause.getMessage());
    }

    private static Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", "1");
        map.put("name", "Camel");
        return map;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:csv").marshal(new BindyCsvDataFormat(CsvItem.class));
                from("direct:fixed").marshal(new BindyFixedLengthDataFormat(FixedItem.class));
                from("direct:kvp").marshal(new BindyKeyValuePairDataFormat(KvpItem.class));
            }
        };
    }

    @CsvRecord(separator = ",")
    public static class CsvItem {
        @DataField(pos = 1)
        private String id;
        @DataField(pos = 2)
        private String name;

        public CsvItem() {
        }

        public CsvItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @FixedLengthRecord(length = 7)
    public static class FixedItem {
        @DataField(pos = 1, length = 2)
        private String id;
        @DataField(pos = 3, length = 5)
        private String name;

        public FixedItem() {
        }

        public FixedItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Message(pairSeparator = " ", keyValuePairSeparator = "=")
    public static class KvpItem {
        @KeyValuePairField(tag = 1)
        private String id;
        @KeyValuePairField(tag = 2)
        private String name;

        public KvpItem() {
        }

        public KvpItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
