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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The dev MCP binds the management server to 127.0.0.1, which a management server that shares the port of the HTTP
 * server cannot apply.
 */
class KameletMainMcpManagementHostTest {

    @Test
    void bindsTheManagementServerToLoopback() throws Exception {
        assertEquals("127.0.0.1", managementHost(null));
    }

    @Test
    void bindsTheManagementServerWithItsOwnPortToLoopback() throws Exception {
        assertEquals("127.0.0.1", managementHost(null, "camel.server.enabled", "true", "camel.management.port", "9876"));
    }

    @Test
    void keepsTheHostWhenSharingThePortOfTheHttpServer() throws Exception {
        // such as camel run --mcp --port 8080
        assertEquals("0.0.0.0", managementHost(null, "camel.server.enabled", "true"));
        assertEquals("0.0.0.0",
                managementHost(null, "camel.server.enabled", "true", "camel.server.port", "0", "camel.management.port", "0"));
    }

    @Test
    void keepsTheHostWhenTheHttpServerIsEnabledInApplicationProperties() throws Exception {
        assertEquals("0.0.0.0", managementHost("classpath:org/apache/camel/main/mcp-shared-port.properties"));
    }

    @Test
    void leavesAConfiguredHostToCamelMain() throws Exception {
        // camel-main binds camel.management.host later on
        assertEquals("0.0.0.0", managementHost(null, "camel.management.host", "192.168.1.10"));
    }

    private static String managementHost(String location, String... properties) throws Exception {
        KameletMain main = new KameletMain("camel.jbang");
        try (CamelContext context = new DefaultCamelContext()) {
            Properties initial = new Properties();
            for (int i = 0; i < properties.length; i += 2) {
                initial.setProperty(properties[i], properties[i + 1]);
            }
            context.getPropertiesComponent().setInitialProperties(initial);
            if (location != null) {
                context.getPropertiesComponent().setLocation(location);
            }
            main.applyMcpManagementHost(context);
        }
        return main.configure().httpManagementServer().getHost();
    }
}
