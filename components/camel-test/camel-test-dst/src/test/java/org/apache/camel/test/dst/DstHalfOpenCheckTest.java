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
package org.apache.camel.test.dst;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half open check of {@code ThrottlingExceptionRoutePolicy} runs on a scheduler created through the
 * ExecutorServiceManager: in a run it is a task of the loop, and the circuit is half opened after {@code halfOpenAfter}
 * of virtual time. When {@code halfOpenAfter} is raised while the circuit is open, the check schedules the next one for
 * the time that is left.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstHalfOpenCheckTest {

    private static final long REAL_LIMIT = 5_000_000_000L;

    @Test
    void theCircuitIsHalfOpenedAfterHalfOpenAfterOfVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        dst.strict(true, List.of());
        DefaultCamelContext context = DstCamel.newContext(dst);
        try {
            ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(1, 60_000, 30_000, List.of());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").routeId("r").routePolicy(policy)
                            .throwException(new IllegalStateException("the downstream system is down"));
                }
            });
            context.start();
            Exchange[] done = new Exchange[1];
            dst.executor("client", 1).schedule("send", 1_000, () -> send(context, done));
            long start = System.nanoTime();

            dst.runFor(30_000);
            assertNotNull(done[0]);
            assertEquals("opened", policy.getStateAsString());
            assertTrue(((ServiceSupport) context.getRoute("r").getConsumer()).isSuspended());

            dst.runUntil(() -> "half opened".equals(policy.getStateAsString()), "the circuit is half opened");
            assertEquals(31_000, dst.now(), "half opened 30 s after the failure at 1 s");
            assertFalse(((ServiceSupport) context.getRoute("r").getConsumer()).isSuspended());
            assertTrue(dst.traceLines().stream()
                    .anyMatch(l -> l.contains("thread pool created: ThrottlingExceptionRoutePolicy (1 threads)")),
                    String.join("\n", dst.traceLines()));
            assertTrue(System.nanoTime() - start < REAL_LIMIT, "30 s of virtual time take no real time");
        } finally {
            dst.dropPending();
            context.stop();
            dst.close();
        }
    }

    @Test
    void aRaisedHalfOpenAfterHalfOpensTheCircuitWhenTheNewTimeIsUp() throws Exception {
        Dst dst = new Dst(1);
        dst.strict(true, List.of());
        DefaultCamelContext context = DstCamel.newContext(dst);
        try {
            ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(1, 60_000, 30_000, List.of());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").routeId("r").routePolicy(policy)
                            .throwException(new IllegalStateException("the downstream system is down"));
                }
            });
            context.start();
            Exchange[] done = new Exchange[1];
            DstExecutor client = dst.executor("client", 1);
            client.schedule("send", 1_000, () -> send(context, done));
            // as over JMX, while the circuit is open: the check scheduled at 1 s is due at 31 s, before the new time
            client.schedule("raise halfOpenAfter", 20_000, () -> policy.setHalfOpenAfter(40_000));

            dst.runFor(31_000);
            assertEquals("opened", policy.getStateAsString());
            assertTrue(((ServiceSupport) context.getRoute("r").getConsumer()).isSuspended());

            // the check at 31 s schedules the next one for the 10 s that are left
            dst.runUntil(() -> "half opened".equals(policy.getStateAsString()), "the circuit is half opened");
            assertEquals(41_000, dst.now(), "half opened 40 s after the failure at 1 s");
            assertFalse(((ServiceSupport) context.getRoute("r").getConsumer()).isSuspended());
        } finally {
            dst.dropPending();
            context.stop();
            dst.close();
        }
    }

    /** Sends an exchange to the route's consumer without waiting for it (no blocking wait on the loop thread). */
    private static void send(DefaultCamelContext context, Exchange[] done) {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute("r").getConsumer();
        Exchange exchange = consumer.createExchange(false);
        exchange.getMessage().setBody("hello");
        consumer.getAsyncProcessor().process(exchange, doneSync -> done[0] = exchange);
    }
}
