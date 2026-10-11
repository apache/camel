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
package org.apache.camel.main.download;

import java.util.Properties;

import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The HTTP server started implicitly for platform-http (camel.server.enabled is not set) uses the camel.server.*
 * settings.
 */
public class MainHttpServerFactoryTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void shouldUseDefaultPortWithoutServerProperties() {
        MainHttpServerFactory.setupHttpServer(context, false);

        MainHttpServer server = context.hasService(MainHttpServer.class);
        assertNotNull(server);
        assertEquals(8080, server.getPort());
        assertEquals("0.0.0.0", server.getHost());
        assertEquals("/", server.getPath());
    }

    @Test
    void shouldApplyServerProperties() {
        Properties prop = new Properties();
        prop.put("camel.server.port", "9090");
        prop.put("camel.server.host", "localhost");
        prop.put("camel.server.path", "/api");
        prop.put("camel.server.max-body-size", "2048");
        context.getPropertiesComponent().setInitialProperties(prop);

        MainHttpServerFactory.setupHttpServer(context, false);

        MainHttpServer server = context.hasService(MainHttpServer.class);
        assertNotNull(server);
        assertEquals(9090, server.getPort());
        assertEquals("localhost", server.getHost());
        assertEquals("/api", server.getPath());
        assertEquals(2048L, server.getMaxBodySize());
    }

    @Test
    void shouldResolvePlaceholderInServerPort() {
        Properties prop = new Properties();
        prop.put("camel.server.port", "{{myPort}}");
        prop.put("myPort", "9191");
        context.getPropertiesComponent().setInitialProperties(prop);

        MainHttpServerFactory.setupHttpServer(context, false);

        MainHttpServer server = context.hasService(MainHttpServer.class);
        assertNotNull(server);
        assertEquals(9191, server.getPort());
    }

    @Test
    void shouldOverrideServerPropertiesWithJvmSystemProperties() {
        Properties prop = new Properties();
        prop.put("camel.server.host", "localhost");
        prop.put("camel.server.path", "/api");
        context.getPropertiesComponent().setInitialProperties(prop);

        System.setProperty("camel.server.host", "127.0.0.1");
        try {
            MainHttpServerFactory.setupHttpServer(context, false);
        } finally {
            System.clearProperty("camel.server.host");
        }

        MainHttpServer server = context.hasService(MainHttpServer.class);
        assertNotNull(server);
        assertEquals("127.0.0.1", server.getHost());
        assertEquals("/api", server.getPath());
    }

    @Test
    void shouldNotStartServerWhenSilent() {
        Properties prop = new Properties();
        prop.put("camel.server.port", "9090");
        context.getPropertiesComponent().setInitialProperties(prop);

        MainHttpServerFactory.setupHttpServer(context, true);

        assertNull(context.hasService(MainHttpServer.class));
    }
}
