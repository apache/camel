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
package org.apache.camel.dsl.jbang.run.common;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SLF4J provider of the CLI logs nothing until the CLI configures the logging, without starting the provider on the
 * classpath (log4j), and then logs with it (CAMEL-26937). The provider is not installed in this JVM: the tests use an
 * instance of their own.
 */
class CliLoggerProviderTest {

    private final AtomicInteger started = new AtomicInteger();
    private final SLF4JServiceProvider log4j = mock(SLF4JServiceProvider.class);
    private final ILoggerFactory log4jLoggers = mock(ILoggerFactory.class);
    private final MDCAdapter log4jMdc = mock(MDCAdapter.class);

    private CliLoggerProvider provider() {
        when(log4j.getLoggerFactory()).thenReturn(log4jLoggers);
        when(log4j.getMDCAdapter()).thenReturn(log4jMdc);
        return new CliLoggerProvider(() -> {
            started.incrementAndGet();
            return log4j;
        });
    }

    @Test
    void loggerLogsNothingBeforeTheLoggingIsConfigured() {
        CliLoggerProvider provider = provider();

        Logger logger = provider.getLoggerFactory().getLogger("org.apache.camel.util.FileUtil");
        logger.error("not logged");

        assertThat(logger.isErrorEnabled()).isFalse();
        assertThat(started).as("the provider on the classpath is not started").hasValue(0);
    }

    @Test
    void choosingTheProviderDoesNotStartLog4j() {
        CliLoggerProvider provider = provider();

        // SLF4J asks for the MDC adapter when it chooses the provider
        assertThat(provider.getMDCAdapter()).isNotNull();

        assertThat(started).hasValue(0);
    }

    @Test
    void loggerCreatedBeforeLogsWithTheProviderOnceConfigured() {
        CliLoggerProvider provider = provider();
        Logger early = provider.getLoggerFactory().getLogger("early");
        Logger log4jLogger = mock(Logger.class);
        when(log4jLogger.isInfoEnabled()).thenReturn(true);
        when(log4jLoggers.getLogger("early")).thenReturn(log4jLogger);

        provider.configured();
        early.info("hello {}", "world");

        assertThat(early.isInfoEnabled()).isTrue();
        verify(log4jLogger).info("hello {}", "world");
        assertThat(started).hasValue(1);
    }

    @Test
    void loggerCreatedAfterIsTheLoggerOfTheProvider() {
        CliLoggerProvider provider = provider();
        Logger log4jLogger = mock(Logger.class);
        when(log4jLoggers.getLogger("late")).thenReturn(log4jLogger);

        provider.configured();

        assertThat(provider.getLoggerFactory().getLogger("late")).isSameAs(log4jLogger);
    }

    @Test
    void configuredTwiceStartsTheProviderOnce() {
        CliLoggerProvider provider = provider();

        provider.configured();
        provider.configured();

        assertThat(started).hasValue(1);
    }

    @Test
    void mdcIsTheMdcOfTheProvider() {
        CliLoggerProvider provider = provider();

        provider.getMDCAdapter().put("camel.exchangeId", "1");

        verify(log4jMdc).put("camel.exchangeId", "1");
    }
}
