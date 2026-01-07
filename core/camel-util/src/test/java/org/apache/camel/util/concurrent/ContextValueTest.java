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
package org.apache.camel.util.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContextValueTest {

    @Test
    public void testBasicUsage() {
        ContextValue<String> routeId = ContextValue.newInstance("routeId");

        // Initially not bound
        assertFalse(routeId.isBound());
        assertNull(routeId.orElse(null));
        assertEquals("default", routeId.orElse("default"));

        // Bind a value
        ContextValue.where(routeId, "myRoute", () -> {
            assertTrue(routeId.isBound());
            assertEquals("myRoute", routeId.get());
            assertEquals("myRoute", routeId.orElse("default"));
        });

        // After scope, not bound anymore (for ScopedValue) or still bound (for ThreadLocal)
        // We can't assert this reliably as it depends on the implementation
    }

    @Test
    public void testNestedScopes() {
        ContextValue<String> routeId = ContextValue.newInstance("routeId");

        ContextValue.where(routeId, "outer", () -> {
            assertEquals("outer", routeId.get());

            ContextValue.where(routeId, "inner", () -> {
                assertEquals("inner", routeId.get());
            });

            // After inner scope, should be back to outer
            assertEquals("outer", routeId.get());
        });
    }

    @Test
    public void testMultipleContextValues() {
        ContextValue<String> routeId = ContextValue.newInstance("routeId");
        ContextValue<String> exchangeId = ContextValue.newInstance("exchangeId");

        ContextValue.where(routeId, "route1", () -> {
            ContextValue.where(exchangeId, "exchange1", () -> {
                assertEquals("route1", routeId.get());
                assertEquals("exchange1", exchangeId.get());
            });
        });
    }

    @Test
    public void testThreadLocalContextValue() {
        // ThreadLocal-based context values support mutation
        ContextValue<String> mutableValue = ContextValue.newThreadLocal("mutable");

        assertFalse(mutableValue.isBound());

        mutableValue.set("value1");
        assertTrue(mutableValue.isBound());
        assertEquals("value1", mutableValue.get());

        mutableValue.set("value2");
        assertEquals("value2", mutableValue.get());

        mutableValue.remove();
        assertFalse(mutableValue.isBound());
    }

    @Test
    public void testWhereWithSupplier() {
        ContextValue<String> routeId = ContextValue.newInstance("routeId");

        String result = ContextValue.where(routeId, "myRoute", () -> {
            return "Result: " + routeId.get();
        });

        assertEquals("Result: myRoute", result);
    }

    @Test
    public void testThreadIsolation() throws Exception {
        ContextValue<String> routeId = ContextValue.newInstance("routeId");

        // Set value in main thread
        ContextValue.where(routeId, "mainThread", () -> {
            assertEquals("mainThread", routeId.get());

            // Create a new thread - it should not see the value (for ScopedValue)
            // or see null (for ThreadLocal without inheritance)
            Thread thread = new Thread(() -> {
                // The value should not be visible in the new thread
                assertNull(routeId.orElse(null));
            });
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Main thread should still see its value
            assertEquals("mainThread", routeId.get());
        });
    }

    @Test
    public void testName() {
        ContextValue<String> value = ContextValue.newInstance("testName");
        assertEquals("testName", value.name());
        assertTrue(value.toString().contains("testName"));
    }
}
