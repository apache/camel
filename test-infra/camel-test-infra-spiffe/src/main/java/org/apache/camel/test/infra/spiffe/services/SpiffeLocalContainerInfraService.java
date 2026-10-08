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

import com.github.dockerjava.api.model.Info;
import com.sun.security.auth.module.UnixSystem;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.spiffe.common.SpiffeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
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
 * <p>
 * This assumes a Linux Docker host: the host PID namespace, the Unix-socket bind mount and the {@code unix:uid}
 * selector all rely on the test JVM and the containers sharing one Linux kernel. It is not expected to work on Docker
 * Desktop (macOS/Windows, where the daemon runs in a separate VM). Under rootless Podman the uid is remapped (the host
 * uid appears as uid 0 inside the container), and this class handles that transparently.
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
    private static final String AGENT_BIN = "/opt/spire/bin/spire-agent";
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
        waitForAgentHealthy();

        socketPath = "unix://" + hostSocketDir.resolve(SOCKET_FILE);
        registerProperties();
        LOG.info("SPIRE Workload API available at {}", socketPath);
    }

    private void createHostSocketDir() {
        try {
            // keep the socket path short (AF_UNIX sun_path is limited to ~108 bytes); /tmp keeps it well within that
            hostSocketDir = Files.createTempDirectory(Path.of("/tmp"), "spiffe");
            // world-accessible so both sides of the bind mount can use the socket: the agent container (running as
            // root, which creates the socket) and the host test process may run under different uids, so an
            // owner-only directory would stop one of them traversing it to create or connect to the socket
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
        long uid = resolveWorkloadUid();
        LOG.info("Registering workload entry for unix:uid:{}", uid);
        exec(server, SERVER_BIN, "entry", "create", "-parentID", AGENT_SPIFFE_ID, "-spiffeID", WORKLOAD_SPIFFE_ID,
                "-selector", "unix:uid:" + uid);
    }

    /**
     * Returns the UID of this JVM process as it will appear to the SPIRE unix workload attestor.
     * <p>
     * Under rootless Podman and Docker the container's user namespace maps the host owner uid to container uid 0, so
     * the agent sees the test process as uid 0 when reading {@code /proc/<pid>/status} through the host PID namespace.
     * On a standard (root-owned) Docker daemon no such remapping occurs and the real host uid is used.
     * <p>
     * Rootless mode is detected via the Docker info API: Podman returns {@code "Rootless": true} in the JSON response
     * (captured in {@code rawValues} since the docker-java model does not have a typed field for it).
     */
    private long resolveWorkloadUid() {
        Info info = DockerClientFactory.instance().client().infoCmd().exec();
        if (isPodmanRootless(info) || isDockerRootless(info)) {
            // Under rootless Podman and Docker the host uid of the container owner (us) is remapped to uid 0 inside every
            // container, so the agent's unix workload attestor sees the test process as uid 0.
            LOG.debug("Rootless container runtime detected; using uid 0 for workload entry selector");
            return 0L;
        }
        long uid = new UnixSystem().getUid();
        LOG.debug("Standard container runtime; using host uid {} for workload entry selector", uid);
        return uid;
    }

    private boolean isPodmanRootless(Info info) {
        Object podmanRootless = info.getRawValues().get("Rootless");
        return Boolean.TRUE.equals(podmanRootless);
    }

    private boolean isDockerRootless(Info info) {
        return info.getSecurityOptions() != null && info.getSecurityOptions().stream().anyMatch(o -> o.contains("rootless"));
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

    private void waitForAgentHealthy() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Container.ExecResult result = agent.execInContainer(AGENT_BIN, "healthcheck",
                        "-socketPath", SOCKET_CONTAINER_DIR + "/" + SOCKET_FILE);
                if (result.getExitCode() == 0) {
                    return;
                }
                last = new RuntimeException("agent healthcheck exit " + result.getExitCode() + ": " + result.getStderr());
            } catch (IOException e) {
                last = new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            sleep(500);
        }
        throw new RuntimeException("SPIRE agent did not become healthy in time", last);
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
