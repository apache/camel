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
package org.apache.camel.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemoveHeadersAllTest extends ContextTestSupport {

    @Test
    public void testRemoveHeadersAll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("Hello World");

        Map<String, Object> headers = new HashMap<>();
        headers.put("dudeCool", "cool");
        headers.put("DudeWicket", "wicket");
        headers.put("DUDEbig", "upper");
        headers.put("duck", "Donald");
        headers.put("foo", "bar");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();

        assertEquals(0, mock.getReceivedExchanges().get(0).getIn().getHeaders().size());
    }

    @Test
    public void testRemoveHeadersAllExclude() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("duck", "Donald");

        Map<String, Object> headers = new HashMap<>();
        headers.put("dudeCool", "cool");
        headers.put("DudeWicket", "wicket");
        headers.put("DUDEbig", "upper");
        headers.put("duck", "Donald");
        headers.put("foo", "bar");

        template.sendBodyAndHeaders("direct:startWithExclude", "Hello World", headers);

        assertMockEndpointsSatisfied();

        assertEquals(1, mock.getReceivedExchanges().get(0).getIn().getHeaders().size());
    }

    @Test
    public void testRemoveHeadersAllExcludeNoMatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("Hello World");

        Map<String, Object> headers = new HashMap<>();
        headers.put("dudeCool", "cool");
        headers.put("DudeWicket", "wicket");
        headers.put("DUDEbig", "upper");
        headers.put("duck", "Donald");
        headers.put("foo", "bar");

        template.sendBodyAndHeaders("direct:startWithExcludeNoMatch", "Hello World", headers);

        assertMockEndpointsSatisfied();

        assertEquals(0, mock.getReceivedExchanges().get(0).getIn().getHeaders().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .removeHeaders("*")
                        .to("mock:end");

                from("direct:startWithExclude")
                        .removeHeaders("*", "duck")
                        .to("mock:end");

                from("direct:startWithExcludeNoMatch")
                        .removeHeaders("*", "dog")
                        .to("mock:end");
            }
        };
    }
}
