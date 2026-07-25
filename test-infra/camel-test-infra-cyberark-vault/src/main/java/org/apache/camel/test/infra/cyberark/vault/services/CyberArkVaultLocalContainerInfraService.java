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
package org.apache.camel.test.infra.cyberark.vault.services;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.cyberark.vault.common.CyberArkVaultProperties;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A CyberArk Conjur Vault service backed by containers.
 * <p>
 * Unlike most other infra services this one starts <em>two</em> containers: Conjur cannot run standalone, it requires a
 * PostgreSQL backend. Both are wired together on a private {@link Network}, and only Conjur is exposed to the host.
 * <p>
 * Conjur has no admin credentials until an account is created, so the API key is generated on startup by running
 * {@code conjurctl account create} inside the container.
 * <p>
 * Note that Conjur listens on port 80 inside the container. Tests get a random host port; when running under
 * {@code camel infra run} (fixed port mode) set {@code camel.infra.port} to avoid binding the privileged host port 80.
 */
@InfraService(service = CyberArkVaultInfraService.class,
              description = "CyberArk Conjur is an open source secrets management solution",
              serviceAlias = "cyberark",
              serviceImplementationAlias = "conjur")
public class CyberArkVaultLocalContainerInfraService
        implements CyberArkVaultInfraService, ContainerService<GenericContainer<?>> {

    public static final String CONTAINER_NAME = "cyberark-vault";

    private static final Logger LOG = LoggerFactory.getLogger(CyberArkVaultLocalContainerInfraService.class);

    private static final String DEFAULT_ACCOUNT = "myConjurAccount";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String POSTGRES_NETWORK_ALIAS = "database";
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASSWORD = "SuperSecret";
    private static final String POSTGRES_DB = "postgres";

    /**
     * Fixed throwaway key used to encrypt the Conjur database. This is the key published in the Conjur quickstart
     * documentation - it guards nothing but ephemeral test data.
     */
    private static final String DATA_KEY = "W0BuL24xJMVfGNTKRxcC4xv76cKE7wNJh0AKXdvmnxk=";

    private static final Pattern API_KEY_PATTERN = Pattern.compile("API key for admin:\\s*(\\S+)");

    private final Network network;
    private final GenericContainer<?> postgresContainer;
    private final GenericContainer<?> container;

    private String apiKey;

    public CyberArkVaultLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                CyberArkVaultLocalContainerInfraService.class,
                CyberArkVaultProperties.CYBERARK_VAULT_CONTAINER),
             LocalPropertyResolver.getProperty(
                     CyberArkVaultLocalContainerInfraService.class,
                     CyberArkVaultProperties.CYBERARK_VAULT_POSTGRES_CONTAINER));
    }

    public CyberArkVaultLocalContainerInfraService(String containerImage, String postgresContainerImage) {
        network = Network.newNetwork();
        postgresContainer = initPostgresContainer(postgresContainerImage);
        container = initContainer(containerImage, CONTAINER_NAME);

        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    protected GenericContainer<?> initPostgresContainer(String imageName) {
        GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse(imageName));

        postgres.withNetwork(network)
                .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
                .withEnv("POSTGRES_USER", POSTGRES_USER)
                .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
                .withEnv("POSTGRES_DB", POSTGRES_DB)
                // the message is logged once by the bootstrap server and once by the real one
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                        .withStartupTimeout(Duration.ofMinutes(2)));

        return postgres;
    }

    protected GenericContainer<?> initContainer(String imageName, String containerName) {
        final Logger containerLog = LoggerFactory.getLogger("container." + containerName);
        final Consumer<OutputFrame> logConsumer = new Slf4jLogConsumer(containerLog);

        GenericContainer<?> conjur = new GenericContainer<>(DockerImageName.parse(imageName));

        conjur.withNetwork(network)
                .withNetworkAliases(containerName)
                .withEnv("DATABASE_URL",
                        String.format("postgres://%s:%s@%s/%s",
                                POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_NETWORK_ALIAS, POSTGRES_DB))
                .withEnv("CONJUR_DATA_KEY", DATA_KEY)
                .withEnv("CONJUR_AUTHENTICATORS", "authn")
                .withCommand("server")
                .withLogConsumer(logConsumer)
                .waitingFor(Wait.forLogMessage(".*Listening on http://.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));

        ContainerEnvironmentUtil.configurePort(conjur,
                ContainerEnvironmentUtil.isFixedPort(this.getClass()),
                CyberArkVaultProperties.DEFAULT_SERVICE_PORT);

        return conjur;
    }

    @Override
    public void registerProperties() {
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_HOST, host());
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_PORT, String.valueOf(port()));
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_URL, url());
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_ACCOUNT, account());
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_USERNAME, username());
        System.setProperty(CyberArkVaultProperties.CYBERARK_VAULT_API_KEY, apiKey());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the CyberArk Conjur database container");
        postgresContainer.start();

        LOG.info("Trying to start the CyberArk Conjur container");
        container.withStartupAttempts(5);
        container.start();

        apiKey = createAdminAccount();

        registerProperties();
        LOG.info("CyberArk Conjur running at {} on account {}", url(), account());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the CyberArk Conjur container");
        container.stop();
        postgresContainer.stop();

        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_HOST);
        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_PORT);
        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_URL);
        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_ACCOUNT);
        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_USERNAME);
        System.clearProperty(CyberArkVaultProperties.CYBERARK_VAULT_API_KEY);
    }

    /**
     * Creates the Conjur account and returns the generated admin API key.
     * <p>
     * Conjur starts accepting connections slightly before it is able to serve them, so this is retried until it
     * succeeds rather than run once.
     */
    private String createAdminAccount() {
        return Awaitility.await()
                .atMost(2, TimeUnit.MINUTES)
                .pollDelay(Duration.ZERO)
                .pollInterval(2, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(this::doCreateAdminAccount, Objects::nonNull);
    }

    private String doCreateAdminAccount() throws IOException, InterruptedException {
        Container.ExecResult result
                = container.execInContainer("conjurctl", "account", "create", DEFAULT_ACCOUNT);

        Matcher matcher = API_KEY_PATTERN.matcher(result.getStdout());
        if (matcher.find()) {
            return matcher.group(1);
        }

        // The account outlives a container restart, in which case creating it fails and the
        // already generated key has to be retrieved instead
        result = container.execInContainer("conjurctl", "role", "retrieve-key", DEFAULT_ACCOUNT + ":user:admin");
        String key = result.getStdout().trim();

        return key.isEmpty() ? null : key;
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String account() {
        return DEFAULT_ACCOUNT;
    }

    @Override
    public String username() {
        return DEFAULT_USERNAME;
    }

    @Override
    public String apiKey() {
        return apiKey;
    }

    @Override
    public int port() {
        return container.getMappedPort(CyberArkVaultProperties.DEFAULT_SERVICE_PORT);
    }

    @Override
    public String host() {
        return container.getHost();
    }
}
