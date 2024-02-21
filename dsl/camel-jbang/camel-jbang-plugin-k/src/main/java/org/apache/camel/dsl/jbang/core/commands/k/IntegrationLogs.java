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
package org.apache.camel.dsl.jbang.core.commands.k;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "logs", description = "Print the logs of an integration", sortOptions = false)
public class IntegrationLogs extends KubeBaseCommand {

    @CommandLine.Parameters(description = "Integration name to grab logs from.",
                            paramLabel = "<name>")
    String name;

    @CommandLine.Option(names = { "--tail", "-t" },
                        defaultValue = "-1",
                        description = "The number of lines from the end of the logs to show. Defaults to -1 to show all the lines.")
    int tail = -1;

    public IntegrationLogs(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        String integrationName = KubernetesHelper.sanitize(name);
        Integration integration = client(Integration.class).withName(integrationName).get();

        if (integration == null) {
            printer().printf("Integration %s not found%n", integrationName);
            return 0;
        }

        watchLogs(integration);

        return 0;
    }

    void watchLogs(Integration integration) {
        PodList pods = pods().withLabel(KubeCommand.INTEGRATION_LABEL, integration.getMetadata().getName()).list();

        Pod pod = pods.getItems().stream()
                .filter(p -> p.getStatus().getPhase() != null && !"Terminated".equals(p.getStatus().getPhase()))
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Failed to find integration pod"));

        String containerName = null;
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            if (pod.getSpec().getContainers().stream()
                    .anyMatch(container -> KubeCommand.INTEGRATION_CONTAINER_NAME.equals(container.getName()))) {
                containerName = KubeCommand.INTEGRATION_CONTAINER_NAME;
            } else if (pod.getSpec().getContainers().size() > 0) {
                containerName = pod.getSpec().getContainers().get(0).getName();
            }
        }

        PodResource podRes = pods().withName(pod.getMetadata().getName());

        LogWatch logs;
        if (tail < 0) {
            if (containerName != null) {
                logs = podRes.inContainer(containerName).watchLog();
            } else {
                logs = podRes.watchLog();
            }
        } else {
            if (containerName != null) {
                logs = podRes.inContainer(containerName).tailingLines(tail).watchLog();
            } else {
                logs = podRes.tailingLines(tail).watchLog();
            }
        }

        try (logs; BufferedReader reader = new BufferedReader(new InputStreamReader(logs.getOutput()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                printer().println(line);
            }
        } catch (IOException e) {
            printer().println("Failed to read integration pod logs - " + e.getMessage());
        }
    }
}
