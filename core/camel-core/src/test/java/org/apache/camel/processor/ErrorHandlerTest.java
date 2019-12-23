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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Error Handler unit test
 */
public class ErrorHandlerTest extends ContextTestSupport {

    @Test
    public void testNoError() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        MockEndpoint result = getMockEndpoint("mock:result");
        error.expectedMessageCount(0);
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testError() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        MockEndpoint result = getMockEndpoint("mock:result");
        error.expectedMessageCount(1);
        // we exepect the orignal input when moved to the DLC queue
        error.expectedBodiesReceived("Boom");
        result.expectedMessageCount(0);

        template.sendBody("direct:start", "Boom");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(1).redeliveryDelay(0)).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Boom".equals(body)) {
                            throw new IllegalArgumentException("Forced exception by unit test");
                        }
                        exchange.getIn().setBody("Bye World");
                    }
                }).to("mock:result");
            }
        };
    }
}
