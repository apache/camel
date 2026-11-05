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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-26330: the camel.chaos.* options are bound to {@link ChaosConfigurationProperties} and made available to
 * camel-chaos in the properties component; camel.chaos.rules is insecure:dev.
 */
class ChaosConfigurationPropertiesTest {

    @Test
    void optionsFromPropertiesAreBoundAndPublished() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.chaos.enabled", "true");
        main.addInitialProperty("camel.chaos.seed", "7");
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error rate=0.3");
        main.addInitialProperty("camel.chaos.max-duration", "1m");
        main.addInitialProperty("camel.chaos.reportOnShutdown", "false");

        main.start();
        try {
            ChaosConfigurationProperties config = main.configure().chaosConfig();
            assertTrue(config.getEnabled());
            assertEquals(7L, config.getSeed());
            assertEquals("endpoint=kafka:orders* error rate=0.3", config.getRules());
            assertEquals("1m", config.getMaxDuration());
            assertFalse(config.isReportOnShutdown());

            PropertiesComponent pc = main.getCamelContext().getPropertiesComponent();
            assertEquals("true", pc.resolveProperty("camel.chaos.enabled").orElse(null));
            assertEquals("7", pc.resolveProperty("camel.chaos.seed").orElse(null));
            assertEquals("endpoint=kafka:orders* error rate=0.3", pc.resolveProperty("camel.chaos.rules").orElse(null));
            assertEquals("1m", pc.resolveProperty("camel.chaos.maxDuration").orElse(null));
            assertEquals("false", pc.resolveProperty("camel.chaos.reportOnShutdown").orElse(null));
        } finally {
            main.stop();
        }
    }

    @Test
    void optionsFromJavaArePublished() throws Exception {
        Main main = new Main();
        main.configure().chaosConfig().withEnabled(true).withSeed(9L).withRules("route=orders corrupt mode=headers")
                .withMaxDuration("2m").withReportOnShutdown(false);

        main.start();
        try {
            PropertiesComponent pc = main.getCamelContext().getPropertiesComponent();
            assertEquals("true", pc.resolveProperty("camel.chaos.enabled").orElse(null));
            assertEquals("9", pc.resolveProperty("camel.chaos.seed").orElse(null));
            assertEquals("route=orders corrupt mode=headers", pc.resolveProperty("camel.chaos.rules").orElse(null));
            assertEquals("2m", pc.resolveProperty("camel.chaos.maxDuration").orElse(null));
            assertEquals("false", pc.resolveProperty("camel.chaos.reportOnShutdown").orElse(null));
        } finally {
            main.stop();
        }
    }

    @Test
    void enabledIsNotPublishedWhenNotSet() throws Exception {
        // camel-chaos decides by the profile when camel.chaos.enabled is not set
        Main main = new Main();
        main.addInitialProperty("camel.chaos.seed", "3");

        main.start();
        try {
            assertNull(main.configure().chaosConfig().getEnabled());
            assertTrue(main.getCamelContext().getPropertiesComponent().resolveProperty("camel.chaos.enabled").isEmpty());
            assertEquals("10m",
                    main.getCamelContext().getPropertiesComponent().resolveProperty("camel.chaos.maxDuration").orElse(null));
        } finally {
            main.stop();
        }
    }

    @Test
    void unknownOptionFailsTheStart() {
        Main main = new Main();
        main.addInitialProperty("camel.chaos.rule", "endpoint=kafka:orders* error");

        Exception e = assertThrows(Exception.class, main::start);
        assertTrue(stackTrace(e).contains("camel.chaos.rule"), stackTrace(e));
        main.stop();
    }

    @Test
    void profileProdRefusesRules() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error");

        Exception e = assertThrows(Exception.class, main::start);
        assertTrue(e.getMessage().contains("Security policy violations detected"), e.getMessage());
        assertTrue(e.getMessage().contains("camel.chaos.rules"), e.getMessage());
        main.stop();
    }

    @Test
    void profileProdWithoutRulesStarts() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.chaos.enabled", "true");

        main.start();
        main.stop();
    }

    @Test
    void profileDevAllowsRules() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error");

        main.start();
        main.stop();
    }

    @Test
    void failPolicyWithoutProfileRefusesRules() {
        Main main = new Main();
        main.addInitialProperty("camel.security.policy", "fail");
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error");

        Exception e = assertThrows(Exception.class, main::start);
        assertTrue(e.getMessage().contains("camel.chaos.rules"), e.getMessage());
        main.stop();
    }

    private static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
