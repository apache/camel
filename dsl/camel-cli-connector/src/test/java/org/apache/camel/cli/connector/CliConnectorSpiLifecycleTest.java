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
package org.apache.camel.cli.connector;

import java.nio.file.Path;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the shared CLI connector is discovered through Camel's SPI and managed by CamelContext. The Spring Boot
 * starter and Camel Quarkus extension adapt their platform runtimes to this same SPI.
 */
@Isolated
class CliConnectorSpiLifecycleTest {

    @TempDir
    Path userHome;

    @Test
    void classpathConnectorIsAutoRegisteredAndStartedByCamelContext() throws Exception {
        String previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            CliConnectorFactory factory = context.getCamelContextExtension().getContextPlugin(CliConnectorFactory.class);
            assertThat(factory).isInstanceOf(DefaultCliConnectorFactory.class);

            CliConnector connector = context.hasService(CliConnector.class);
            assertThat(connector).isInstanceOf(LocalCliConnector.class);
            assertThat(((LocalCliConnector) connector).isStarted()).isTrue();
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousUserHome);
            }
        }
    }
}
