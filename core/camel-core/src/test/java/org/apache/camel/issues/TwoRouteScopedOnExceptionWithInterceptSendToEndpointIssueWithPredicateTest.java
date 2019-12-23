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

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

/**
 * Based on user forum issue
 */
public class TwoRouteScopedOnExceptionWithInterceptSendToEndpointIssueWithPredicateTest extends ContextTestSupport {

    private final AtomicInteger invoked = new AtomicInteger();

    @Test
    public void testIssue() throws Exception {
        final Predicate fail = PredicateBuilder.or(header(Exchange.REDELIVERY_COUNTER).isNull(), header(Exchange.REDELIVERY_COUNTER).isLessThan(5));

        RouteDefinition route = context.getRouteDefinitions().get(0);
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:*").skipSendToOriginalEndpoint().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        invoked.incrementAndGet();

                        if (fail.matches(exchange)) {
                            throw new ConnectException("Forced");
                        }
                    }
                }).to("mock:ok");
            }
        });

        getMockEndpoint("mock:global").expectedMessageCount(0);
        getMockEndpoint("mock:ok").expectedMessageCount(1);
        getMockEndpoint("mock:exhausted").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // 5 retry + 1 ok
        assertEquals(6, invoked.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:global").maximumRedeliveries(2).redeliveryDelay(500));

                from("direct:start")
                    // no redelivery delay for faster unit tests
                    .onException(ConnectException.class).maximumRedeliveries(5).redeliveryDelay(0).logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.WARN)
                    // send to mock when we are exhausted
                    .to("mock:exhausted").end().to("seda:foo");

                from("direct:start2")
                    // no redelivery delay for faster unit tests
                    .onException(ConnectException.class).maximumRedeliveries(3).redeliveryDelay(0).logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.ERROR)
                    // send to mock when we are exhausted
                    .to("mock:exhausted2").end().to("seda:foo2");
            }
        };
    }
}
