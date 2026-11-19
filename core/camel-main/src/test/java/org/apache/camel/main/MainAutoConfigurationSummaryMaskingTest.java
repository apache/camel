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
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class MainAutoConfigurationSummaryMaskingTest {

    private static final String LOGGER = BaseMainSupport.class.getName();

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    void addAppender() {
        appender = new AbstractAppender("MainAutoConfigurationSummaryMaskingTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.INFO, true);
        loggerConfig.addAppender(appender, Level.INFO, null);
        config.addLogger(LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    void serverJwtKeystorePasswordIsMaskedInSummary() {
        assertMaskedInSummary("camel.server.jwtKeystorePassword", "Keyst0reS3cr3t");
    }

    @Test
    void vaultEventhubConnectionStringIsMaskedInSummary() {
        assertMaskedInSummary("camel.vault.azure.eventhubConnectionString", "C0nnS3cr3t");
    }

    private void assertMaskedInSummary(String key, String value) {
        Main main = new Main();
        main.setPropertyPlaceholderLocations("classpath:summary-masking.properties");
        main.start();
        try {
            assertTrue(messages.stream().anyMatch(m -> m.contains(key + " = xxxxxx")), messages.toString());
            assertTrue(messages.stream().noneMatch(m -> m.contains(value)), messages.toString());
        } finally {
            main.stop();
        }
    }
}
