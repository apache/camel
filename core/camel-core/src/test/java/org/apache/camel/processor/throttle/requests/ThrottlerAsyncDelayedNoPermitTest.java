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
package org.apache.camel.processor.throttle.requests;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With asyncDelayed, an exchange that finds no permit at all in the queue (the maximum requests is 0) should wait and
 * try again, instead of failing.
 */
public class ThrottlerAsyncDelayedNoPermitTest extends ContextTestSupport {

    private final AtomicInteger evaluations = new AtomicInteger();

    @Test
    public void testNoPermitAsyncDelayed() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        Future<Exchange> reply = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello World"));
        Exchange out = reply.get(10, TimeUnit.SECONDS);

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        // the maximum requests was evaluated as 0 first, and as 1 when the exchange tried again
        assertEquals(2, evaluations.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .throttle(new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) {
                                // paused for the first evaluation, then 1 per period
                                return evaluations.incrementAndGet() == 1 ? 0 : 1;
                            }
                        }).timePeriodMillis(100).asyncDelayed()
                        .to("mock:result");
            }
        };
    }
}
