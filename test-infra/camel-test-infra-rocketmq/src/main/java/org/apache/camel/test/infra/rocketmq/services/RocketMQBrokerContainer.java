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
package org.apache.camel.test.infra.rocketmq.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.rocketmq.common.RocketMQProperties;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

public class RocketMQBrokerContainer extends GenericContainer<RocketMQBrokerContainer> {

    private static final String BROKER_CONF
            = "/opt/rocketmq-" + RocketMQContainerInfraService.ROCKETMQ_VERSION + "/conf/broker.conf";

    private final String confName;
    private final boolean fixedPort;

    public RocketMQBrokerContainer(Network network, String confName, boolean fixedPort) {
        super(RocketMQContainerInfraService.ROCKETMQ_IMAGE);
        this.confName = confName;
        this.fixedPort = fixedPort;

        withEnv("NAMESRV_ADDR", "nameserver:9876");
        if (fixedPort) {
            ContainerEnvironmentUtil.configurePorts(this, fixedPort,
                    ContainerEnvironmentUtil.PortConfig.primary(RocketMQProperties.ROCKETMQ_BROKER1_PORT),
                    ContainerEnvironmentUtil.PortConfig.secondary(RocketMQProperties.ROCKETMQ_BROKER2_PORT),
                    ContainerEnvironmentUtil.PortConfig.secondary(RocketMQProperties.ROCKETMQ_BROKER3_PORT));
            withClasspathResourceMapping(confName + "/" + confName + ".conf", BROKER_CONF, BindMode.READ_WRITE);
        } else {
            withNetwork(network);
        }

        withTmpFs(Collections.singletonMap("/home/rocketmq/store", "rw"));
        withTmpFs(Collections.singletonMap("/home/rocketmq/logs", "rw"));
        withCommand("sh", "mqbroker", "-c", BROKER_CONF);

        waitingFor(Wait.forListeningPort());
    }

    @Override
    public void start() {
        if (!fixedPort) {
            // the broker registers its address with the name server, and the clients connect to that address: by default
            // it is the container IP, which a Docker Desktop host cannot reach. So the broker listens on a free host port,
            // bound to the same port in the container, and registers the docker host as its address.
            int port = findFreePort();
            withExposedPorts(port);
            addFixedExposedPort(port, port);
            String conf = readConf() + "\nlistenPort = " + port + "\nbrokerIP1 = " + getHost() + "\n";
            withCopyToContainer(Transferable.of(conf), BROKER_CONF);
        }
        super.start();
    }

    private String readConf() {
        String resource = confName + "/" + confName + ".conf";
        try (InputStream is = RocketMQBrokerContainer.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Broker configuration not found on the classpath: " + resource);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the broker configuration " + resource, e);
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress((InetAddress) null, 0), 1);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find a free port", e);
        }
    }
}
