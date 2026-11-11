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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import com.sun.security.auth.module.UnixSystem;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.spiffe.common.SpiffeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import static org.awaitility.Awaitility.await;

/**
 * Runs a SPIRE server and agent so a test can fetch SVIDs from a real Workload API.
 * <p>
 * The agent's Workload API Unix socket is bind-mounted to a short host path (the {@code AF_UNIX} {@code sun_path} limit
 * is ~108 bytes, so a socket under a deep temp tree would fail to connect from the host). The agent runs in the host
 * PID namespace because the {@code unix} workload attestor resolves the calling process through {@code /proc}, which a
 * container cannot see otherwise; a single registration entry is created for {@code unix:uid:<the test process uid>} so
 * the test JVM is issued {@code spiffe://<trustDomain>/workload}. That uid is the one the agent sees, translated
 * through the user namespace of the agent container, so rootless Podman and rootless Docker work as well.
 * <p>
 * The service is only handed over once the agent holds that entry. The SPIRE server passes a new entry on to its agents
 * when it next rebuilds its entry cache (every 5 seconds by default), and the agent picks it up on its next sync; until
 * then the Workload API is up and healthy but answers {@code PERMISSION_DENIED: no identity issued}.
 * <p>
 * This assumes a Linux container host: the host PID namespace, the Unix-socket bind mount and the {@code unix:uid}
 * selector all rely on the test JVM and the containers sharing one Linux kernel. It is not expected to work on Docker
 * Desktop (macOS/Windows, where the daemon runs in a separate VM).
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
    private final Duration workloadSvidTtl;
    private final Duration caTtl;

    private Network network;
    private GenericContainer<?> server;
    private GenericContainer<?> agent;
    private Path hostSocketDir;
    private String socketPath;

    public SpiffeLocalContainerInfraService() {
        this(Duration.ofHours(1), Duration.ofHours(24));
    }

    /** Uses a shorter registration TTL for rotation tests without changing the shared fixture defaults. */
    public SpiffeLocalContainerInfraService(Duration workloadSvidTtl, Duration caTtl) {
        this(LocalPropertyResolver.getProperty(SpiffeLocalContainerInfraService.class,
                SpiffeProperties.SPIFFE_SERVER_CONTAINER),
             LocalPropertyResolver.getProperty(SpiffeLocalContainerInfraService.class,
                     SpiffeProperties.SPIFFE_AGENT_CONTAINER),
             workloadSvidTtl, caTtl);
    }

    public SpiffeLocalContainerInfraService(String serverImage, String agentImage) {
        this(serverImage, agentImage, Duration.ofHours(1), Duration.ofHours(24));
    }

    public SpiffeLocalContainerInfraService(String serverImage, String agentImage, Duration workloadSvidTtl, Duration caTtl) {
        if (workloadSvidTtl == null || workloadSvidTtl.getSeconds() < 1) {
            throw new IllegalArgumentException("Workload SVID TTL must be at least one second");
        }
        if (caTtl == null || caTtl.compareTo(workloadSvidTtl.multipliedBy(6)) < 0) {
            throw new IllegalArgumentException("CA TTL must be at least six times the workload SVID TTL");
        }
        this.caTtl = caTtl;
        this.workloadSvidTtl = workloadSvidTtl;
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
                .withCopyToContainer(Transferable.of(serverConfiguration()), SERVER_CONF)
                .withExposedPorts(SERVER_PORT)
                .withCommand("-config", SERVER_CONF)
                .waitingFor(Wait.forListeningPort());
        LOG.info("Starting the SPIRE server container");
        server.start();
        waitForServerHealthy();

        String joinToken = generateJoinToken();

        // the agent needs the host PID namespace so the unix workload attestor can resolve the host test process, and
        // the socket directory bind-mounted (SELinux-shared) so the host can reach the Workload API socket it creates
        agent = new GenericContainer<>(agentImage)
                .withNetwork(network)
                .withCopyToContainer(Transferable.of(agentConfiguration()), AGENT_CONF)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPidMode("host"))
                .withStartupCheckStrategy(new IsRunningStartupCheckStrategy())
                .withCommand("-config", AGENT_CONF, "-joinToken", joinToken);
        agent.addFileSystemBind(hostSocketDir.toString(), SOCKET_CONTAINER_DIR, BindMode.READ_WRITE, SelinuxContext.SHARED);
        LOG.info("Starting the SPIRE agent container");
        agent.start();
        waitForSocket();

        // the entry is only created now that the agent runs, as its selector needs the uid the agent sees, which
        // depends on the user namespace of the agent container
        createWorkloadEntry(workloadUid());
        waitForWorkloadEntry();

        socketPath = "unix://" + hostSocketDir.resolve(SOCKET_FILE);
        registerProperties();
        LOG.info("SPIRE Workload API available at {}", socketPath);
    }

    String agentConfiguration() {
        try (var input = getClass().getClassLoader().getResourceAsStream(AGENT_CONF_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing SPIRE agent configuration");
            }
            // Restarting a process must retain its node key, rather than try to reuse a consumed join token.
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (workloadSvidTtl.equals(Duration.ofHours(1)) && caTtl.equals(Duration.ofHours(24))) {
                return configuration;
            }
            return configuration
                    .replace("KeyManager \"memory\" {\n        plugin_data {}",
                            "KeyManager \"disk\" {\n        plugin_data { directory = \"/tmp/spire-agent/keys\" }");
        } catch (IOException e) {
            throw new RuntimeException("Could not read SPIRE agent configuration", e);
        }
    }

    String serverConfiguration() {
        try (var input = getClass().getClassLoader().getResourceAsStream(SERVER_CONF_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing SPIRE server configuration");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("ca_ttl = \"24h\"", "ca_ttl = \"" + caTtl.getSeconds() + "s\"")
                    .replace("default_x509_svid_ttl = \"1h\"",
                            "default_x509_svid_ttl = \"" + workloadSvidTtl.getSeconds() + "s\"");
        } catch (IOException e) {
            throw new RuntimeException("Could not read SPIRE server configuration", e);
        }
    }

    /**
     * Stops the existing container without removing its data. Use the custom-TTL fixture, which persists the node key,
     * when restarting: the default in-memory key manager cannot recover a consumed join token.
     */
    public void stopAgent() {
        agent.getDockerClient().stopContainerCmd(agent.getContainerId()).withTimeout(10).exec();
    }

    /** Restarts the same agent, retaining its node attestation and Workload API socket location. */
    public void startAgent() {
        agent.getDockerClient().startContainerCmd(agent.getContainerId()).exec();
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
        await().alias("SPIRE server healthcheck").atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500)).ignoreException(IOException.class)
                .until(() -> server.execInContainer(SERVER_BIN, "healthcheck").getExitCode() == 0);
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

    private void createWorkloadEntry(long uid) {
        LOG.info("Registering {} for the selector unix:uid:{}", WORKLOAD_SPIFFE_ID, uid);
        exec(server, SERVER_BIN, "entry", "create", "-parentID", AGENT_SPIFFE_ID, "-spiffeID", WORKLOAD_SPIFFE_ID,
                "-selector", "unix:uid:" + uid, "-x509SVIDTTL", Long.toString(workloadSvidTtl.getSeconds()));
    }

    /**
     * Returns the uid of this JVM as the agent's unix workload attestor sees it. The attestor reads the uid from
     * {@code /proc/<pid>/status} inside the agent container's user namespace, so the kernel translates it through the
     * {@code uid_map} of that namespace (see {@link #uidInNamespace(long, List, long)}).
     */
    private long workloadUid() {
        long uid = new UnixSystem().getUid();
        Path uidMap = Path.of("/proc", String.valueOf(agent.getContainerInfo().getState().getPidLong()), "uid_map");
        try {
            long overflowUid = Long.parseLong(Files.readString(Path.of("/proc/sys/kernel/overflowuid")).trim());
            return uidInNamespace(uid, Files.readAllLines(uidMap), overflowUid);
        } catch (IOException | NumberFormatException e) {
            // no mapping to read (for example the engine is not on this host): use the uid as is, as a rootful engine does
            LOG.warn("Could not read the user namespace mapping of the SPIRE agent from {}, using uid {} unchanged", uidMap,
                    uid, e);
            return uid;
        }
    }

    /**
     * Translates a uid of this host into a user namespace, given the {@code uid_map} of the namespace as read from this
     * host. Each line maps the {@code <count>} uids starting at {@code <outside>} onto the uids starting at
     * {@code <inside>}, and a uid outside every range is seen as the kernel overflow uid. So the uid is unchanged under
     * a rootful engine, becomes 0 for the engine owner under rootless Podman or Docker, and becomes the overflow uid
     * when the namespace does not map it.
     */
    static long uidInNamespace(long uid, List<String> uidMap, long overflowUid) {
        for (String line : uidMap) {
            // <inside> <outside> <count>
            String[] range = line.trim().split("\\s+");
            long inside = Long.parseLong(range[0]);
            long outside = Long.parseLong(range[1]);
            long count = Long.parseLong(range[2]);
            if (uid >= outside && uid - outside < count) {
                return inside + uid - outside;
            }
        }
        return overflowUid;
    }

    private void waitForWorkloadEntry() {
        // the agent logs the creation of the workload X509-SVID once the entry is in its cache, which is when a fetch
        // for this workload stops being answered with PERMISSION_DENIED "no identity issued"
        try {
            Wait.forLogMessage(".*Creating X509-SVID.*" + Pattern.quote(WORKLOAD_SPIFFE_ID) + ".*", 1)
                    .withStartupTimeout(Duration.ofSeconds(60))
                    .waitUntilReady(agent);
        } catch (ContainerLaunchException e) {
            throw new RuntimeException(
                    "The SPIRE agent did not receive the registration entry for " + WORKLOAD_SPIFFE_ID
                                       + " in time, agent log:\n"
                                       + agent.getLogs(),
                    e);
        }
    }

    private void waitForSocket() {
        Path socket = hostSocketDir.resolve(SOCKET_FILE);
        await().alias("SPIRE Workload API socket " + socket).atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(250)).until(() -> Files.exists(socket));
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
