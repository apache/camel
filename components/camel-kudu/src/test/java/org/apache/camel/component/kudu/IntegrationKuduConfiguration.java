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
package org.apache.camel.component.kudu;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.kudu.client.KuduClient;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JUnit 5 extension that starts a local Kudu cluster (one master + one tablet server) using Testcontainers and the
 * official {@code apache/kudu} Docker image.
 *
 * <p>
 * Based on <a href="https://github.com/apache/kudu/blob/master/docker/quickstart.yml"> Kudu quickstart.yml</a>.
 */
public class IntegrationKuduConfiguration implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationKuduConfiguration.class);

    static final String KUDU_IMAGE = "apache/kudu:1.18.0-ubuntu";

    private static final int KUDU_MASTER_RPC_PORT = 7051;
    private static final int KUDU_MASTER_HTTP_PORT = 8051;
    private static final int KUDU_TABLET_RPC_PORT = 7050;
    private static final int KUDU_TABLET_HTTP_PORT = 8050;

    private static final String KUDU_MASTER_ALIAS = "kudu-master";
    private static final String KUDU_TABLET_ALIAS = "kudu-tserver";

    private GenericContainer<?> masterContainer;
    private GenericContainer<?> tabletContainer;
    private KuduClient kuduClient;
    private boolean available;
    private Network network;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            LOG.warn("Docker is not available; Kudu integration tests will be skipped");
            available = false;
            return;
        }
        try {
            startContainers();
            available = true;
        } catch (Exception e) {
            LOG.warn("Failed to start Kudu containers; tests will be skipped: {}", e.getMessage(), e);
            available = false;
        }
    }

    private void startContainers() {
        network = Network.newNetwork();
        final String advertisingIp = resolveAdvertisingIpAddress();
        LOG.info("Kudu advertising IP address: {}", advertisingIp);

        masterContainer = new GenericContainer<>(KUDU_IMAGE)
                .withCommand("master")
                .withEnv("MASTER_ARGS",
                        "--unlock_unsafe_flags=true "
                                        + "--default_num_replicas=1 "
                                        + "--rpc_advertised_addresses=" + advertisingIp + ":" + KUDU_MASTER_RPC_PORT
                                        + " --webserver_advertised_addresses=" + advertisingIp + ":" + KUDU_MASTER_HTTP_PORT)
                .withEnv("KUDU_MASTERS", KUDU_MASTER_ALIAS)
                .withExposedPorts(KUDU_MASTER_RPC_PORT, KUDU_MASTER_HTTP_PORT)
                .withNetwork(network)
                .withNetworkAliases(KUDU_MASTER_ALIAS)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forListeningPort());
        masterContainer.start();

        // Fix the tablet server ports so we can advertise them before the container starts.
        // See https://github.com/testcontainers/testcontainers-java/issues/3967
        Consumer<CreateContainerCmd> portBinder = cmd -> {
            Ports portBindings = new Ports();
            portBindings.bind(ExposedPort.tcp(KUDU_TABLET_RPC_PORT), Ports.Binding.bindPort(KUDU_TABLET_RPC_PORT));
            portBindings.bind(ExposedPort.tcp(KUDU_TABLET_HTTP_PORT), Ports.Binding.bindPort(KUDU_TABLET_HTTP_PORT));
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(portBindings)
                    .withNetworkMode(network.getId());
            cmd.withHostName(KUDU_TABLET_ALIAS).withHostConfig(hostConfig);
        };

        tabletContainer = new GenericContainer<>(KUDU_IMAGE)
                .withCommand("tserver")
                .withEnv("TSERVER_ARGS",
                        "--unlock_unsafe_flags=true "
                                         + "--rpc_advertised_addresses=" + advertisingIp + ":" + KUDU_TABLET_RPC_PORT
                                         + " --webserver_advertised_addresses=" + advertisingIp + ":" + KUDU_TABLET_HTTP_PORT)
                .withEnv("KUDU_MASTERS", KUDU_MASTER_ALIAS)
                .withExposedPorts(KUDU_TABLET_RPC_PORT, KUDU_TABLET_HTTP_PORT)
                .withNetwork(network)
                .withNetworkAliases(KUDU_TABLET_ALIAS)
                .withCreateContainerCmdModifier(portBinder)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forListeningPort());
        tabletContainer.start();

        final String masterAuthority = masterContainer.getHost() + ":" + masterContainer.getMappedPort(KUDU_MASTER_RPC_PORT);
        LOG.info("Kudu master RPC accessible at {}", masterAuthority);
        LOG.info("Kudu tablet server RPC accessible at {}:{}", tabletContainer.getHost(), KUDU_TABLET_RPC_PORT);

        kuduClient = new KuduClient.KuduClientBuilder(masterAuthority).build();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (kuduClient != null) {
            try {
                kuduClient.shutdown();
            } catch (Exception e) {
                LOG.debug("Error shutting down Kudu client: {}", e.getMessage());
            }
        }
        if (tabletContainer != null) {
            tabletContainer.stop();
        }
        if (masterContainer != null) {
            masterContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    public KuduClient getClient() {
        return kuduClient;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Inject the shared KuduClient into all KuduEndpoints of the given CamelContext, bypassing URI-based client
     * creation. The client is marked as user-managed so Camel does not shut it down between test methods.
     */
    public void setupCamelContext(CamelContext context) {
        KuduClient client = this.getClient();
        assertNotNull(client);
        assertNotNull(context);
        for (Endpoint endpoint : context.getEndpoints()) {
            if (endpoint instanceof KuduEndpoint kuduEndpoint) {
                kuduEndpoint.setKuduClient(client);
                kuduEndpoint.setUserManagedClient(true);
            }
        }
    }

    /**
     * Resolves the host IP address that Kudu containers should advertise so that the Kudu Java client (running on the
     * host) can reach them.
     */
    private static String resolveAdvertisingIpAddress() {
        String dockerHostIp = DockerClientFactory.instance().dockerHostIpAddress();
        if (!"localhost".equals(dockerHostIp)) {
            // Docker is running remotely; use its IP.
            return dockerHostIp;
        }
        // Docker is local — resolve the first non-loopback, non-docker IPv4 address.
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                String name = iface.getName();
                if (name.startsWith("docker") || name.startsWith("br-")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve host IP address for Kudu advertising", e);
        }
    }
}
