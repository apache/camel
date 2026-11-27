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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Prometheus;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.CliException;

/**
 * Lets Prometheus scrape the metrics of the integration, which camel-observability-services serves on the management
 * port: names the container port of the metrics, and creates a PodMonitor of the Prometheus Operator for the pods of
 * the Deployment.
 */
public class PrometheusTrait extends BaseTrait {

    public static final int PROMETHEUS_TRAIT_ORDER = 1900;

    public static final int DEFAULT_METRICS_PORT = 9876;
    public static final String DEFAULT_METRICS_PORT_NAME = "management";
    public static final String DEFAULT_METRICS_PATH = "/observe/metrics";

    public static final String POD_MONITOR_API_VERSION = "monitoring.coreos.com/v1";
    public static final String POD_MONITOR_KIND = "PodMonitor";

    public PrometheusTrait() {
        super("prometheus", PROMETHEUS_TRAIT_ORDER);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        // must be explicitly enabled
        if (traitConfig.getPrometheus() == null
                || !Optional.ofNullable(traitConfig.getPrometheus().getEnabled()).orElse(false)) {
            return false;
        }
        // a mistake in the labels fails the export, before the manifest is built
        labels(traitConfig.getPrometheus().getPodMonitorLabels());
        if (context.getDeployment().isEmpty()) {
            // a CronJob ends before it is scraped, and a Knative service has one port only
            context.printer().println("The prometheus trait applies to a Deployment only, and is skipped");
            return false;
        }
        return true;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Prometheus prometheus = Optional.ofNullable(traitConfig.getPrometheus()).orElseGet(Prometheus::new);
        int port = Optional.ofNullable(prometheus.getContainerPort()).map(Long::intValue).orElse(DEFAULT_METRICS_PORT);

        // the port may be declared already, such as the http port of a WAR, which serves the management endpoints
        Optional<ContainerPort> existing = context.getDeployment()
                .map(d -> d.buildSpec())
                .map(s -> s.getTemplate())
                .map(t -> t.getSpec())
                .map(s -> s.getContainers())
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0).getPorts())
                .flatMap(ports -> ports.stream().filter(p -> Objects.equals(p.getContainerPort(), port)).findFirst());
        String portName = existing.map(ContainerPort::getName)
                .orElseGet(() -> Optional.ofNullable(prometheus.getContainerPortName()).orElse(DEFAULT_METRICS_PORT_NAME));
        if (existing.isEmpty()) {
            context.doWithDeployments(
                    d -> d.editSpec()
                            .editOrNewTemplate()
                            .editOrNewSpec()
                            .editFirstContainer()
                            .addNewPort()
                            .withName(portName)
                            .withContainerPort(port)
                            .withProtocol("TCP")
                            .endPort()
                            .endContainer()
                            .endSpec()
                            .endTemplate()
                            .endSpec());
        }

        if (Optional.ofNullable(prometheus.getPodMonitor()).orElse(true)) {
            context.add(podMonitor(context.getName(), portName,
                    Optional.ofNullable(prometheus.getPath()).orElse(DEFAULT_METRICS_PATH),
                    labels(prometheus.getPodMonitorLabels())));
        }
    }

    private static GenericKubernetesResourceBuilder podMonitor(
            String name, String portName, String path, Map<String, String> labels) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("port", portName);
        endpoint.put("path", path);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", Map.of("matchLabels", Map.of(KUBERNETES_LABEL_NAME, name)));
        spec.put("podMetricsEndpoints", List.of(endpoint));

        GenericKubernetesResourceBuilder builder = new GenericKubernetesResourceBuilder()
                .withApiVersion(POD_MONITOR_API_VERSION)
                .withKind(POD_MONITOR_KIND)
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .addToAdditionalProperties("spec", spec);
        if (!labels.isEmpty()) {
            builder.editMetadata().withLabels(labels).endMetadata();
        }
        return builder;
    }

    private static Map<String, String> labels(List<String> keyValues) {
        Map<String, String> answer = new LinkedHashMap<>();
        for (String keyValue : Optional.ofNullable(keyValues).orElseGet(List::of)) {
            String[] parts = keyValue.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw CliException.usage(
                        "The prometheus trait takes pod-monitor-labels as key=value, not: " + keyValue);
            }
            answer.put(parts[0].trim(), parts[1].trim());
        }
        return answer;
    }
}
