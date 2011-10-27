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

import java.io.IOException;
import java.net.ConnectException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ErrorHandlerOnExceptionRedeliveryAndHandledTest extends ContextTestSupport {

    private static String counter = "";

    public void testRedeliveryCounterIsResetWhenHandled() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            // we tried to handle that exception but then another exception occurred
            // so this exchange failed with an exception
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            ConnectException cause = assertIsInstanceOf(ConnectException.class, e.getCause());
            assertEquals("Cannot connect to bar server", cause.getMessage());
        }

        assertMockEndpointsSatisfied();

        assertEquals("123", counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(5).redeliveryDelay(0));

                onException(IOException.class).maximumRedeliveries(3).handled(true)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            if (exchange.getIn().getHeader(Exchange.REDELIVERED) != null) {
                                String s = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, String.class);
                                counter += s;
                            }
                            // we throw an exception here, but the default error handler should not kick in
                            throw new ConnectException("Cannot connect to bar server");
                        }
                    })
                    .to("mock:other");

                from("direct:start")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                if (exchange.getIn().getHeader(Exchange.REDELIVERED) != null) {
                                    String s = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, String.class);
                                    counter += s;
                                }
                                throw new ConnectException("Cannot connect to foo server");
                            }
                        })
                    .to("mock:result");
            }
        };
    }
}
