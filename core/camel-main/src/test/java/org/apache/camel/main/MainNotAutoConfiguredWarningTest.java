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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainNotAutoConfiguredWarningTest {

    private static final String LOGGER = BaseMainSupport.class.getName();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    public void addAppender() {
        appender = new AbstractAppender("MainNotAutoConfiguredWarningTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.WARN, true);
        loggerConfig.addAppender(appender, Level.WARN, null);
        config.addLogger(LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @AfterEach
    public void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    private List<String> notAutoConfigured() {
        return warnings.stream().filter(w -> w.contains("not auto-configured")).toList();
    }

    @Test
    public void testTypoIsLogged() {
        Main main = new Main();
        main.configure().withAutoConfigurationFailFast(false);
        main.addProperty("camel.rest.contxtPath", "/api");
        main.addProperty("camel.rest.password", "S3cr3t");
        main.start();
        try {
            List<String> list = notAutoConfigured();
            assertEquals(2, list.size(), list.toString());
            assertTrue(list.stream().anyMatch(w -> w.contains("camel.rest.contxtPath=/api")), list.toString());
            // the value of a sensitive option is masked
            assertTrue(list.stream().anyMatch(w -> w.contains("camel.rest.password=xxxxxx")), list.toString());
            assertTrue(list.stream().noneMatch(w -> w.contains("S3cr3t")), list.toString());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testNoWarningsForValidConfiguration() {
        Main main = new Main();
        main.configure().withAutoConfigurationFailFast(false);
        main.addProperty("camel.variable.greeting", "Hello");
        main.addProperty("camel.variable.global.foo", "123");
        main.addProperty("camel.rest.contextPath", "/api");
        main.addProperty("camel.lra.enabled", "false");
        main.addProperty("camel.lra.coordinatorUrl", "http://localhost:8080");
        main.addProperty("camel.opentelemetry.enabled", "false");
        main.addProperty("camel.opentelemetry.instrumentationName", "camel");
        main.start();
        try {
            List<String> list = notAutoConfigured();
            assertTrue(list.isEmpty(), list.toString());
            assertEquals("Hello", main.getCamelContext().getVariable("greeting"));
        } finally {
            main.stop();
        }
    }
}
