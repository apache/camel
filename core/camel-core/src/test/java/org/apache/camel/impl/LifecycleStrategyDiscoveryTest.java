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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextInitializing;
import org.apache.camel.spi.OnCamelContextStarting;
import org.apache.camel.spi.OnCamelContextStopping;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextInitialized;
import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextInitializing;
import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextStarted;
import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextStarting;
import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextStopped;
import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextStopping;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LifecycleStrategyDiscoveryTest extends TestSupport {
    @Test
    public void testLifecycleStrategyDiscovery() throws Exception {
        final AtomicInteger onInitializing = new AtomicInteger();
        final AtomicInteger onInitialized = new AtomicInteger();
        final AtomicInteger onStarting = new AtomicInteger();
        final AtomicInteger onStarted = new AtomicInteger();
        final AtomicInteger onStopping = new AtomicInteger();
        final AtomicInteger onStopped = new AtomicInteger();
        final AtomicInteger onInitializingRoute = new AtomicInteger();
        final AtomicInteger onInitializedRoute = new AtomicInteger();
        final AtomicInteger onStartRoute = new AtomicInteger();
        final AtomicInteger onStopRoute = new AtomicInteger();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("myOnInitializing", onCamelContextInitializing(c -> onInitializing.incrementAndGet()));
        context.getRegistry().bind("myOnInitialized", onCamelContextInitialized(c -> onInitialized.incrementAndGet()));
        context.getRegistry().bind("myOnStarting", onCamelContextStarting(c -> onStarting.incrementAndGet()));
        context.getRegistry().bind("myOnStarted", onCamelContextStarted(c -> onStarted.incrementAndGet()));
        context.getRegistry().bind("myOnStopping", onCamelContextStopping(c -> onStopping.incrementAndGet()));
        context.getRegistry().bind("myOnStopped", onCamelContextStopped(c -> onStopped.incrementAndGet()));

        try {
            class MyBuilder
                    extends RouteBuilder
                    implements OnCamelContextInitializing, OnCamelContextInitialized, OnCamelContextStarting,
                    OnCamelContextStopping {

                @Override
                public void configure() throws Exception {
                }

                @Override
                public void onContextInitializing(CamelContext context) {
                    onInitializingRoute.incrementAndGet();
                }

                @Override
                public void onContextInitialized(CamelContext context) {
                    onInitializedRoute.incrementAndGet();
                }

                @Override
                public void onContextStarting(CamelContext context) {
                    onStartRoute.incrementAndGet();
                }

                @Override
                public void onContextStopping(CamelContext context) {
                    onStopRoute.incrementAndGet();
                }
            }

            context.addRoutes(new MyBuilder());
            context.start();
        } finally {
            context.stop();
        }

        assertEquals(1, onInitializing.get());
        assertEquals(1, onInitialized.get());
        assertEquals(1, onStarting.get());
        assertEquals(1, onStarted.get());
        assertEquals(1, onStopping.get());
        assertEquals(1, onStopped.get());
        assertEquals(1, onInitializingRoute.get());
        assertEquals(1, onInitializedRoute.get());
        assertEquals(1, onStartRoute.get());
        assertEquals(1, onStopRoute.get());
    }
}
