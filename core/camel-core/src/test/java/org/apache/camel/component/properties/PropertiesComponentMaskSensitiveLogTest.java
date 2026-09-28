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
package org.apache.camel.component.properties;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesComponentMaskSensitiveLogTest extends ContextTestSupport {

    private static final String LOGGER = "org.apache.camel.component.properties";

    private final List<String> messages = new CopyOnWriteArrayList<>();

    private static class MyVaultFunction implements PropertiesFunction {

        @Override
        public String getName() {
            return "myvault";
        }

        @Override
        public String apply(String remainder) {
            return "Vault-" + remainder;
        }

        @Override
        public boolean isSensitive() {
            return true;
        }
    }

    private static class MyUpperFunction implements PropertiesFunction {

        @Override
        public String getName() {
            return "myupper";
        }

        @Override
        public String apply(String remainder) {
            return remainder.toUpperCase();
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        ConsumingAppender.newAppender(LOGGER, "PropertiesComponentMaskSensitiveLogTest", Level.TRACE,
                e -> messages.add(e.getMessage().getFormattedMessage()));
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties props = new Properties();
        props.put("db.password", "Secret-db");
        props.put("DB_PASSWORD", "Secret-env");
        props.put("my.apiSecret", "Secret-api");
        props.put("greeting", "Hello-public");
        context.getPropertiesComponent().setInitialProperties(props);
        context.getPropertiesComponent().addPropertiesFunction(new MyVaultFunction());
        context.getPropertiesComponent().addPropertiesFunction(new MyUpperFunction());
        return context;
    }

    @Test
    public void testSensitiveValuesAreMasked() {
        assertEquals("Vault-db/password", context.resolvePropertyPlaceholders("{{myvault:db/password}}"));
        assertEquals("Secret-db", context.resolvePropertyPlaceholders("{{db.password}}"));
        assertEquals("Secret-env", context.resolvePropertyPlaceholders("{{DB_PASSWORD}}"));
        assertEquals("Secret-api", context.resolvePropertyPlaceholders("{{my.apiSecret}}"));
        assertEquals("jdbc:Secret-db", context.resolvePropertyPlaceholders("jdbc:{{db.password}}"));

        assertFalse(messages.isEmpty());
        for (String msg : messages) {
            assertFalse(msg.contains("Vault-"), msg);
            assertFalse(msg.contains("Secret-"), msg);
        }
        assertTrue(messages.stream().anyMatch(m -> m.contains("xxxxxx")));
    }

    @Test
    public void testOtherValuesAreLogged() {
        assertEquals("Hello-public", context.resolvePropertyPlaceholders("{{greeting}}"));
        assertEquals("HELLO", context.resolvePropertyPlaceholders("{{myupper:hello}}"));

        assertTrue(messages.stream().anyMatch(m -> m.contains("Hello-public")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("HELLO")));
    }
}
