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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class RedeliveryOnExceptionBlockedDelayTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RedeliveryOnExceptionBlockedDelayTest.class);

    private static volatile int attempt;

    public void testRedelivery() throws Exception {
        MockEndpoint before = getMockEndpoint("mock:result");
        before.expectedBodiesReceived("Hello World", "Hello Camel");

        // we use blocked redelivery delay so the messages arrive in the same order
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World", "Hello Camel");

        template.sendBody("seda:start", "World");
        template.sendBody("seda:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // will by default block
                onException(IllegalArgumentException.class)
                    .maximumRedeliveries(5).redeliveryDelay(0);

                from("seda:start")
                    .to("log:before")
                    .to("mock:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            LOG.info("Processing at attempt {} {}", attempt, exchange);

                            String body = exchange.getIn().getBody(String.class);
                            if (body.contains("World")) {
                                if (++attempt <= 2) {
                                    LOG.info("Processing failed will thrown an exception");
                                    throw new IllegalArgumentException("Damn");
                                }
                            }

                            exchange.getIn().setBody("Hello " + body);
                            LOG.info("Processing at attempt {} complete {}", attempt, exchange);
                        }
                    })
                    .to("log:after")
                    .to("mock:result");
            }
        };
    }
}
