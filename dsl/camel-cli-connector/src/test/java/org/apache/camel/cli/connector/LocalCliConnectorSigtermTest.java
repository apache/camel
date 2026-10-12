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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LocalCliConnectorSigtermTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // do not let the context start the connector found on the classpath, the test uses its own
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        return context;
    }

    @Test
    void terminateThreadEndsOnceCamelIsStopped() {
        LocalCliConnector connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);

        connector.sigterm();

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(context.getStatus()).isEqualTo(ServiceStatus.Stopped);
            // a non-daemon thread left behind keeps the JVM running (Spring Boot)
            assertThat(Thread.getAllStackTraces().keySet())
                    .noneMatch(t -> t.getName().endsWith("Terminate JVM task") && t.isAlive());
        });
    }
}
