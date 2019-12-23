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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 * Based on user form issue
 */
public class ErrorHandlerAdviceIssueTest extends ContextTestSupport {

    @Test
    public void testErrorHandlerAdvice() throws Exception {
        RouteDefinition foo = context.getRouteDefinition("foo");
        RouteReifier.adviceWith(foo, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:*").skipSendToOriginalEndpoint().throwException(new IllegalAccessException("Forced"));
            }
        });

        RouteDefinition error = context.getRouteDefinition("error");
        RouteReifier.adviceWith(error, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("file:*").skipSendToOriginalEndpoint().to("mock:file");
            }
        });

        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:file").expectedMessageCount(1);
        // should be intercepted
        getMockEndpoint("mock:foo").expectedMessageCount(0);

        context.getRouteController().stopRoute("timer");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("direct:error").maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:error").routeId("error").errorHandler(deadLetterChannel("log:dead?level=ERROR")).to("mock:error").to("file:error");

                from("timer://someTimer?delay=15000&fixedRate=true&period=5000").routeId("timer").to("log:level=INFO");

                from("direct:start").routeId("foo").to("seda:foo");

                from("seda:foo").to("mock:foo");

            }
        };
    }
}
