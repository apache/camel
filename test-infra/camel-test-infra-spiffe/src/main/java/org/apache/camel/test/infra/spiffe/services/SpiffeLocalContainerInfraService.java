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
package org.apache.camel.test.infra.spiffe.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.sun.security.auth.module.UnixSystem;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.spiffe.common.SpiffeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Runs a SPIRE server and agent so a test can fetch SVIDs from a real Workload API.
 * <p>
 * The agent's Workload API Unix socket is bind-mounted to a short host path (the {@code AF_UNIX} {@code sun_path} limit
 * is ~108 bytes, so a socket under a deep temp tree would fail to connect from the host). The agent runs in the host
 * PID namespace because the {@code unix} workload attestor resolves the calling process through {@code /proc}, which a
 * container cannot see otherwise; a single registration entry is created for {@code unix:uid:<the test process uid>} so
 * the test JVM is issued {@code spiffe://<trustDomain>/workload}.
 */
@InfraService(service = SpiffeInfraService.class,
              description = "SPIFFE/SPIRE server and agent exposing the Workload API",
              serviceAlias = { "spiffe" })
public class SpiffeLocalContainerInfraService implements SpiffeInfraService, ContainerService<GenericContainer<?>> {

    public static final String TRUST_DOMAIN = "example.org";
    public static final String AGENT_SPIFFE_ID = "spiffe://" + TRUST_DOMAIN + "/agent";
    public static final String WORKLOAD_SPIFFE_ID = "spiffe://" + TRUST_DOMAIN + "/workload";

    private static final Logger LOG = LoggerFactory.getLogger(SpiffeLocalContainerInfraService.class);

    private static final String SERVER_ALIAS = "spire-server";
    private static final int SERVER_PORT = 8081;
    private static final String SERVER_BIN = "/opt/spire/bin/spire-server";
    private static final String SERVER_CONF = "/opt/spire/conf/server/server.conf";
    private static final String AGENT_CONF = "/opt/spire/conf/agent/agent.conf";
    private static final String SOCKET_CONTAINER_DIR = "/tmp/spire-agent/public";
    private static final String SOCKET_FILE = "api.sock";
    private static final String SERVER_CONF_RESOURCE = "org/apache/camel/test/infra/spiffe/services/spire-server.conf";
    private static final String AGENT_CONF_RESOURCE = "org/apache/camel/test/infra/spiffe/services/spire-agent.conf";

    private final String serverImage;
    private final String agentImage;

    private Network network;
    private GenericContainer<?> server;
    private GenericContainer<?> agent;
    private Path hostSocketDir;
    private String socketPath;

    public SpiffeLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(SpiffeLocalContainerInfraService.class,
                SpiffeProperties.SPIFFE_SERVER_CONTAINER),
             LocalPropertyResolver.getProperty(SpiffeLocalContainerInfraService.class,
                     SpiffeProperties.SPIFFE_AGENT_CONTAINER));
    }

    public SpiffeLocalContainerInfraService(String serverImage, String agentImage) {
        this.serverImage = serverImage;
        this.agentImage = agentImage;
    }

    @Override
    public void initialize() {
        try {
            doInitialize();
        } catch (RuntimeException e) {
            // a partial start would leak the server container, the network and the temp socket directory, and a
            // TestServiceUtil.tryInitialize() retry would overwrite the fields and orphan them; tear down whatever
            // came up before propagating
            cleanup();
            throw e;
        }
    }

    private void doInitialize() {
        createHostSocketDir();
        network = Network.newNetwork();

        // the image entrypoint is "spire-server run", so only -config is passed; the config is copied in (no bind mount
        // needed for it) and port 8081 is exposed purely so the listening-port wait can tell when the API is up
        server = new GenericContainer<>(serverImage)
                .withNetwork(network)
                .withNetworkAliases(SERVER_ALIAS)
                .withCopyFileToContainer(MountableFile.forClasspathResource(SERVER_CONF_RESOURCE), SERVER_CONF)
                .withExposedPorts(SERVER_PORT)
                .withCommand("-config", SERVER_CONF)
                .waitingFor(Wait.forListeningPort());
        LOG.info("Starting the SPIRE server container");
        server.start();
        waitForServerHealthy();

        String joinToken = generateJoinToken();
        createWorkloadEntry();

        // the agent needs the host PID namespace so the unix workload attestor can resolve the host test process, and
        // the socket directory bind-mounted (SELinux-shared) so the host can reach the Workload API socket it creates
        agent = new GenericContainer<>(agentImage)
                .withNetwork(network)
                .withCopyFileToContainer(MountableFile.forClasspathResource(AGENT_CONF_RESOURCE), AGENT_CONF)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPidMode("host"))
                .withStartupCheckStrategy(new IsRunningStartupCheckStrategy())
                .withCommand("-config", AGENT_CONF, "-joinToken", joinToken);
        agent.addFileSystemBind(hostSocketDir.toString(), SOCKET_CONTAINER_DIR, BindMode.READ_WRITE, SelinuxContext.SHARED);
        LOG.info("Starting the SPIRE agent container");
        agent.start();
        waitForSocket();

        socketPath = "unix://" + hostSocketDir.resolve(SOCKET_FILE);
        registerProperties();
        LOG.info("SPIRE Workload API available at {}", socketPath);
    }

    private void createHostSocketDir() {
        try {
            // keep the socket path short (AF_UNIX sun_path is limited to ~108 bytes); /tmp keeps it well within that
            hostSocketDir = Files.createTempDirectory(Path.of("/tmp"), "spiffe");
            hostSocketDir.toFile().setReadable(true, false);
            hostSocketDir.toFile().setWritable(true, false);
            hostSocketDir.toFile().setExecutable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Could not create the SPIFFE Workload API socket directory", e);
        }
    }

    private void waitForServerHealthy() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Container.ExecResult result = server.execInContainer(SERVER_BIN, "healthcheck");
                if (result.getExitCode() == 0) {
                    return;
                }
                last = new RuntimeException("healthcheck exit " + result.getExitCode() + ": " + result.getStderr());
            } catch (IOException e) {
                last = new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            sleep(500);
        }
        throw new RuntimeException("SPIRE server did not become healthy in time", last);
    }

    private String generateJoinToken() {
        Container.ExecResult result = exec(server, SERVER_BIN, "token", "generate", "-spiffeID", AGENT_SPIFFE_ID,
                "-ttl", "3600");
        for (String line : result.getStdout().split("\\R")) {
            if (line.startsWith("Token: ")) {
                return line.substring("Token: ".length()).trim();
            }
        }
        throw new RuntimeException("Could not parse a join token from: " + result.getStdout());
    }

    private void createWorkloadEntry() {
        long uid = new UnixSystem().getUid();
        exec(server, SERVER_BIN, "entry", "create", "-parentID", AGENT_SPIFFE_ID, "-spiffeID", WORKLOAD_SPIFFE_ID,
                "-selector", "unix:uid:" + uid);
    }

    private void waitForSocket() {
        Path socket = hostSocketDir.resolve(SOCKET_FILE);
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(socket)) {
                return;
            }
            sleep(250);
        }
        throw new RuntimeException("The SPIRE agent did not create the Workload API socket at " + socket);
    }

    private Container.ExecResult exec(GenericContainer<?> container, String... command) {
        try {
            Container.ExecResult result = container.execInContainer(command);
            if (result.getExitCode() != 0) {
                throw new RuntimeException(
                        "Command " + String.join(" ", command) + " failed (exit "
                                           + result.getExitCode() + "): " + result.getStderr());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(SpiffeProperties.SPIFFE_SOCKET_PATH, socketPath);
        System.setProperty(SpiffeProperties.SPIFFE_TRUST_DOMAIN, TRUST_DOMAIN);
        System.setProperty(SpiffeProperties.SPIFFE_WORKLOAD_ID, WORKLOAD_SPIFFE_ID);
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the SPIRE containers");
        cleanup();
    }

    // stops whatever has been started and nulls the fields, so it is safe to call from a failed initialize() (before
    // a retry) as well as from shutdown(); every step is best-effort so one failure does not leak the rest
    private void cleanup() {
        agent = stopQuietly(agent);
        server = stopQuietly(server);
        if (network != null) {
            try {
                network.close();
            } catch (RuntimeException e) {
                // best effort on cleanup
            }
            network = null;
        }
        if (hostSocketDir != null) {
            deleteQuietly(hostSocketDir.resolve(SOCKET_FILE));
            deleteQuietly(hostSocketDir);
            hostSocketDir = null;
        }
        socketPath = null;
    }

    private static GenericContainer<?> stopQuietly(GenericContainer<?> container) {
        if (container != null) {
            try {
                container.stop();
            } catch (RuntimeException e) {
                // best effort on cleanup
            }
        }
        return null;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best effort on cleanup
        }
    }

    @Override
    public GenericContainer<?> getContainer() {
        return agent;
    }

    @Override
    public String getWorkloadApiSocketPath() {
        return socketPath;
    }

    @Override
    public String getTrustDomain() {
        return TRUST_DOMAIN;
    }

    @Override
    public String getWorkloadSpiffeId() {
        return WORKLOAD_SPIFFE_ID;
    }
}
