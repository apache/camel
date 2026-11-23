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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The names of the header option are trimmed (CAMEL-27371): "id, name" gives the columns id and name, not " name".
 */
class CsvHeaderNamesTrimTest extends CamelTestSupport {
    @EndpointInject("mock:output")
    MockEndpoint output;

    @Test
    void marshalFindsTheKeysOfTheTrimmedNames() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Alice");

        assertEquals("id,name\r\n1,Alice\r\n", send("direct:marshal", List.of(row), String.class));
    }

    @Test
    void marshalOnTheUriFindsTheKeysOfTheTrimmedNames() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Alice");

        assertEquals("id,name\r\n1,Alice\r\n", send("direct:marshalUri", List.of(row), String.class));
    }

    @Test
    void unmarshalGivesMapsWithTheTrimmedNames() throws Exception {
        assertEquals(List.of(Map.of("id", "1", "name", "Alice")), send("direct:unmarshal", "1,Alice\r\n", List.class));
    }

    @Test
    void singleNameIsTrimmed() throws Exception {
        assertEquals(List.of(Map.of("id", "1")), send("direct:unmarshalSingle", "1\r\n", List.class));
    }

    private <T> T send(String uri, Object body, Class<T> type) throws Exception {
        output.reset();
        output.expectedMessageCount(1);
        template.sendBody(uri, body);
        output.assertIsSatisfied();
        return output.getExchanges().get(0).getIn().getBody(type);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal")
                        .marshal(new CsvDataFormat().setHeader("id, name"))
                        .to("mock:output");

                from("direct:marshalUri")
                        .to("dataformat:csv:marshal?header=id, name ")
                        .to("mock:output");

                from("direct:unmarshal")
                        .unmarshal(new CsvDataFormat().setHeader(" id , name").setUseMaps(true))
                        .to("mock:output");

                from("direct:unmarshalSingle")
                        .unmarshal(new CsvDataFormat().setHeader(" id ").setUseMaps(true))
                        .to("mock:output");
            }
        };
    }
}
