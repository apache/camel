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

import java.lang.reflect.Field;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end test for {@code CAMEL_COMPONENT_*} environment variable processing through {@link BaseMainSupport}.
 * Verifies that the bug-1 fix (using uppercase prefixes in {@code filterEnvVariables}) actually reaches the component
 * at runtime.
 * <p>
 * Reverting the one-line change in {@code BaseMainSupport} (back to lowercase {@code "camel.component."}) leaves every
 * {@link MainHelperTest} green but breaks this test, because only this test exercises the full
 * {@code filterEnvVariables → addComponentEnvVariables} path inside {@code BaseMainSupport}.
 */
@Isolated
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@DisabledOnOs(OS.WINDOWS)
public class MainEnvVarComponentConfigTest {

    private String previousValue;

    @Test
    public void testCamelComponentEnvVarConfiguresComponent() {
        // Inject CAMEL_COMPONENT_SEDA_QUEUE_SIZE=100 into the process environment
        setEnv("CAMEL_COMPONENT_SEDA_QUEUE_SIZE", "100");

        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("seda:test");
            }
        });
        main.start();

        try {
            SedaComponent seda = main.getCamelContext().getComponent("seda", SedaComponent.class);
            assertNotNull(seda);
            assertEquals(100, seda.getQueueSize(),
                    "CAMEL_COMPONENT_SEDA_QUEUE_SIZE=100 must configure seda.queueSize via BaseMainSupport");
        } finally {
            main.stop();
        }
    }

    @AfterEach
    void tearDown() {
        // Restore original env state
        if (previousValue != null) {
            getEditableEnv().put("CAMEL_COMPONENT_SEDA_QUEUE_SIZE", previousValue);
        } else {
            getEditableEnv().remove("CAMEL_COMPONENT_SEDA_QUEUE_SIZE");
        }
    }

    private void setEnv(String name, String value) {
        Map<String, String> env = getEditableEnv();
        previousValue = env.get(name);
        env.put(name, value);
    }

    private static Map<String, String> getEditableEnv() {
        Class<?> classOfMap = System.getenv().getClass();
        try {
            Field field = classOfMap.getDeclaredField("m");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> m = (Map<String, String>) field.get(System.getenv());
            return m;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Cannot access System.getenv() map", e);
        }
    }
}
