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
package org.apache.camel.util;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Installing a provider other than the default, and putting the default back, is logged with the class of the provider:
 * the provider is static, so in a shared runtime it applies to every CamelContext. The first time for each provider
 * class at INFO, then at DEBUG, as a test harness installs its provider for each test or seed. Each test uses provider
 * classes of its own, as what was logged at INFO is kept for the JVM.
 */
@Isolated
class TimeSourceLogTest {

    private static final String LOGGER = TimeSource.class.getName();

    /** A provider of a test harness (only its class matters here). */
    private abstract static class HarnessClock implements TimeSource.Provider {
        @Override
        public long currentTimeMillis() {
            return 0;
        }

        @Override
        public long nanoTime() {
            return 0;
        }

        @Override
        public void sleep(long millis) {
            // virtual
        }
    }

    private static final class FirstClock extends HarnessClock {
    }

    private static final class RepeatedClock extends HarnessClock {
    }

    private static final class ResetClock extends HarnessClock {
    }

    private static final class ReplacedClock extends HarnessClock {
    }

    private static final class ReplacingClock extends HarnessClock {
    }

    private static final class QuietClock extends HarnessClock {
    }

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    void addAppender() {
        TimeSource.reset();
        appender = new AbstractAppender("TimeSourceLogTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.DEBUG, true);
        loggerConfig.addAppender(appender, Level.DEBUG, null);
        config.addLogger(LOGGER, loggerConfig);
        ctx.updateLoggers();
    }

    @AfterEach
    void removeAppender() {
        TimeSource.reset();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    private List<String> messages() {
        return events.stream().map(e -> e.getLevel() + " " + e.getMessage().getFormattedMessage()).toList();
    }

    @Test
    void installingAndPuttingTheDefaultBackAreLoggedWithTheProviderClass() {
        TimeSource.Provider previous = TimeSource.install(new FirstClock());
        TimeSource.install(previous);

        assertEquals(List.of(
                "INFO Installed TimeSource provider: " + FirstClock.class.getName(),
                "INFO Installed default TimeSource provider (replacing: " + FirstClock.class.getName() + ")"),
                messages());
    }

    @Test
    void theNextInstallsOfTheSameProviderClassAreLoggedAtDebug() {
        for (int i = 0; i < 3; i++) {
            TimeSource.install(new RepeatedClock());
            TimeSource.reset();
        }

        String name = RepeatedClock.class.getName();
        assertEquals(List.of(
                "INFO Installed TimeSource provider: " + name,
                "INFO Installed default TimeSource provider (replacing: " + name + ")",
                "DEBUG Installed TimeSource provider: " + name,
                "DEBUG Installed default TimeSource provider (replacing: " + name + ")",
                "DEBUG Installed TimeSource provider: " + name,
                "DEBUG Installed default TimeSource provider (replacing: " + name + ")"),
                messages());
    }

    @Test
    void resetIsLoggedLikeInstallingTheDefault() {
        TimeSource.install(new ResetClock());
        TimeSource.reset();

        List<String> messages = messages();
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(1).startsWith("INFO Installed default TimeSource provider"), messages.toString());
    }

    @Test
    void replacingAProviderOtherThanTheDefaultSaysWhatItReplaced() {
        TimeSource.install(new ReplacedClock());
        TimeSource.install(new ReplacingClock());

        assertEquals(List.of(
                "INFO Installed TimeSource provider: " + ReplacedClock.class.getName(),
                "INFO Installed TimeSource provider: " + ReplacingClock.class.getName() + " (replacing: "
                                                                                        + ReplacedClock.class.getName() + ")"),
                messages());
    }

    @Test
    void nothingIsLoggedWhenTheDefaultStaysInstalled() {
        TimeSource.reset();
        TimeSource.install(TimeSource.install(new QuietClock()));
        events.clear();

        TimeSource.reset();

        assertTrue(messages().isEmpty(), messages().toString());
    }
}
