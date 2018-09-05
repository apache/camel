/**
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
package org.apache.camel.component.restlet;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RecipientListInflightExchangesTest extends RestletTestSupport {

    @Test
    public void testRecipientListWithBean() throws Exception {
        // there should be 0 inflight when we start
        assertEquals(0, context.getInflightRepository().size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("RS-response", "RS-response");

        String out = template.requestBody("direct:start", "theBody", String.class);
        assertEquals("RS-response", out);

        //invoke twice
        out = template.requestBody("direct:start", "theBody", String.class);
        assertEquals("RS-response", out);

        assertMockEndpointsSatisfied();

        // and there should be 0 inflight when we are finished
        assertEquals(0, context.getInflightRepository().size());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.setStreamCaching(true);

                // turn off error handler
                errorHandler(noErrorHandler());

                //route under test
                from("direct:start").routeId("test1")
                        .process(new MyInflightCheckBean())
                        .recipientList().simple("restlet:http://localhost:" + portNum + "/users/123/basic?synchronous=true")
                        .to("mock:result");

                // restlet "Server" side
                from("restlet:http://localhost:" + portNum + "/users/{id}/basic")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody("RS-response");
                            }
                        });
            }
        };
    }

    public class MyInflightCheckBean implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
             // there should only be one exchange in flight at the time this bean is invoked.
            assertEquals("Should not be more than 1 exchanges inflight", 1, exchange.getContext().getInflightRepository().size());
        }
    }

}