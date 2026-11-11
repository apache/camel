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
package org.apache.camel.dataformat.csv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * A List of Maps without a configured header: every key must stay in one column, whatever the iteration order of each
 * row map, and a key missing in a row must not shift the following values into the wrong column. Each body has its own
 * columns.
 */
class CsvMarshalMapRowsColumnOrderTest extends CamelTestSupport {
    @EndpointInject("mock:output")
    MockEndpoint output;

    @Test
    void rowsWithTheSameKeysInAnotherOrderStayAligned() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:default", List.of(
                row("id", "1", "name", "Alice", "city", "Oslo"),
                row("name", "Bob", "city", "Rome", "id", "2")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,Alice,Oslo", "2,Bob,Rome" }, readOutputLines(0));
    }

    @Test
    void rowWithAMissingKeyDoesNotShiftTheFollowingColumns() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:default", List.of(
                row("id", "1", "name", "Alice", "city", "Oslo"),
                row("id", "2", "city", "Rome")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,Alice,Oslo", "2,,Rome" }, readOutputLines(0));
    }

    @Test
    void rowWithAMissingKeyGetsTheNullString() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:nullString", List.of(
                row("id", "1", "name", "Alice", "city", "Oslo"),
                row("id", "2", "city", "Rome")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,Alice,Oslo", "2,NULL,Rome" }, readOutputLines(0));
    }

    @Test
    void keyThatTheFirstRowDoesNotHaveGetsTheNextColumn() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:default", List.of(
                row("id", "1", "name", "Alice"),
                row("city", "Rome", "id", "2"),
                row("name", "Carol", "city", "Bergen", "id", "3")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,Alice", "2,,Rome", "3,Carol,Bergen" }, readOutputLines(0));
    }

    @Test
    void rowsOfDifferentRecordTypesGetTheColumnsOfAllRows() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:default", List.of(
                row("type", "H", "date", "20261028"),
                row("type", "D", "sku", "A1", "qty", "2"),
                row("type", "T", "count", "1")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "H,20261028", "D,,A1,2", "T,,,,1" }, readOutputLines(0));
    }

    @Test
    void listRowsOfDifferentRecordTypesKeepTheirOwnFields() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:default", List.of(
                List.of("H", "20261028"),
                List.of("D", "A1", "2"),
                List.of("T", "1")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "H,20261028", "D,A1,2", "T,1" }, readOutputLines(0));
    }

    @Test
    void nullKeyOfAnEarlierRowWithAMapThatDoesNotPermitNullKeys() throws Exception {
        output.expectedMessageCount(1);

        Map<String, Object> withNullKey = new LinkedHashMap<>();
        withNullKey.put("id", "1");
        withNullKey.put(null, "x");
        // Map.of(...) throws a NullPointerException on get(null)
        template.sendBody("direct:default", List.of(withNullKey, Map.of("id", "2")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,x", "2," }, readOutputLines(0));
    }

    @Test
    void keyOfAnotherTypeOfAnEarlierRowWithASortedMap() throws Exception {
        output.expectedMessageCount(1);

        Map<Object, Object> first = new LinkedHashMap<>();
        first.put(1, "x");
        first.put("id", "1");
        // a TreeMap of String keys throws a ClassCastException on get(1)
        Map<Object, Object> sorted = new TreeMap<>(Map.of("id", "2"));
        template.sendBody("direct:default", List.of(first, sorted));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "x,1", ",2" }, readOutputLines(0));
    }

    @Test
    void eachBodyHasItsOwnColumns() throws Exception {
        output.expectedMessageCount(2);

        template.sendBody("direct:default", List.of(row("id", "1", "name", "Alice")));
        template.sendBody("direct:default", List.of(row("city", "Rome", "id", "2")));
        output.assertIsSatisfied();

        assertArrayEquals(new String[] { "1,Alice" }, readOutputLines(0));
        assertArrayEquals(new String[] { "Rome,2" }, readOutputLines(1));
    }

    private static Map<String, Object> row(String... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default")
                        .marshal(new CsvDataFormat())
                        .to("mock:output");

                from("direct:nullString")
                        .marshal(new CsvDataFormat().setNullString("NULL"))
                        .to("mock:output");
            }
        };
    }

    private String[] readOutputLines(int index) {
        return output.getExchanges().get(index).getIn().getBody(String.class).split("\r\n|\r|\n");
    }
}
