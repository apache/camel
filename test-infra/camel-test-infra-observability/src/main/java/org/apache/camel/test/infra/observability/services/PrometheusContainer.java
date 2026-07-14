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
import java.nio.charset.StandardCharsets;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.observability.common.ObservabilityProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

public class PrometheusContainer extends GenericContainer<PrometheusContainer> {
    public static final String CONTAINER_NAME = "prometheus";

    public PrometheusContainer() {
        super(LocalPropertyResolver.getProperty(
                ObservabilityLocalContainerInfraService.class, ObservabilityProperties.PROMETHEUS_CONTAINER));

        this.withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(ObservabilityProperties.DEFAULT_PROMETHEUS_PORT)
                .withExtraHost("host.docker.internal", "host-gateway")
                .waitingFor(Wait.forHttp("/-/healthy").forPort(ObservabilityProperties.DEFAULT_PROMETHEUS_PORT));

        String config = loadPrometheusConfig();
        this.withCopyToContainer(Transferable.of(config.getBytes(StandardCharsets.UTF_8)),
                "/etc/prometheus/prometheus.yml");
    }

    private String loadPrometheusConfig() {
        try (InputStream is = getClass().getResourceAsStream("prometheus.yml")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fall through to default
        }
        return "global:\n  scrape_interval: 15s\n\nscrape_configs:\n"
               + "  - job_name: 'camel'\n    metrics_path: '/observe/metrics'\n"
               + "    static_configs:\n      - targets: ['host.docker.internal:9876']\n";
    }
}
