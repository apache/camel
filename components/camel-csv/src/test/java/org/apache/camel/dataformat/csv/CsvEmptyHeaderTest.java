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
 * An empty or blank header is handled like no header: the columns of a Map row are its keys, and no header line is
 * written.
 */
class CsvEmptyHeaderTest extends CamelTestSupport {
    @EndpointInject("mock:output")
    MockEndpoint output;

    @Test
    void emptyHeaderMarshalsTheMapValues() throws Exception {
        assertEquals("abc,123\r\n", marshal("direct:empty"));
    }

    @Test
    void emptyHeaderOnTheUriMarshalsTheMapValues() throws Exception {
        assertEquals("abc,123\r\n", marshal("direct:emptyUri"));
    }

    @Test
    void blankHeaderMarshalsTheMapValues() throws Exception {
        assertEquals("abc,123\r\n", marshal("direct:blank"));
    }

    @Test
    void emptyHeaderUnmarshalsTheFirstLineAsHeader() throws Exception {
        output.expectedMessageCount(1);

        template.sendBody("direct:emptyUnmarshal", "foo,bar\r\nabc,123\r\n");
        output.assertIsSatisfied();

        assertEquals(List.of(Map.of("foo", "abc", "bar", "123")), output.getExchanges().get(0).getIn().getBody(List.class));
    }

    private String marshal(String uri) throws Exception {
        output.expectedMessageCount(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foo", "abc");
        row.put("bar", 123);
        template.sendBody(uri, row);
        output.assertIsSatisfied();

        return output.getExchanges().get(0).getIn().getBody(String.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:empty")
                        .marshal(new CsvDataFormat().setHeader(""))
                        .to("mock:output");

                from("direct:emptyUri")
                        .to("dataformat:csv:marshal?header=")
                        .to("mock:output");

                from("direct:blank")
                        .marshal(new CsvDataFormat().setHeader(" "))
                        .to("mock:output");

                from("direct:emptyUnmarshal")
                        .unmarshal(new CsvDataFormat().setHeader("").setUseMaps(true))
                        .to("mock:output");
            }
        };
    }
}
