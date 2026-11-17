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
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route is stopped with a timeout that forces it down while an exchange is still in flight, and removed: the done
 * exchange must not be taken off the inflight count of a route that is added again with the same id.
 */
public class InflightRepositoryRouteRemovedWhileInflightTest extends ContextTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch gate = new CountDownLatch(1);

    @Test
    public void testRouteAddedAgainWithTheSameId() throws Exception {
        Future<Object> reply = forceRemoveWithExchangeInFlight();

        context.addRoutes(slowRoute());

        gate.countDown();
        assertOldExchangeFailed(reply);
        assertEquals(0, context.getInflightRepository().size("slow"),
                "the exchange of the removed route must not be taken off the count of the new route");

        // the count works as before for the exchanges of the new route
        template.sendBody("direct:slow", "B");
        assertEquals(0, context.getInflightRepository().size("slow"));
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void testExchangeOfRemovedRouteCountedUntilDone() throws Exception {
        Future<Object> reply = forceRemoveWithExchangeInFlight();

        context.addRoutes(slowRoute());
        // it still runs (and a graceful stop of the new route waits for it)
        assertEquals(1, context.getInflightRepository().size("slow"),
                "the exchange of the removed route is still in flight");

        gate.countDown();
        assertOldExchangeFailed(reply);
        assertEquals(0, context.getInflightRepository().size("slow"));
    }

    @Test
    public void testRouteNotAddedAgain() throws Exception {
        Future<Object> reply = forceRemoveWithExchangeInFlight();

        gate.countDown();
        assertOldExchangeFailed(reply);
        assertEquals(0, context.getInflightRepository().size("slow"));
        assertEquals(0, context.getInflightRepository().size());
    }

    private Future<Object> forceRemoveWithExchangeInFlight() throws Exception {
        Future<Object> reply = template.asyncRequestBody("direct:slow", "A");
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals(1, context.getInflightRepository().size("slow"));

        // the graceful stop times out and forces the route down, with the exchange still in flight
        context.getRouteController().stopRoute("slow", 100, TimeUnit.MILLISECONDS);
        assertTrue(context.removeRoute("slow"));
        return reply;
    }

    private static void assertOldExchangeFailed(Future<Object> reply) {
        // its route was stopped while it was in flight
        ExecutionException e = assertThrows(ExecutionException.class, () -> reply.get(10, TimeUnit.SECONDS));
        assertInstanceOf(RouteStoppingException.class, e.getCause().getCause());
    }

    private RouteBuilder slowRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:slow").routeId("slow")
                        .process(e -> {
                            if ("A".equals(e.getMessage().getBody())) {
                                entered.countDown();
                                assertTrue(gate.await(20, TimeUnit.SECONDS));
                            }
                        });
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return slowRoute();
    }
}
