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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.vault.GcpVaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The OS environment variables of camel-main options can have underscores between the words of an option, such as the
 * {@code CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY} variable that the GCP vault documents.
 */
@Isolated
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@DisabledOnOs(OS.WINDOWS)
public class MainEnvVarUnderscoreOptionTest {

    private final Map<String, String> previous = new HashMap<>();

    @Test
    public void testVaultAndMainOptionsWithUnderscoresBetweenWords() {
        setEnv("CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY", "file:/etc/gcp/sa.json");
        setEnv("CAMEL_VAULT_GCP_PROJECT_ID", "myproject");
        setEnv("CAMEL_MAIN_SHUTDOWN_TIMEOUT", "7");

        Main main = new Main();
        main.start();
        try {
            GcpVaultConfiguration gcp = main.getCamelContext().getVaultConfiguration().gcp();
            assertEquals("file:/etc/gcp/sa.json", gcp.getServiceAccountKey());
            assertEquals("myproject", gcp.getProjectId());
            assertEquals(7, main.configure().getShutdownTimeout());
        } finally {
            main.stop();
        }
    }

    @AfterEach
    void tearDown() {
        Map<String, String> env = getEditableEnv();
        previous.forEach((k, v) -> {
            if (v != null) {
                env.put(k, v);
            } else {
                env.remove(k);
            }
        });
    }

    private void setEnv(String name, String value) {
        Map<String, String> env = getEditableEnv();
        previous.put(name, env.get(name));
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
