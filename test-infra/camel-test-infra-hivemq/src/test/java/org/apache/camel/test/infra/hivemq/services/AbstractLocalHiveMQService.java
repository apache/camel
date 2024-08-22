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
package org.apache.camel.test.infra.hivemq.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.hivemq.HiveMQContainer;

public abstract class AbstractLocalHiveMQService<T extends AbstractLocalHiveMQService<T>>
        implements HiveMQService, ContainerService<HiveMQContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractLocalHiveMQService.class);

    public static final int MQTT_PORT_DEFAULT = 1883;
    public static final int WEBSOCKET_PORT_DEFAULT = 8000;

    private final HiveMQContainer container;

    protected abstract HiveMQContainer initContainer(String imageName);

    protected AbstractLocalHiveMQService(String imageName) {
        container = initContainer(imageName)
                .withExposedPorts(MQTT_PORT_DEFAULT, WEBSOCKET_PORT_DEFAULT)
                .waitingFor(Wait.forListeningPort());
    }

    @Override
    public int getMqttPort() {
        return container.getMqttPort();
    }

    @Override
    public String getMqttHost() {
        return container.getHost();
    }

    // Using HiveMQ CE 'hivemq-allow-all-extension' for local container - No user/password required to connect
    @Override
    public String getUserName() {
        return null;
    }

    @Override
    public char[] getUserPassword() {
        return null;
    }

    @Override
    public void registerProperties() {
        System.setProperty(HiveMQProperties.HIVEMQ_SERVICE_MQTT_HOST, getMqttHost());
        System.setProperty(HiveMQProperties.HIVEMQ_SERVICE_MQTT_PORT, String.valueOf(getMqttPort()));
        System.setProperty(HiveMQProperties.HIVEMQ_SERVICE_MQTT_HOST_ADDRESS, getMqttHostAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Starting the HiveMQ container");
        container.start();

        registerProperties();
        LOG.info("HiveMQ instance running at {}", getMqttHostAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the HiveMQ container");
        container.stop();
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    @Override
    public HiveMQContainer getContainer() {
        return container;
    }
}
