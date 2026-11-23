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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With headerFromMapKeys, marshalling a Map or a List of Maps without a configured header writes a header line with the
 * keys of the first Map (CAMEL-27371), such as for JSON rows turned into CSV.
 */
class CsvMarshalHeaderFromMapKeysTest extends CamelTestSupport {
    @EndpointInject("mock:output")
    MockEndpoint output;

    @Test
    void listOfMapsGetsAHeaderLineFromTheKeysOfTheFirstMap() throws Exception {
        assertEquals("id,name\r\n1,Alice\r\n2,Bob\r\n",
                marshal("direct:keys", List.of(row("id", "1", "name", "Alice"), row("name", "Bob", "id", "2"))));
    }

    @Test
    void keysOfTheFirstMapAreTheColumns() throws Exception {
        // a missing key gives an empty field, a key that only a later row has is not written, as with the header option
        assertEquals("id,name\r\n1,Alice\r\n2,\r\n3,Carol\r\n",
                marshal("direct:keys", List.of(row("id", "1", "name", "Alice"), row("id", "2", "city", "Rome"),
                        row("city", "Bergen", "name", "Carol", "id", "3"))));
    }

    @Test
    void singleMapGetsAHeaderLine() throws Exception {
        assertEquals("id,name\r\n1,Alice\r\n", marshal("direct:keys", row("id", "1", "name", "Alice")));
    }

    @Test
    void iteratorOfMapsGetsAHeaderLine() throws Exception {
        Iterator<Map<String, Object>> rows = List.of(row("id", "1"), row("id", "2")).iterator();
        assertEquals("id\r\n1\r\n2\r\n", marshal("direct:keys", rows));
    }

    @Test
    void keysThatAreNotStrings() throws Exception {
        Map<Object, Object> first = new LinkedHashMap<>();
        first.put(1, "a");
        first.put(2, "b");
        Map<Object, Object> second = new LinkedHashMap<>();
        second.put(2, "d");
        second.put(1, "c");
        assertEquals("1,2\r\na,b\r\nc,d\r\n", marshal("direct:keys", List.of(first, second)));
    }

    @Test
    void listRowsGetNoHeaderLine() throws Exception {
        assertEquals("1,Alice\r\n2,Bob\r\n", marshal("direct:keys", List.of(List.of("1", "Alice"), List.of("2", "Bob"))));
    }

    @Test
    void configuredHeaderWins() throws Exception {
        assertEquals("name\r\nAlice\r\n", marshal("direct:keysAndHeader", List.of(row("id", "1", "name", "Alice"))));
    }

    @Test
    void skipHeaderRecordWritesNoHeaderLine() throws Exception {
        assertEquals("1,Alice\r\n", marshal("direct:keysSkipHeader", List.of(row("id", "1", "name", "Alice"))));
    }

    @Test
    void withoutTheOptionNoHeaderLine() throws Exception {
        assertEquals("1,Alice\r\n", marshal("direct:default", List.of(row("id", "1", "name", "Alice"))));
    }

    @Test
    void optionOfTheModel() throws Exception {
        assertEquals("id,name\r\n1,Alice\r\n", marshal("direct:model", List.of(row("id", "1", "name", "Alice"))));
    }

    @Test
    void optionOnTheUri() throws Exception {
        assertEquals("id,name\r\n1,Alice\r\n", marshal("direct:uri", List.of(row("id", "1", "name", "Alice"))));
    }

    private String marshal(String uri, Object body) throws Exception {
        output.reset();
        output.expectedMessageCount(1);
        template.sendBody(uri, body);
        output.assertIsSatisfied();
        return output.getExchanges().get(0).getIn().getBody(String.class);
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
                from("direct:keys")
                        .marshal(new CsvDataFormat().setHeaderFromMapKeys(true))
                        .to("mock:output");

                from("direct:keysAndHeader")
                        .marshal(new CsvDataFormat().setHeaderFromMapKeys(true).setHeader("name"))
                        .to("mock:output");

                from("direct:keysSkipHeader")
                        .marshal(new CsvDataFormat().setHeaderFromMapKeys(true).setSkipHeaderRecord(true))
                        .to("mock:output");

                from("direct:default")
                        .marshal(new CsvDataFormat())
                        .to("mock:output");

                from("direct:model")
                        .marshal(dataFormat().csv().headerFromMapKeys(true).end())
                        .to("mock:output");

                from("direct:uri")
                        .to("dataformat:csv:marshal?headerFromMapKeys=true")
                        .to("mock:output");
            }
        };
    }
}
