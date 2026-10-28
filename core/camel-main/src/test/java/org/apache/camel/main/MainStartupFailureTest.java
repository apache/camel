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

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.RouteBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Camel Main that fails to start logs why, as the failure leaves run() to the caller and was otherwise only printed
 * by the JVM, on stderr; the JVM then does not print it a second time.
 */
@Isolated
class MainStartupFailureTest {

    private static final String LOGGER = MainSupport.class.getName();

    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<Throwable> printed = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private Thread.UncaughtExceptionHandler original;

    @BeforeEach
    void setUp() {
        appender = new AbstractAppender("MainStartupFailureTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.ERROR) {
                    errors.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER, Level.ERROR, true);
        loggerConfig.addAppender(appender, Level.ERROR, null);
        config.addLogger(LOGGER, loggerConfig);
        ctx.updateLoggers();

        original = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> printed.add(e));
    }

    @AfterEach
    void tearDown() {
        Thread.currentThread().setUncaughtExceptionHandler(original instanceof ThreadGroup ? null : original);
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
        appender.stop();
    }

    @Test
    void aFailureToStartIsLoggedAndNotPrintedAgain() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                throw new IllegalStateException("cannot configure the routes");
            }
        });

        Exception e = assertThrows(Exception.class, main::run);

        assertThat(errors).singleElement().satisfies(m -> assertThat(m).startsWith("Error starting Camel: ")
                .contains("cannot configure the routes"));
        // the failure leaves main, as the launcher of a fat jar wraps it: the JVM does not print it again
        Thread.currentThread().getUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), new InvocationTargetException(e));
        assertThat(printed).isEmpty();
    }

    @Test
    void anotherExceptionIsStillPrinted() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                throw new IllegalStateException("cannot configure the routes");
            }
        });
        assertThrows(Exception.class, main::run);

        IllegalArgumentException other = new IllegalArgumentException("another");
        Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), other);
        assertThat(printed).containsExactly(other);
    }
}
