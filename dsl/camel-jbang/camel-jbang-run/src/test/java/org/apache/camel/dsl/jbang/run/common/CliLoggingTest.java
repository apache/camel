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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CLI tells its SLF4J provider when it configured the logging, also when SLF4J chooses the provider only after that
 * (CAMEL-26937).
 */
class CliLoggingTest {

    private final AtomicInteger configured = new AtomicInteger();

    @BeforeEach
    @AfterEach
    void newJvm() {
        CliLogging.reset();
    }

    @Test
    void providerChosenBeforeTheLoggingIsConfigured() {
        CliLogging.bound(configured::incrementAndGet);
        assertThat(configured).hasValue(0);

        CliLogging.loggingConfigured();

        assertThat(configured).hasValue(1);
    }

    @Test
    void providerChosenAfterTheLoggingIsConfigured() {
        CliLogging.loggingConfigured();

        CliLogging.bound(configured::incrementAndGet);

        assertThat(configured).hasValue(1);
    }

    @Test
    @SetSystemProperty(key = CliLogging.PROVIDER_PROPERTY, value = CliLogging.PROVIDER)
    void deferredUntilTheLoggingIsConfigured() {
        assertThat(CliLogging.isDeferred()).isTrue();

        CliLogging.loggingConfigured();

        assertThat(CliLogging.isDeferred()).isFalse();
    }

    @Test
    @ClearSystemProperty(key = CliLogging.PROVIDER_PROPERTY)
    void notDeferredWithAnotherProvider() {
        // a JVM that did not start from the main method of the CLI, such as the tests of the commands
        assertThat(CliLogging.isDeferred()).isFalse();
    }

    @Test
    void providerName() {
        assertThat(CliLogging.PROVIDER).isEqualTo(CliLoggerProvider.class.getName());
    }
}
