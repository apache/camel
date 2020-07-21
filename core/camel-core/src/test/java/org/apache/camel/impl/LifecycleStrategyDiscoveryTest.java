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
import org.apache.camel.spi.OnCamelContextStart;
import org.apache.camel.spi.OnCamelContextStop;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.LifecycleStrategySupport.onCamelContextInitialized;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LifecycleStrategyDiscoveryTest extends TestSupport {
    @Test
    public void testLifecycleStrategyDiscovery() throws Exception {
        final AtomicInteger onInit = new AtomicInteger();
        final AtomicInteger onStart = new AtomicInteger();
        final AtomicInteger onStop = new AtomicInteger();
        final AtomicInteger onInitRoute = new AtomicInteger();
        final AtomicInteger onStartRoute = new AtomicInteger();
        final AtomicInteger onStopRoute = new AtomicInteger();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("myOnInit", onCamelContextInitialized(c -> onInit.incrementAndGet()));
        context.getRegistry().bind("myOnStart", onCamelContextInitialized(c -> onStart.incrementAndGet()));
        context.getRegistry().bind("myOnStop", onCamelContextInitialized(c -> onStop.incrementAndGet()));

        try {
            class MyBuilder extends RouteBuilder implements OnCamelContextInitialized, OnCamelContextStart, OnCamelContextStop {
                @Override
                public void configure() throws Exception {
                }

                @Override
                public void onContextInitialized(CamelContext context) {
                    onInitRoute.incrementAndGet();
                }

                @Override
                public void onContextStart(CamelContext context) {
                    onStartRoute.incrementAndGet();
                }

                @Override
                public void onContextStop(CamelContext context) {
                    onStopRoute.incrementAndGet();
                }
            }

            context.addRoutes(new MyBuilder());
            context.start();
        } finally {
            context.stop();
        }

        assertEquals(1, onInit.get());
        assertEquals(1, onStart.get());
        assertEquals(1, onStop.get());
        assertEquals(1, onInitRoute.get());
        assertEquals(1, onStartRoute.get());
        assertEquals(1, onStopRoute.get());
    }
}
