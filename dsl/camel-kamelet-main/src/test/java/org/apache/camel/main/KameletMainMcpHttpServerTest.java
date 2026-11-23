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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * camel.server.mcp-enabled turns the HTTP server on at runtime, from every property source, as camel.server.enabled
 * does (CAMEL-27404).
 */
class KameletMainMcpHttpServerTest {

    @Test
    void mcpEnabledTurnsTheHttpServerOn() throws Exception {
        assertTrue(httpServerEnabled("camel.server.mcp-enabled", "true"));
        assertTrue(httpServerEnabled("camel.server.mcpEnabled", "true"));
        assertTrue(httpServerEnabled("camel.server.mcp-enabled", "true", "camel.server.mcp-transport", "http"));
    }

    @Test
    void mcpEnabledAsJvmSystemPropertyTurnsTheHttpServerOn() throws Exception {
        System.setProperty("camel.server.mcp-enabled", "true");
        try {
            assertTrue(httpServerEnabled());
        } finally {
            System.clearProperty("camel.server.mcp-enabled");
        }
    }

    @Test
    void mcpDisabledLeavesTheHttpServerOff() throws Exception {
        assertFalse(httpServerEnabled());
        assertFalse(httpServerEnabled("camel.server.mcp-enabled", "false"));
    }

    @Test
    void mcpOverStdioNeedsNoHttpServer() throws Exception {
        assertFalse(httpServerEnabled("camel.server.mcp-enabled", "true", "camel.server.mcp-transport", "stdio"));
        assertFalse(httpServerEnabled("camel.server.mcp-enabled", "true", "camel.server.mcpTransport", " STDIO "));
    }

    @Test
    void aConfiguredHttpServerIsLeftToCamelMain() throws Exception {
        // camel-main binds camel.server.enabled later on
        assertFalse(httpServerEnabled("camel.server.mcp-enabled", "true", "camel.server.enabled", "false"));
    }

    private static boolean httpServerEnabled(String... properties) throws Exception {
        KameletMain main = new KameletMain("camel.jbang");
        try (CamelContext context = new DefaultCamelContext()) {
            Properties initial = new Properties();
            for (int i = 0; i < properties.length; i += 2) {
                initial.setProperty(properties[i], properties[i + 1]);
            }
            context.getPropertiesComponent().setInitialProperties(initial);
            main.applyMcpHttpServer(context);
        }
        return main.configure().httpServer().isEnabled();
    }
}
