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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of the supervising route controller are looked up while routes are added and removed.
 */
public class DefaultSupervisingRouteControllerConcurrentRoutesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNonControlledRouteIdsWhileARouteIsAdded() throws Exception {
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setExcludeRoutes("excluded*");
        context.addRoutes(routes("excluded1", "excluded2"));
        context.start();

        Iterator<String> ids = src.getNonControlledRouteIds().iterator();
        String first = ids.next();

        // a route added at runtime while the ids are read, such as from JMX or a health check
        context.addRoutes(routes("excluded3"));

        List<String> read = new ArrayList<>(List.of(first));
        ids.forEachRemaining(read::add);
        assertTrue(read.containsAll(List.of("excluded1", "excluded2")), "read " + read);
        assertEquals(3, src.getNonControlledRouteIds().size());
    }

    @Test
    public void testControlledRoutesWhileRoutesAreAddedAndRemoved() throws Exception {
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setExcludeRoutes("excluded*");
        context.addRoutes(routes("supervised1", "supervised2", "excluded1"));
        context.start();

        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch reading = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                while (!done.get()) {
                    for (Route route : src.getControlledRoutes()) {
                        route.getId();
                    }
                    src.getNonControlledRouteIds().forEach(String::length);
                    reading.countDown();
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                reading.countDown();
            }
        }, "reader");
        reader.start();

        try {
            assertTrue(reading.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 100 && failure.get() == null; i++) {
                context.addRoutes(routes("supervised-" + i, "excluded-" + i));
                context.getRouteController().stopRoute("supervised-" + i);
                context.removeRoute("supervised-" + i);
                context.getRouteController().stopRoute("excluded-" + i);
                context.removeRoute("excluded-" + i);
            }
        } finally {
            done.set(true);
            reader.join(10000);
        }

        assertFalse(reader.isAlive());
        assertNull(failure.get(), () -> "Reading the routes failed: " + failure.get());
        assertEquals(3, src.getControlledRoutes().size());
        assertEquals(List.of("excluded1"), new ArrayList<>(src.getNonControlledRouteIds()));
    }

    private static RouteBuilder routes(String... ids) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String id : ids) {
                    from("direct:" + id).routeId(id).to("mock:result");
                }
            }
        };
    }
}
