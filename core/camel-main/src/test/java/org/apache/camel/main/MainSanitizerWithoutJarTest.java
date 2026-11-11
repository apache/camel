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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * camel-main logs a WARN when the exchange sanitizer would be switched on (profile dev or camel.sanitizer.enabled=true)
 * but camel-sanitizer is not on the classpath (it is not on the test classpath of camel-main), as the camel.sanitizer
 * options are then ignored.
 */
@Isolated
class MainSanitizerWithoutJarTest {

    private static final String LOGGER = BaseMainSupport.class.getName();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    void addAppender() {
        appender = new AbstractAppender("MainSanitizerWithoutJarTest", null, null, true, Property.EMPTY_ARRAY) {
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
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    private List<String> sanitizerWarnings(Main main) {
        main.start();
        try {
            return warnings.stream().filter(w -> w.contains("camel-sanitizer is not on the classpath")).toList();
        } finally {
            main.stop();
        }
    }

    @Test
    void enabledWithoutTheJarIsWarnedFor() {
        Main main = new Main();
        main.addInitialProperty("camel.sanitizer.enabled", "true");

        List<String> list = sanitizerWarnings(main);

        assertEquals(1, list.size(), list.toString());
        assertEquals("The exchange sanitizer would be switched on (camel.sanitizer.enabled=true), but camel-sanitizer is"
                     + " not on the classpath, so the camel.sanitizer.* options are ignored. Add camel-sanitizer (for"
                     + " development and tests only) to use them.",
                list.get(0));
    }

    @Test
    void profileDevWithoutTheJarIsWarnedFor() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.sanitizer.stackDepth", "0");

        List<String> list = sanitizerWarnings(main);

        assertEquals(1, list.size(), list.toString());
        assertTrue(list.get(0).startsWith("The exchange sanitizer would be switched on (profile dev)"), list.toString());
    }

    @Test
    void optionsFromJavaWithoutTheJarAreWarnedFor() {
        Main main = new Main();
        main.configure().sanitizerConfig().withEnabled(true);

        assertEquals(1, sanitizerWarnings(main).size(), warnings.toString());
    }

    @Test
    void notWhenTheSanitizerWouldStayOffAnyway() {
        // without profile dev or enabled=true the sanitizer stays off also with the JAR
        Main main = new Main();
        main.addInitialProperty("camel.sanitizer.stackDepth", "0");

        assertTrue(sanitizerWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithEnabledFalse() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.sanitizer.enabled", "false");

        assertTrue(sanitizerWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithProfileProd() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.sanitizer.enabled", "true");

        assertTrue(sanitizerWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithoutSanitizerOptions() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");

        assertTrue(sanitizerWarnings(main).isEmpty(), warnings.toString());
    }
}
