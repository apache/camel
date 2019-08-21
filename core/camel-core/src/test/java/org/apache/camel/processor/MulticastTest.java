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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class MulticastTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Test
    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        x.expectedBodiesReceived("input+output");
        y.expectedBodiesReceived("input+output");
        z.expectedBodiesReceived("input+output");

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("input");
                in.setHeader("foo", "bar");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new AppendingProcessor();

        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").multicast().to("direct:x", "direct:y", "direct:z");
                // END SNIPPET: example

                from("direct:x").process(processor).to("mock:x");
                from("direct:y").process(processor).to("mock:y");
                from("direct:z").process(processor).to("mock:z");
            }
        };

    }

}
