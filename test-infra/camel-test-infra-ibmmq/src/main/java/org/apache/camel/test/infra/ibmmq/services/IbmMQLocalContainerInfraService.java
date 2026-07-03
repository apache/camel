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
package org.apache.camel.test.infra.ibmmq.services;

import java.util.concurrent.TimeUnit;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.ibmmq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.ibmmq.common.IbmMQProperties;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = IbmMQInfraService.class,
              description = "IBM MQ is enterprise messaging middleware for reliable communication",
              serviceAlias = "ibmmq")
public class IbmMQLocalContainerInfraService implements IbmMQInfraService, ContainerService<GenericContainer<?>> {

    public static final String CONTAINER_NAME = "ibmmq";
    public static final int MQ_LISTENER_PORT = 1414;
    public static final int WEB_CONSOLE_PORT = 9443;

    private static final Logger LOG = LoggerFactory.getLogger(IbmMQLocalContainerInfraService.class);

    private final GenericContainer<?> container;

    public IbmMQLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                IbmMQLocalContainerInfraService.class, IbmMQProperties.IBM_MQ_CONTAINER));
    }

    public IbmMQLocalContainerInfraService(String containerImage) {
        container = initContainer(containerImage);
    }

    protected GenericContainer<?> initContainer(String imageName) {

        class IbmMQContainer extends GenericContainer<IbmMQContainer> {
            public IbmMQContainer() {
                super(DockerImageName.parse(imageName));

                withNetworkAliases(CONTAINER_NAME)
                        .withEnv("LICENSE", "accept")
                        .withEnv("MQ_QMGR_NAME", IbmMQProperties.DEFAULT_QMGR_NAME)
                        .withEnv("MQ_APP_PASSWORD", IbmMQProperties.DEFAULT_APP_PASSWORD)
                        .withLogConsumer(new Slf4jLogConsumer(LOG))
                        // AND the listener-port and log-message checks; a plain chained waitingFor() would replace,
                        // not combine, the strategies. WITH_INDIVIDUAL_TIMEOUTS_ONLY keeps each strategy's own timeout
                        // instead of the 30s outer cap the default WaitAllStrategy mode would impose.
                        .waitingFor(new WaitAllStrategy(WaitAllStrategy.Mode.WITH_INDIVIDUAL_TIMEOUTS_ONLY)
                                .withStrategy(Wait.forListeningPort())
                                .withStrategy(Wait.forLogMessage(
                                        ".*Queued Publish/Subscribe Daemon started for queue manager.*", 1)));

                ContainerEnvironmentUtil.configurePorts(this,
                        ContainerEnvironmentUtil.isFixedPort(IbmMQLocalContainerInfraService.class),
                        ContainerEnvironmentUtil.PortConfig.primary(MQ_LISTENER_PORT),
                        ContainerEnvironmentUtil.PortConfig.secondary(WEB_CONSOLE_PORT));
            }
        }
        return new IbmMQContainer();
    }

    @Override
    public void registerProperties() {
        System.setProperty(IbmMQProperties.IBM_MQ_CHANNEL, channel());
        System.setProperty(IbmMQProperties.IBM_MQ_QMGR_NAME, queueManager());
        System.setProperty(IbmMQProperties.IBM_MQ_PORT, String.valueOf(listenerPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the IBM MQ container");
        container.withStartupAttempts(5);

        // also starts admin console on https://localhost:9443/ibmmq/console, user: admin, password: passw0rd
        container.start();
        registerProperties();
        waitForJmsConnection();
    }

    private void waitForJmsConnection() {
        Awaitility.await("IBM MQ accepting JMS connections")
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(JMSException.class)
                .until(() -> {
                    ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(
                            queueManager(), channel(), listenerPort());
                    try (Connection connection = connectionFactory.createConnection()) {
                        connection.start();
                    }
                    return true;
                });
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the IBM MQ container");
        container.stop();
        System.clearProperty(IbmMQProperties.IBM_MQ_CHANNEL);
        System.clearProperty(IbmMQProperties.IBM_MQ_QMGR_NAME);
        System.clearProperty(IbmMQProperties.IBM_MQ_PORT);
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String channel() {
        return IbmMQProperties.DEFAULT_CHANNEL;
    }

    @Override
    public String queueManager() {
        return IbmMQProperties.DEFAULT_QMGR_NAME;
    }

    @Override
    public int listenerPort() {
        return container.getMappedPort(MQ_LISTENER_PORT);
    }
}
