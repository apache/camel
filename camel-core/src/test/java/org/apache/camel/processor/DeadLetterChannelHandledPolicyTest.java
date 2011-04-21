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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * DeadLetterChannel now also have handled policy, like onException.
 *
 * @version 
 */
public class DeadLetterChannelHandledPolicyTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0).logStackTrace(false));

                from("direct:start").process(new MyThrowExceptionProcessor());
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        // as its handled no exception is thrown to the client
        assertMockEndpointsSatisfied();
    }

    public void testNotHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).redeliveryDelay(0).logStackTrace(false).handled(false));

                from("direct:start").process(new MyThrowExceptionProcessor());
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedBodiesReceived("Hello World");

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // as its NOT handled the exception should be thrown back to the client
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    // for spring unit testing
    public static class MyThrowExceptionProcessor implements Processor {

        public MyThrowExceptionProcessor() {
        }

        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException("Forced");
        }
    }

}
