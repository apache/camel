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
package org.apache.camel.test.infra.keycloak.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.keycloak.common.KeycloakProperties;
import org.apache.commons.lang3.SystemUtils;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = KeycloakInfraService.class,
              description = "Identity and access management solution",
              serviceAlias = { "keycloak" })
public class KeycloakLocalContainerInfraService implements KeycloakInfraService, ContainerService<GenericContainer<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakLocalContainerInfraService.class);

    private static final String DEFAULT_KEYCLOAK_CONTAINER = "quay.io/keycloak/keycloak:latest";
    private static final int KEYCLOAK_PORT = 8080;
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    // NOTE: default value used only for testing purposes.
    private static final String DEFAULT_ADMIN_PASSWORD = "admin"; // NOSONAR
    private static final String DEFAULT_REALM = "master";

    private final GenericContainer<?> container;

    public KeycloakLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(KeycloakLocalContainerInfraService.class,
                KeycloakProperties.KEYCLOAK_CONTAINER));
    }

    public KeycloakLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public KeycloakLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    protected GenericContainer<?> initContainer(String imageName) {
        String keycloakImage = imageName != null ? imageName : DEFAULT_KEYCLOAK_CONTAINER;

        class TestInfraKeycloakContainer extends GenericContainer<TestInfraKeycloakContainer> {
            public TestInfraKeycloakContainer(boolean fixedPort) {
                super(DockerImageName.parse(keycloakImage));

                if ("ppc64le".equals(SystemUtils.OS_ARCH))
                    withExposedPorts(KEYCLOAK_PORT)
                            .withEnv("KEYCLOAK_ADMIN", DEFAULT_ADMIN_USERNAME)
                            .withEnv("KEYCLOAK_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD)
                            .withCommand("/opt/bitnami/keycloak/bin/kc.sh", "start-dev")
                            .waitingFor(Wait.forListeningPorts(KEYCLOAK_PORT))
                            .withStartupTimeout(Duration.ofMinutes(3L));
                else
                    withExposedPorts(KEYCLOAK_PORT)
                            .withEnv("KEYCLOAK_ADMIN", DEFAULT_ADMIN_USERNAME)
                            .withEnv("KEYCLOAK_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD)
                            .withCommand("start-dev")
                            .waitingFor(Wait.forListeningPorts(KEYCLOAK_PORT))
                            .withStartupTimeout(Duration.ofMinutes(3L));

                if (fixedPort) {
                    addFixedExposedPort(KEYCLOAK_PORT, KEYCLOAK_PORT);
                }
            }
        }

        return new TestInfraKeycloakContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(KeycloakProperties.KEYCLOAK_SERVER_URL, getKeycloakServerUrl());
        System.setProperty(KeycloakProperties.KEYCLOAK_REALM, getKeycloakRealm());
        System.setProperty(KeycloakProperties.KEYCLOAK_USERNAME, getKeycloakUsername());
        System.setProperty(KeycloakProperties.KEYCLOAK_PASSWORD, getKeycloakPassword());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Keycloak container");
        container.start();

        registerProperties();
        LOG.info("Keycloak instance running at {}", getKeycloakServerUrl());
        LOG.info("Keycloak admin console available at {}/admin", getKeycloakServerUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Keycloak container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String getKeycloakServerUrl() {
        return String.format("http://%s:%d", container.getHost(), container.getMappedPort(KEYCLOAK_PORT));
    }

    @Override
    public String getKeycloakRealm() {
        return DEFAULT_REALM;
    }

    @Override
    public String getKeycloakUsername() {
        return DEFAULT_ADMIN_USERNAME;
    }

    @Override
    public String getKeycloakPassword() {
        return DEFAULT_ADMIN_PASSWORD;
    }

    @Override
    public Keycloak getKeycloakAdminClient() {
        return Keycloak.getInstance(
                getKeycloakServerUrl(),
                getKeycloakRealm(),
                getKeycloakUsername(),
                getKeycloakPassword(),
                "admin-cli");
    }
}
