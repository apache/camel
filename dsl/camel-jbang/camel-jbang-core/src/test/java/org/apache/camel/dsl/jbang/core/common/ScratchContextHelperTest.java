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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Path;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.KameletMain;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26396: the CamelContexts the CLI runs for itself start without the CLI connector, which would write the status
 * file of this process in {@code ~/.camel} (under {@code user.home}, a directory of the test here).
 */
class ScratchContextHelperTest {

    @TempDir
    Path userHome;

    private String previousUserHome;

    @BeforeEach
    void useHome() {
        previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", previousUserHome);
    }

    @Test
    void theConnectorIsOnTheClasspath() throws Exception {
        // what the helper turns off: a context the CLI runs is managed by the CLI connector by default
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            CliConnectorFactory factory = context.getCamelContextExtension().getContextPlugin(CliConnectorFactory.class);
            assertThat(factory).isNotNull();
            assertThat(factory.isEnabled()).isTrue();
        }
    }

    @Test
    void aContextThatIsNotBuiltYetStartsWithoutTheConnector() throws Exception {
        try (DefaultCamelContext context = ScratchContextHelper.withoutCliConnector(new DefaultCamelContext(false))) {
            context.start();
            assertThat(context.hasService(CliConnector.class)).isNull();
            assertThat(context.getCamelContextExtension().getContextPlugin(CliConnectorFactory.class).isEnabled()).isFalse();
        }
        assertThat(userHome.resolve(".camel")).doesNotExist();
    }

    @Test
    void aBuiltContextStartsWithoutTheConnector() throws Exception {
        try (DefaultCamelContext context = ScratchContextHelper.withoutCliConnector(new DefaultCamelContext())) {
            context.start();
            assertThat(context.hasService(CliConnector.class)).isNull();
        }
        assertThat(userHome.resolve(".camel")).doesNotExist();
    }

    @Test
    void aCamelMainStartsWithoutTheConnector() throws Exception {
        // as camel version list downloads with Camel Main
        KameletMain main = new KameletMain("camel.jbang");
        main.bind(CliConnectorFactory.FACTORY, ScratchContextHelper.disabledCliConnectorFactory());
        main.setDownload(false);
        try {
            main.start();
            assertThat(main.getCamelContext().hasService(CliConnector.class)).isNull();
        } finally {
            main.stop();
        }
        assertThat(userHome.resolve(".camel")).doesNotExist();
    }
}
