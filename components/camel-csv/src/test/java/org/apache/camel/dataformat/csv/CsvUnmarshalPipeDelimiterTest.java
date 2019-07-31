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

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Spring based integration test for the <code>CsvDataFormat</code>
 */
public class CsvUnmarshalPipeDelimiterTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @SuppressWarnings("unchecked")
    @Test
    public void testCsvUnMarshal() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", "123|Camel in Action|1\n124|ActiveMQ in Action|2");

        assertMockEndpointsSatisfied();

        List<List<String>> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, body.size());
        assertEquals("123", body.get(0).get(0));
        assertEquals("Camel in Action", body.get(0).get(1));
        assertEquals("1", body.get(0).get(2));
        assertEquals("124", body.get(1).get(0));
        assertEquals("ActiveMQ in Action", body.get(1).get(1));
        assertEquals("2", body.get(1).get(2));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCsvUnMarshalSingleLine() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", "123|Camel in Action|1");

        assertMockEndpointsSatisfied();

        List<List<String>> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(1, body.size());
        assertEquals("123", body.get(0).get(0));
        assertEquals("Camel in Action", body.get(0).get(1));
        assertEquals("1", body.get(0).get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CsvDataFormat csv = new CsvDataFormat().setDelimiter('|');

                from("direct:start").unmarshal(csv)
                        .to("mock:result");
            }
        };
    }
}