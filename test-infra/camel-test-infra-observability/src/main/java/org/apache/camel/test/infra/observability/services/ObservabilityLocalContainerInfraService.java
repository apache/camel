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
package org.apache.camel.test.infra.observability.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.observability.common.ObservabilityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

@InfraService(service = ObservabilityInfraService.class,
              description = "Local observability stack (Prometheus + VictoriaTraces + VictoriaLogs + Perses)",
              serviceAlias = { "observability" },
              uiSupported = true)
public class ObservabilityLocalContainerInfraService
        implements ObservabilityInfraService, ContainerService<PrometheusContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityLocalContainerInfraService.class);

    private final Network network;
    private final PrometheusContainer prometheusContainer;
    private final VictoriaTracesContainer victoriaTracesContainer;
    private final VictoriaLogsContainer victoriaLogsContainer;
    private final PersesContainer persesContainer;

    public ObservabilityLocalContainerInfraService() {
        network = Network.newNetwork();

        prometheusContainer = new PrometheusContainer();
        prometheusContainer.withNetwork(network);

        victoriaTracesContainer = new VictoriaTracesContainer();
        victoriaTracesContainer.withNetwork(network);

        victoriaLogsContainer = new VictoriaLogsContainer();
        victoriaLogsContainer.withNetwork(network);

        persesContainer = new PersesContainer();
        persesContainer.withNetwork(network);

        initContainers();
    }

    private void initContainers() {
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            prometheusContainer.withCreateContainerCmdModifier(cmd -> cmd.withName(name + "-prometheus"));
            victoriaTracesContainer.withCreateContainerCmdModifier(cmd -> cmd.withName(name + "-victoriatraces"));
            victoriaLogsContainer.withCreateContainerCmdModifier(cmd -> cmd.withName(name + "-victorialogs"));
            persesContainer.withCreateContainerCmdModifier(cmd -> cmd.withName(name + "-perses"));
        }

        boolean fixedPort = ContainerEnvironmentUtil.isFixedPort(this.getClass());
        if (fixedPort) {
            ContainerEnvironmentUtil.configurePorts(prometheusContainer, true,
                    ContainerEnvironmentUtil.PortConfig.primary(ObservabilityProperties.DEFAULT_PROMETHEUS_PORT));

            ContainerEnvironmentUtil.configurePorts(victoriaTracesContainer, true,
                    ContainerEnvironmentUtil.PortConfig.primary(ObservabilityProperties.DEFAULT_VICTORIA_TRACES_PORT));

            ContainerEnvironmentUtil.configurePorts(victoriaLogsContainer, true,
                    ContainerEnvironmentUtil.PortConfig.primary(ObservabilityProperties.DEFAULT_VICTORIA_LOGS_PORT));

            ContainerEnvironmentUtil.configurePorts(persesContainer, true,
                    ContainerEnvironmentUtil.PortConfig.primary(ObservabilityProperties.DEFAULT_PERSES_PORT));
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(ObservabilityProperties.HOST, host());
        System.setProperty(ObservabilityProperties.PROMETHEUS_PORT, String.valueOf(prometheusPort()));
        System.setProperty(ObservabilityProperties.VICTORIA_TRACES_PORT, String.valueOf(victoriaTracesPort()));
        System.setProperty(ObservabilityProperties.VICTORIA_LOGS_PORT, String.valueOf(victoriaLogsPort()));
        System.setProperty(ObservabilityProperties.PERSES_PORT, String.valueOf(persesPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Starting Prometheus");
        prometheusContainer.start();

        LOG.info("Starting VictoriaTraces");
        victoriaTracesContainer.start();

        LOG.info("Starting VictoriaLogs");
        victoriaLogsContainer.start();

        LOG.info("Starting Perses");
        persesContainer.start();

        registerProperties();
        configurePerses();

        LOG.info("Observability stack running:");
        LOG.info("  Prometheus:      {}", prometheusUrl());
        LOG.info("  VictoriaTraces:  {} (OTLP: {})", victoriaTracesUrl(), victoriaTracesOtlpEndpoint());
        LOG.info("  VictoriaLogs:    {} (OTLP: {})", victoriaLogsUrl(), victoriaLogsOtlpEndpoint());
        LOG.info("  Perses:          {}", persesUrl());
        LOG.info("  Metrics target:  {}", metricsTarget());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping observability stack");
        safeStop(persesContainer::stop);
        safeStop(victoriaLogsContainer::stop);
        safeStop(victoriaTracesContainer::stop);
        safeStop(prometheusContainer::stop);
        safeStop(network::close);
    }

    private void safeStop(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.warn("Shutdown step failed: {}", e.getMessage());
        }
    }

    @Override
    public PrometheusContainer getContainer() {
        return prometheusContainer;
    }

    @Override
    public String host() {
        return prometheusContainer.getHost();
    }

    @Override
    public int prometheusPort() {
        return prometheusContainer.getMappedPort(ObservabilityProperties.DEFAULT_PROMETHEUS_PORT);
    }

    @Override
    public int victoriaTracesPort() {
        return victoriaTracesContainer.getMappedPort(ObservabilityProperties.DEFAULT_VICTORIA_TRACES_PORT);
    }

    @Override
    public int victoriaLogsPort() {
        return victoriaLogsContainer.getMappedPort(ObservabilityProperties.DEFAULT_VICTORIA_LOGS_PORT);
    }

    @Override
    public int persesPort() {
        return persesContainer.getMappedPort(ObservabilityProperties.DEFAULT_PERSES_PORT);
    }

    private void configurePerses() {
        HttpClient client = HttpClient.newHttpClient();
        persesPost(client, "/api/v1/globaldatasources", loadResource("perses-datasource.json"));
        persesPost(client, "/api/v1/projects", loadResource("perses-project.json"));
        persesPost(client, "/api/v1/projects/camel/dashboards", loadResource("perses-dashboard.json"));
        LOG.info("Perses configured with Camel project and dashboard");
    }

    private void persesPost(HttpClient client, String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(persesUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("Perses POST {} failed: {} {}", path, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.warn("Perses POST {} failed: {}", path, e.getMessage());
        }
    }

    private String loadResource(String name) {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warn("Failed to load resource {}: {}", name, e.getMessage());
        }
        return "{}";
    }
}
