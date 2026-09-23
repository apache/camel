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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When the exception changes between redelivery attempts, the exception policy (onException) for the current exception
 * is used, and not the one matched by a previous attempt (CAMEL-24981).
 */
public class OnExceptionChangedExceptionOnRedeliveryTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        attempts.set(0);
        super.setUp();
    }

    @Test
    public void testNoPolicyForNewExceptionGoesToDeadLetter() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:iae").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT)
                .isInstanceOf(IllegalStateException.class);

        template.sendBody("direct:dlc", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(2, attempts.get());
    }

    @Test
    public void testNoPolicyForNewExceptionIsNotHandled() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:default", "Hello"));
        assertInstanceOf(IllegalStateException.class, e.getCause());

        assertMockEndpointsSatisfied();
        assertEquals(2, attempts.get());
    }

    @Test
    public void testPolicyForNewExceptionIsUsed() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:iae").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:iae", "Hello");

        assertMockEndpointsSatisfied();
        // 1 attempt with IOException, then 2 more as the IllegalArgumentException policy allows 2 redeliveries
        assertEquals(3, attempts.get());
    }

    @Test
    public void testSameExceptionKeepsPolicy() throws Exception {
        getMockEndpoint("mock:io").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:same", "Hello");

        assertMockEndpointsSatisfied();
        // 1 attempt and 1 redelivery
        assertEquals(2, attempts.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                onException(IOException.class).maximumRedeliveries(1).redeliveryDelay(0).handled(true).to("mock:io");
                onException(IllegalArgumentException.class).maximumRedeliveries(2).redeliveryDelay(0).handled(true)
                        .to("mock:iae");

                from("direct:dlc").process(e -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IOException("Forced");
                    }
                    throw new IllegalStateException("No policy for this");
                });

                from("direct:iae").process(e -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IOException("Forced");
                    }
                    throw new IllegalArgumentException("Has its own policy");
                });

                from("direct:same").process(e -> {
                    attempts.incrementAndGet();
                    throw new IOException("Forced");
                });

                from("direct:default").errorHandler(defaultErrorHandler())
                        .onException(IOException.class).maximumRedeliveries(1).redeliveryDelay(0).handled(true)
                        .to("mock:io").end()
                        .process(e -> {
                            if (attempts.getAndIncrement() == 0) {
                                throw new IOException("Forced");
                            }
                            throw new IllegalStateException("No policy for this");
                        });
            }
        };
    }
}
