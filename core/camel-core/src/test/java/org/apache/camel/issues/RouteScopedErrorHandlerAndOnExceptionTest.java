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
package org.apache.camel.issues;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 * Based on user forum issue
 */
public class RouteScopedErrorHandlerAndOnExceptionTest extends ContextTestSupport {

    @Test
    public void testOnException() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:*").skipSendToOriginalEndpoint().throwException(new ConnectException("Forced"));
            }
        });

        getMockEndpoint("mock:local").expectedMessageCount(0);
        getMockEndpoint("mock:seda").expectedMessageCount(0);
        // we fail all redeliveries so after that we send to mock:exhausted
        getMockEndpoint("mock:exhausted").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            ConnectException cause = assertIsInstanceOf(ConnectException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testErrorHandler() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:*").skipSendToOriginalEndpoint().throwException(new FileNotFoundException("Forced"));
            }
        });

        getMockEndpoint("mock:local").expectedMessageCount(1);
        getMockEndpoint("mock:seda").expectedMessageCount(0);
        getMockEndpoint("mock:exhausted").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start").errorHandler(deadLetterChannel("mock:local").maximumRedeliveries(2).redeliveryDelay(0))
                    // no redelivery delay for faster unit tests
                    .onException(ConnectException.class).maximumRedeliveries(5).redeliveryDelay(0).logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.WARN)
                    // send to mock when we are exhausted
                    .to("mock:exhausted").end().to("seda:foo");
            }
        };
    }
}
