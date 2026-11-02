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
 * CAMEL-26418: camel-main logs a WARN when chaos would be switched on (profile dev or camel.chaos.enabled=true) but
 * camel-chaos is not on the classpath (it is not on the test classpath of camel-main), as the camel.chaos options are
 * then ignored.
 */
@Isolated
class MainChaosWithoutJarTest {

    private static final String LOGGER = BaseMainSupport.class.getName();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    void addAppender() {
        appender = new AbstractAppender("MainChaosWithoutJarTest", null, null, true, Property.EMPTY_ARRAY) {
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

    private List<String> chaosWarnings(Main main) {
        main.start();
        try {
            return warnings.stream().filter(w -> w.contains("camel-chaos is not on the classpath")).toList();
        } finally {
            main.stop();
        }
    }

    @Test
    void enabledWithoutTheJarIsWarnedFor() {
        Main main = new Main();
        main.addInitialProperty("camel.chaos.enabled", "true");
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error");

        List<String> list = chaosWarnings(main);

        assertEquals(1, list.size(), list.toString());
        assertEquals("Chaos would be switched on (camel.chaos.enabled=true), but camel-chaos is not on the classpath,"
                     + " so the camel.chaos.* options are ignored. Add camel-chaos (for development only) to use them.",
                list.get(0));
    }

    @Test
    void profileDevWithoutTheJarIsWarnedFor() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.chaos.seed", "1");

        List<String> list = chaosWarnings(main);

        assertEquals(1, list.size(), list.toString());
        assertTrue(list.get(0).startsWith("Chaos would be switched on (profile dev)"), list.toString());
    }

    @Test
    void optionsFromJavaWithoutTheJarAreWarnedFor() {
        Main main = new Main();
        main.configure().chaosConfig().withEnabled(true);

        assertEquals(1, chaosWarnings(main).size(), warnings.toString());
    }

    @Test
    void notWhenChaosWouldStayOffAnyway() {
        // without profile dev or enabled=true chaos stays off also with the JAR (its plugin then says so)
        Main main = new Main();
        main.addInitialProperty("camel.chaos.rules", "endpoint=kafka:orders* error");

        assertTrue(chaosWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithEnabledFalse() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");
        main.addInitialProperty("camel.chaos.enabled", "false");

        assertTrue(chaosWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithProfileProd() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.chaos.enabled", "true");

        assertTrue(chaosWarnings(main).isEmpty(), warnings.toString());
    }

    @Test
    void notWithoutChaosOptions() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "dev");

        assertTrue(chaosWarnings(main).isEmpty(), warnings.toString());
    }
}
