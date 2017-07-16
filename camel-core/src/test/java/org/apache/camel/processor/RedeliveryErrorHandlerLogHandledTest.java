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

import org.apache.camel.CamelException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class RedeliveryErrorHandlerLogHandledTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testRedeliveryErrorHandlerOnExceptionLogHandledDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .maximumRedeliveries(3)
                    .redeliveryDelay(0)
                    .handled(true)
                    .to("mock:handled");

                from("direct:foo")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerOnExceptionLogHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .maximumRedeliveries(3)
                    .redeliveryDelay(0)
                    .logHandled(true)
                    .handled(true)
                    .to("mock:handled");

                from("direct:foo")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerOnExceptionLogRetryAttempted() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .maximumRedeliveries(3)
                    .redeliveryDelay(0)
                    .logHandled(true)
                    .logRetryAttempted(true)
                    .handled(true)
                    .to("mock:handled");

                from("direct:foo")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerDoNotLogExhausted() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().logExhausted(false));

                from("direct:bar")
                    .throwException(new CamelException("Camel rocks"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedMessageCount(0);

        try {
            template.sendBody("direct:bar", "Hello World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelException cause = assertIsInstanceOf(CamelException.class, e.getCause());
            assertEquals("Camel rocks", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerLogExhaustedDefault() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler());

                from("direct:bar")
                    .throwException(new CamelException("Camel rocks"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedMessageCount(0);

        try {
            template.sendBody("direct:bar", "Hello World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelException cause = assertIsInstanceOf(CamelException.class, e.getCause());
            assertEquals("Camel rocks", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerAllOptions() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler()
                        .redeliveryDelay(0)
                        .maximumRedeliveries(3)
                        .logExhausted(true).logHandled(true).logRetryStackTrace(true).logStackTrace(true)
                        .retryAttemptedLogLevel(LoggingLevel.WARN).retriesExhaustedLogLevel(LoggingLevel.ERROR));

                from("direct:bar")
                    .throwException(new CamelException("Camel rocks"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedMessageCount(0);

        try {
            template.sendBody("direct:bar", "Hello World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelException cause = assertIsInstanceOf(CamelException.class, e.getCause());
            assertEquals("Camel rocks", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testRedeliveryErrorHandlerOnExceptionAllOptions() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .redeliveryDelay(0)
                    .maximumRedeliveries(3)
                    .logHandled(true)
                    .logRetryAttempted(true)
                    .logRetryStackTrace(true)
                    .logExhausted(true)
                    .logStackTrace(true)
                    .handled(true)
                    .retryAttemptedLogLevel(LoggingLevel.WARN)
                    .retriesExhaustedLogLevel(LoggingLevel.ERROR)
                    .to("mock:handled");

                from("direct:foo")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
