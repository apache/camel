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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The Wire Tap EIP with a thread pool that its owner shut down (with the default CallerRuns policy): the taps that
 * cannot be sent must not be lost silently, nor stay counted as pending exchanges, as a graceful shutdown of the route
 * would wait for them until its timeout.
 */
class WireTapShutDownPoolTest extends ContextTestSupport {

    private ExecutorService pool;

    @Test
    void testThreadPoolShutDown() throws Exception {
        pool.shutdown();

        getMockEndpoint("mock:main").expectedMessageCount(0);
        getMockEndpoint("mock:tapped").expectedMessageCount(0);

        // the tap is rejected, as with the Abort policy: the exchange fails, so the error handler can react
        for (String body : List.of("A", "B", "C")) {
            Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(body));
            assertInstanceOf(RejectedExecutionException.class, exchange.getException());
        }

        assertMockEndpointsSatisfied();
        WireTapProcessor tap = context.getProcessor("tap", WireTapProcessor.class);
        assertEquals(0, tap.getPendingExchangesSize());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        pool = context.getExecutorServiceManager().newDefaultThreadPool(this, "shared");
        context.getRegistry().bind("sharedPool", pool);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").wireTap("mock:tapped").executorService("sharedPool").id("tap").to("mock:main");
            }
        };
    }
}
