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
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextStart;
import org.apache.camel.spi.OnCamelContextStop;
import org.junit.Assert;
import org.junit.Test;

public class ContextEventsTest extends Assert {
    @Test
    public void testEvents() throws Exception {
        MyConfig config = new MyConfig();

        Main main = new Main();
        main.configure().setDurationMaxSeconds(1);
        main.configure().addConfiguration(config);
        main.run();

        assertEquals(1, config.onInit.get());
        assertEquals(1, config.onStart.get());
        assertEquals(1, config.onStop.get());
    }

    public static class MyConfig {
        final AtomicInteger onInit = new AtomicInteger();
        final AtomicInteger onStart = new AtomicInteger();
        final AtomicInteger onStop = new AtomicInteger();

        @BindToRegistry
        public OnCamelContextInitialized onContextInitialized() {
            return context -> onInit.incrementAndGet();
        }

        @BindToRegistry
        public OnCamelContextStart onContextStart() {
            return context -> onStart.incrementAndGet();
        }

        @BindToRegistry
        public OnCamelContextStop onContextStop() {
            return context -> onStop.incrementAndGet();
        }
    }
}
