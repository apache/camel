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
package org.apache.camel.test.infra.postgres.services;

import java.util.Arrays;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.postgres.common.PostgresProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = PostgresInfraService.class,
              description = "PostgreSQL is an open source object-relational database",
              serviceAlias = { "postgres" }, uiSupported = true)
public class PostgresLocalContainerInfraService implements PostgresInfraService, ContainerService<PostgreSQLContainer> {

    public static final String DEFAULT_POSTGRES_CONTAINER
            = LocalPropertyResolver.getProperty(PostgresLocalContainerInfraService.class,
                    PostgresProperties.POSTGRES_CONTAINER);

    private static final String PGADMIN_CONTAINER_IMAGE = "pgadmin.container.image";
    private static final int PGADMIN_PORT = 5050;
    private static final String PGADMIN_EMAIL = "admin@camel.apache.org";
    // NOTE: default value used for local development only
    private static final String PGADMIN_PASSWORD = "admin"; // NOSONAR

    private static final Logger LOG = LoggerFactory.getLogger(PostgresLocalContainerInfraService.class);
    private final PostgreSQLContainer container;
    private GenericContainer<?> uiContainer;

    public PostgresLocalContainerInfraService() {
        this(DEFAULT_POSTGRES_CONTAINER);
    }

    public PostgresLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public PostgresLocalContainerInfraService(PostgreSQLContainer container) {
        this.container = container;
    }

    protected PostgreSQLContainer initContainer(String imageName) {
        class TestInfraPostgreSQLContainer extends PostgreSQLContainer {
            public TestInfraPostgreSQLContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName)
                        .asCompatibleSubstituteFor("postgres"));

                ContainerEnvironmentUtil.configurePort(this, fixedPort, 5432);
                withLogConsumer(new Slf4jLogConsumer(LOG));

                // PostgreSQL disables prepared transactions by default
                // (max_prepared_transactions = 0), which rejects the PREPARE TRANSACTION
                // issued by XA two-phase commit. Append to the command configured by
                // testcontainers ("postgres -c fsync=off") instead of replacing it.
                String[] command = getCommandParts();
                String[] augmented = Arrays.copyOf(command, command.length + 2);
                augmented[command.length] = "-c";
                augmented[command.length + 1] = "max_prepared_transactions=100";
                setCommand(augmented);
            }
        }

        return new TestInfraPostgreSQLContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(PostgresProperties.SERVICE_ADDRESS, getServiceAddress());
        System.setProperty(PostgresProperties.HOST, host());
        System.setProperty(PostgresProperties.PORT, String.valueOf(port()));
        System.setProperty(PostgresProperties.USERNAME, container.getUsername());
        System.setProperty(PostgresProperties.PASSWORD, container.getPassword());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Postgres container");
        container.start();

        registerProperties();
        LOG.info("Postgres instance running at {}", getServiceAddress());

        if (ContainerEnvironmentUtil.isWithUi()) {
            try {
                startUiContainer();
            } catch (Exception e) {
                LOG.warn("Failed to start pgAdmin UI container: {}", e.getMessage(), e);
            }
        }
    }

    private void startUiContainer() {
        String uiImage = LocalPropertyResolver.getProperty(
                PostgresLocalContainerInfraService.class, PGADMIN_CONTAINER_IMAGE);
        int pgPort = ContainerEnvironmentUtil.getConfiguredPort(5432);
        Testcontainers.exposeHostPorts(pgPort);

        String serversJson = String.format(
                "{\"Servers\":{\"1\":{\"Name\":\"camel-infra\",\"Group\":\"Servers\","
                                           + "\"Host\":\"host.testcontainers.internal\",\"Port\":%d,"
                                           + "\"MaintenanceDB\":\"postgres\",\"Username\":\"%s\","
                                           + "\"PassFile\":\"/tmp/pgpassfile\",\"SSLMode\":\"prefer\"}}}",
                pgPort, container.getUsername());
        String pgpassFile = String.format(
                "host.testcontainers.internal:%d:postgres:%s:%s",
                pgPort, container.getUsername(), container.getPassword());

        uiContainer = new GenericContainer<>(uiImage)
                .withEnv("PGADMIN_DEFAULT_EMAIL", PGADMIN_EMAIL)
                .withEnv("PGADMIN_DEFAULT_PASSWORD", PGADMIN_PASSWORD)
                .withEnv("PGADMIN_LISTEN_PORT", String.valueOf(PGADMIN_PORT))
                .withEnv("PGADMIN_CONFIG_SERVER_MODE", "False")
                .withEnv("PGADMIN_CONFIG_MASTER_PASSWORD_REQUIRED", "False")
                .withCopyToContainer(Transferable.of(serversJson), "/pgadmin4/servers.json")
                .withCopyToContainer(Transferable.of(pgpassFile, 0600), "/tmp/pgpassfile")
                .withAccessToHost(true);
        ContainerEnvironmentUtil.configurePort(uiContainer, true, PGADMIN_PORT);
        uiContainer.start();

        LOG.info("pgAdmin running at http://{}:{} (email: {}, password: {})",
                uiContainer.getHost(), uiContainer.getMappedPort(PGADMIN_PORT), PGADMIN_EMAIL, PGADMIN_PASSWORD);
    }

    @Override
    public void shutdown() {
        if (uiContainer != null) {
            LOG.info("Shutting down pgAdmin container");
            uiContainer.stop();
        }
        LOG.info("Stopping the Postgres container");
        container.stop();
    }

    @Override
    public PostgreSQLContainer getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", host(), port());
    }

    @Override
    public String userName() {
        return container.getUsername();
    }

    @Override
    public String password() {
        return container.getPassword();
    }

    @Override
    public String uiUrl() {
        if (uiContainer != null && uiContainer.isRunning()) {
            return String.format("http://%s:%d", uiContainer.getHost(), uiContainer.getMappedPort(PGADMIN_PORT));
        }
        return null;
    }
}
