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
package org.apache.camel.main;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextInitializing;
import org.apache.camel.spi.OnCamelContextStarting;
import org.apache.camel.spi.OnCamelContextStopping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContextEventsTest {
    @Test
    public void testEvents() throws Exception {
        MyConfig config = new MyConfig();

        Main main = new Main();
        main.configure().setDurationMaxSeconds(1);
        main.configure().addConfiguration(config);
        main.run();

        assertEquals(1, config.onInitializing.get());
        assertEquals(1, config.onInitialized.get());
        assertEquals(1, config.onStart.get());
        assertEquals(1, config.onStop.get());
    }

    public static class MyConfig implements CamelConfiguration {
        final AtomicInteger onInitializing = new AtomicInteger();
        final AtomicInteger onInitialized = new AtomicInteger();
        final AtomicInteger onStart = new AtomicInteger();
        final AtomicInteger onStop = new AtomicInteger();

        @BindToRegistry
        public OnCamelContextInitializing onContextInitializing() {
            return context -> onInitializing.incrementAndGet();
        }

        @BindToRegistry
        public OnCamelContextInitialized onContextInitialized() {
            return context -> onInitialized.incrementAndGet();
        }

        @BindToRegistry
        public OnCamelContextStarting onContextStart() {
            return context -> onStart.incrementAndGet();
        }

        @BindToRegistry
        public OnCamelContextStopping onContextStop() {
            return context -> onStop.incrementAndGet();
        }
    }
}
