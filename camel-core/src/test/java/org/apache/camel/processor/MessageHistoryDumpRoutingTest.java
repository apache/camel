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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class MessageHistoryDumpRoutingTest extends ContextTestSupport {

    private String body = "Hello World 1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";

    public void testReduceStacksNeeded() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedBodiesReceived(body);

        template.sendBody("seda:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setMessageHistory(true);
                // to test that the message history exchange gets clipped
                context.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "100");

                from("seda:start")
                        .to("log:foo")
                        .to("direct:bar")
                        .delay(300)
                        .to("log:baz")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new IllegalArgumentException("Forced to dump message history");
                            }
                        })
                        .to("mock:result");

                from("direct:bar")
                    .to("log:bar")
                    .delay(100)
                    .to("mock:bar");
            }
        };
    }
}
