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
package org.apache.camel.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Drives nested test classes through the JUnit Platform launcher to verify that {@link AvailablePortFinder.Port} reacts
 * correctly to all three combinations of {@code @RegisterExtension} placement and {@code @TestInstance} lifecycle. The
 * pre-existing leak (CAMEL-21122) only manifested under the real JUnit lifecycle, so this test exercises it directly
 * instead of calling callbacks by hand.
 */
public class AvailablePortFinderLifecycleTest {

    static final List<AvailablePortFinder.Port> SEEN = new ArrayList<>();

    public static class PerMethodNonStaticContainer {
        @RegisterExtension
        AvailablePortFinder.Port port = AvailablePortFinder.find();

        @Test
        void t1() {
            SEEN.add(port);
        }

        @Test
        void t2() {
            SEEN.add(port);
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    public static class PerClassNonStaticContainer {
        @RegisterExtension
        AvailablePortFinder.Port port = AvailablePortFinder.find();

        @Test
        void t1() {
            SEEN.add(port);
        }

        @Test
        void t2() {
            SEEN.add(port);
        }
    }

    public static class StaticContainer {
        @RegisterExtension
        static AvailablePortFinder.Port PORT = AvailablePortFinder.find();

        @Test
        void t1() {
            SEEN.add(PORT);
        }

        @Test
        void t2() {
            SEEN.add(PORT);
        }
    }

    @Test
    public void perMethodNonStaticPortReleasesAfterEach() {
        SEEN.clear();
        run(PerMethodNonStaticContainer.class);

        // Default lifecycle: JUnit constructs a fresh test instance per method,
        // so each method sees its own Port. Both must be released after their test.
        assertEquals(2, SEEN.size(), "two test methods should have observed a Port each");
        assertNotSame(SEEN.get(0), SEEN.get(1), "PER_METHOD lifecycle must allocate a new Port per method");
        assertFalse(AvailablePortFinder.isRegistered(SEEN.get(0)), "first method's port must be released");
        assertFalse(AvailablePortFinder.isRegistered(SEEN.get(1)), "second method's port must be released");
    }

    @Test
    public void perClassNonStaticPortSurvivesAndReleasesOnAfterAll() {
        SEEN.clear();
        run(PerClassNonStaticContainer.class);

        // PER_CLASS reuses the single test instance, so both methods share the same Port.
        // JUnit also delivers BeforeAllCallback/AfterAllCallback to instance extensions
        // under PER_CLASS, so classScoped is set, afterEach is a no-op, and afterAll
        // releases the port exactly once.
        assertEquals(2, SEEN.size(), "two test methods should have observed a Port each");
        assertSame(SEEN.get(0), SEEN.get(1), "PER_CLASS lifecycle must share a single Port across methods");
        assertFalse(AvailablePortFinder.isRegistered(SEEN.get(0)), "shared port must be released on afterAll");
    }

    @Test
    public void staticPortSurvivesAndReleasesOnAfterAll() {
        SEEN.clear();
        run(StaticContainer.class);

        assertEquals(2, SEEN.size(), "two test methods should have observed a Port each");
        assertSame(SEEN.get(0), SEEN.get(1), "static @RegisterExtension must share a single Port across methods");
        assertFalse(AvailablePortFinder.isRegistered(SEEN.get(0)), "static port must be released on afterAll");
    }

    private static void run(Class<?> testClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass))
                .build();
        LauncherFactory.create().execute(request);
    }
}
