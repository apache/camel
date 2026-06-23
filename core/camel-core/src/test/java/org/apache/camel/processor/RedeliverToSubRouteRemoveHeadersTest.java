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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that redelivery works correctly when a child route with NoErrorHandler uses removeHeaders("*"), which strips
 * internal CamelRedeliveryCounter headers during each redelivery attempt.
 */
public class RedeliverToSubRouteRemoveHeadersTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        OnRedeliveryRecorder.counters.clear();
    }

    @Test
    public void testRedeliveryCounterWithRemoveHeaders() throws Exception {
        // The sub-route always throws, so after 3 redelivery attempts the onException handler should kick in.
        // mock:dead receives the handled failure; mock:sub is hit 1 (initial) + 3 (redeliveries) = 4 times.
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:sub").expectedMessageCount(4);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBody("direct:start", "Hello World");

        assertTrue(notify.matches(10, TimeUnit.SECONDS), "Exchange did not complete in time — redelivery may be looping");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnRedeliverySeesCorrectCounter() throws Exception {
        // Verify that the onRedelivery processor sees the correct, incrementing
        // CamelRedeliveryCounter header even when removeHeaders("*") is used in the child route.
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.sendBody("direct:start", "Hello World");

        assertTrue(notify.matches(10, TimeUnit.SECONDS), "Exchange did not complete in time — redelivery may be looping");

        assertMockEndpointsSatisfied();

        assertEquals(List.of(1, 2, 3), OnRedeliveryRecorder.counters);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0)
                        .handled(true)
                        .onRedelivery(new OnRedeliveryRecorder())
                        .to("mock:dead");

                from("direct:start")
                        .to("direct:sub");

                from("direct:sub")
                        .errorHandler(noErrorHandler())
                        .removeHeaders("*")
                        .to("mock:sub")
                        .process(new AlwaysFailProcessor());
            }
        };
    }

    public static class AlwaysFailProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            throw new IOException("Forced");
        }
    }

    public static class OnRedeliveryRecorder implements Processor {
        static final List<Integer> counters = new ArrayList<>();

        @Override
        public void process(Exchange exchange) {
            Integer counter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
            counters.add(counter);
        }
    }
}
