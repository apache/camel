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
package org.apache.camel.component.rabbitmq.integration;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.test.testcontainers.ContainerAwareTestSupport;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemConfig;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractRabbitMQIntTest extends ContainerAwareTestSupport {
    protected static final String INITIAL_CONFIGURATION = "qpid-test-initial-config.json";
    protected static SystemLauncher systemLauncher = new SystemLauncher();

    // Container starts once per test class
    private static GenericContainer container;

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    protected boolean isStartDocker() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Helper method for creating a RabbitMQ connection to the test instance of
     * the RabbitMQ server.
     *
     * @return
     * @throws IOException
     * @throws TimeoutException
     */
    protected Connection connection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        return factory.newConnection();
    }

    /**
     * Helper method for creating a Qpid Broker-J system configuration for the
     * initiate of the local AMQP server.
     */
    protected static Map<String, Object> createQpidSystemConfig() {
        Map<String, Object> attributes = new HashMap<>();
        URL initialConfig = AbstractRabbitMQIntTest.class.getClassLoader().getResource(INITIAL_CONFIGURATION);
        attributes.put(ConfiguredObject.TYPE, "Memory");
        attributes.put(SystemConfig.INITIAL_CONFIGURATION_LOCATION, initialConfig.toExternalForm());
        attributes.put(SystemConfig.STARTUP_LOGGED_TO_SYSTEM_OUT, false);

        return attributes;
    }

    @Override
    protected GenericContainer<?> createContainer() {
        container = isStartDocker() ? DockerTestUtils.rabbitMQContainer() : null;
        return (GenericContainer<?>)container;
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();

        if (container != null) {
            container.stop();
        }
    }

    protected long containerShutdownTimeout() {
        return TimeUnit.MINUTES.toSeconds(1L);
    }
}
