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

import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camel.sanitizer.* options are bound to {@link SanitizerConfigurationProperties} and made available to
 * camel-sanitizer in the properties component, which it reads when the context initializes.
 */
class SanitizerConfigurationPropertiesTest {

    @Test
    void optionsFromPropertiesAreBoundAndPublished() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.sanitizer.enabled", "true");
        main.addInitialProperty("camel.sanitizer.checks", "commit");
        main.addInitialProperty("camel.sanitizer.stack-depth", "0");
        main.addInitialProperty("camel.sanitizer.reportOnShutdown", "false");
        main.addInitialProperty("camel.sanitizer.pending-timeout", "5s");
        main.addInitialProperty("camel.sanitizer.overlap-sampling", "10");
        main.addInitialProperty("camel.sanitizer.overlapWindow", "2ms");

        main.start();
        try {
            SanitizerConfigurationProperties config = main.configure().sanitizerConfig();
            assertTrue(config.getEnabled());
            assertEquals("commit", config.getChecks());
            assertEquals(0, config.getStackDepth());
            assertFalse(config.isReportOnShutdown());
            assertEquals("5s", config.getPendingTimeout());
            assertEquals(10, config.getOverlapSampling());
            assertEquals("2ms", config.getOverlapWindow());

            PropertiesComponent pc = main.getCamelContext().getPropertiesComponent();
            assertEquals("true", pc.resolveProperty("camel.sanitizer.enabled").orElse(null));
            assertEquals("commit", pc.resolveProperty("camel.sanitizer.checks").orElse(null));
            assertEquals("0", pc.resolveProperty("camel.sanitizer.stackDepth").orElse(null));
            assertEquals("false", pc.resolveProperty("camel.sanitizer.reportOnShutdown").orElse(null));
            assertEquals("5s", pc.resolveProperty("camel.sanitizer.pendingTimeout").orElse(null));
            assertEquals("10", pc.resolveProperty("camel.sanitizer.overlapSampling").orElse(null));
            assertEquals("2ms", pc.resolveProperty("camel.sanitizer.overlapWindow").orElse(null));
        } finally {
            main.stop();
        }
    }

    @Test
    void optionsFromJavaArePublished() throws Exception {
        Main main = new Main();
        main.configure().sanitizerConfig().withEnabled(false).withStackDepth(8).withReportOnShutdown(false)
                .withPendingTimeout("0");

        main.start();
        try {
            PropertiesComponent pc = main.getCamelContext().getPropertiesComponent();
            assertEquals("false", pc.resolveProperty("camel.sanitizer.enabled").orElse(null));
            // not published when not set: camel-sanitizer decides (R1 and R3, and R1, R2 and R3 in a test)
            assertFalse(pc.resolveProperty("camel.sanitizer.checks").isPresent());
            // the same for the sampling of rule R3 (0, and 100 in a test); the window has its default
            assertFalse(pc.resolveProperty("camel.sanitizer.overlapSampling").isPresent());
            assertEquals("1ms", pc.resolveProperty("camel.sanitizer.overlapWindow").orElse(null));
            assertEquals("8", pc.resolveProperty("camel.sanitizer.stackDepth").orElse(null));
            assertEquals("false", pc.resolveProperty("camel.sanitizer.reportOnShutdown").orElse(null));
            assertEquals("0", pc.resolveProperty("camel.sanitizer.pendingTimeout").orElse(null));
        } finally {
            main.stop();
        }
    }

    @Test
    void enabledIsNotPublishedWhenNotSet() throws Exception {
        // camel-sanitizer decides by the profile and the JUnit extension when camel.sanitizer.enabled is not set
        Main main = new Main();
        main.addInitialProperty("camel.sanitizer.stackDepth", "4");

        main.start();
        try {
            PropertiesComponent pc = main.getCamelContext().getPropertiesComponent();
            assertFalse(pc.resolveProperty("camel.sanitizer.enabled").isPresent());
            assertEquals("4", pc.resolveProperty("camel.sanitizer.stackDepth").orElse(null));
        } finally {
            main.stop();
        }
    }
}
