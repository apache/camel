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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test to verify that headers set before pollEnrich are preserved after pollEnrich operation.
 */
public class PollEnrichHeaderPropagationTest extends ContextTestSupport {

    @Test
    public void testHeadersPreservedAfterPollEnrich() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel from file");
        mock.expectedHeaderReceived("foo", "bar");
        mock.expectedHeaderReceived("customHeader", "customValue");

        // Create a file to be consumed by pollEnrich
        template.sendBodyAndHeader(fileUri(), "Hello Camel from file", Exchange.FILE_NAME, "file.txt");

        // Trigger the route
        template.sendBody("direct:start", "trigger");

        assertMockEndpointsSatisfied();

        // Verify headers are preserved
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals("bar", exchange.getIn().getHeader("foo"));
        assertEquals("customValue", exchange.getIn().getHeader("customHeader"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader("foo", constant("bar"))
                        .setHeader("customHeader", constant("customValue"))
                        .pollEnrich(fileUri("?fileName=file.txt"), 3000)
                        .convertBodyTo(String.class)
                        .log("${headers}")
                        .to("mock:result");
            }
        };
    }
}
