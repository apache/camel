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

package org.apache.camel.test.infra.opensearch.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.opensearch.common.OpenSearchProperties;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = OpenSearchInfraService.class,
              description = "OpenSearch is a distributed search and analytics engine",
              serviceAlias = { "opensearch" }, uiSupported = true)
public class OpenSearchLocalContainerInfraService implements OpenSearchInfraService, ContainerService<OpensearchContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchLocalContainerInfraService.class);
    private static final int OPEN_SEARCH_PORT = 9200;
    private static final String USER_NAME = "admin";
    // NOTE: default value used for testing purposes only.
    private static final String PASSWORD = "admin"; // NOSONAR

    private static final String OPENSEARCH_DASHBOARDS_CONTAINER_IMAGE = "opensearch-dashboards.container.image";
    private static final int OPENSEARCH_DASHBOARDS_PORT = 5601;

    private final OpensearchContainer container;
    private GenericContainer<?> uiContainer;

    public OpenSearchLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(OpenSearchLocalContainerInfraService.class,
                OpenSearchProperties.OPEN_SEARCH_CONTAINER));
    }

    public OpenSearchLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public OpenSearchLocalContainerInfraService(OpensearchContainer container) {
        this.container = container;
    }

    protected OpensearchContainer initContainer(String imageName) {
        class TestInfraOpensearchContainer extends OpensearchContainer {
            public TestInfraOpensearchContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName)
                        .asCompatibleSubstituteFor("opensearchproject/opensearch"));

                // Increase the timeout from 60 seconds to 90 seconds to ensure that it will be long enough
                // on the build pipeline
                setWaitStrategy(
                        new LogMessageWaitStrategy()
                                .withRegEx(".*(\"message\":\\s?\"started[\\s?|\"].*|] started\n$)")
                                .withStartupTimeout(Duration.ofSeconds(90)));

                withLogConsumer(new Slf4jLogConsumer(LOG));

                // Disable the observability plugin to avoid startup issues (CAMEL-23502)
                withEnv("OPENSEARCH_JAVA_OPTS",
                        "-Dopensearch.cgroups.hierarchy.override=/ -Dopensearch.plugin.disable=opensearch-observability");

                // Disable disk watermarks to prevent "disk usage exceeded flood-stage watermark"
                // errors when running on CI machines with limited disk space (CAMEL-24018)
                withEnv("cluster.routing.allocation.disk.threshold_enabled", "false");

                ContainerEnvironmentUtil.configurePort(this, fixedPort, OPEN_SEARCH_PORT);
            }
        }

        return new TestInfraOpensearchContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public int getPort() {
        return container.getMappedPort(OPEN_SEARCH_PORT);
    }

    @Override
    public String getOpenSearchHost() {
        return container.getHost();
    }

    @Override
    public String getHttpHostAddress() {
        return container.getHttpHostAddress();
    }

    @Override
    public void registerProperties() {
        System.setProperty(OpenSearchProperties.OPEN_SEARCH_HOST, getOpenSearchHost());
        System.setProperty(OpenSearchProperties.OPEN_SEARCH_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the OpenSearch container");
        ContainerEnvironmentUtil.configureContainerStartup(container, OpenSearchProperties.OPEN_SEARCH_CONTAINER_STARTUP,
                2);

        container.start();

        registerProperties();
        LOG.info("OpenSearch instance running at {}", getHttpHostAddress());

        if (ContainerEnvironmentUtil.isWithUi()) {
            try {
                startUiContainer();
            } catch (Exception e) {
                LOG.warn("Failed to start OpenSearch Dashboards UI container: {}", e.getMessage(), e);
            }
        }
    }

    private void startUiContainer() {
        String uiImage = LocalPropertyResolver.getProperty(
                OpenSearchLocalContainerInfraService.class, OPENSEARCH_DASHBOARDS_CONTAINER_IMAGE);
        int opensearchPort = ContainerEnvironmentUtil.getConfiguredPort(OPEN_SEARCH_PORT);
        Testcontainers.exposeHostPorts(opensearchPort);

        uiContainer = new GenericContainer<>(uiImage)
                .withEnv("OPENSEARCH_HOSTS", "[\"http://host.testcontainers.internal:" + opensearchPort + "\"]")
                .withEnv("DISABLE_SECURITY_DASHBOARDS_PLUGIN", "true")
                .withAccessToHost(true);
        ContainerEnvironmentUtil.configurePort(uiContainer, true, OPENSEARCH_DASHBOARDS_PORT);
        uiContainer.start();

        LOG.info("OpenSearch Dashboards running at http://{}:{}",
                uiContainer.getHost(), uiContainer.getMappedPort(OPENSEARCH_DASHBOARDS_PORT));
    }

    @Override
    public void shutdown() {
        if (uiContainer != null) {
            LOG.info("Shutting down OpenSearch Dashboards container");
            uiContainer.stop();
        }
        LOG.info("Stopping the OpenSearch container");
        container.stop();
    }

    @Override
    public OpensearchContainer getContainer() {
        return container;
    }

    @Override
    public String getUsername() {
        return USER_NAME;
    }

    @Override
    public String getPassword() {
        return PASSWORD;
    }

    @Override
    public String uiUrl() {
        if (uiContainer != null && uiContainer.isRunning()) {
            return String.format("http://%s:%d", uiContainer.getHost(),
                    uiContainer.getMappedPort(OPENSEARCH_DASHBOARDS_PORT));
        }
        return null;
    }
}
