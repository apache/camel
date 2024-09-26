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
package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "logs", description = "Print the logs of a Kubernetes pod", sortOptions = false)
public class PodLogs extends KubernetesBaseCommand {

    @CommandLine.Parameters(description = "The Camel file to get logs from. Integration name is derived from the file name.",
                            arity = "0..1", paramLabel = "<file>")
    protected String filePath;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived from the source file name.")
    protected String name;

    @CommandLine.Option(names = { "--label" },
                        description = "Label name and value used as a pod selector.")
    protected String label;

    @CommandLine.Option(names = "--container",
                        description = "Name identifying the pod container to grab the logs from.")
    protected String container;

    @CommandLine.Option(names = { "--tail" },
                        defaultValue = "-1",
                        description = "The number of lines from the end of the logs to show. Defaults to -1 to show all the lines.")
    int tail = -1;

    int maxWaitAttempts = 30; // total timeout of 60 seconds

    // used for testing
    int maxLogMessages = -1;
    long messageCount = 0;

    public PodLogs(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        if (name == null && label == null && filePath == null) {
            printer().println("Name or label selector must be set");
            return 1;
        }

        if (label == null) {
            String projectName;
            if (name != null) {
                projectName = KubernetesHelper.sanitize(name);
            } else {
                projectName = KubernetesHelper.sanitize(FileUtil.onlyName(SourceScheme.onlyName(filePath)));
            }

            label = "%s=%s".formatted(BaseTrait.INTEGRATION_LABEL, projectName);
        }

        String[] parts = label.split("=", 2);
        if (parts.length != 2) {
            printer().println("--label selector must be in syntax: key=value");
        }

        boolean shouldResume = true;
        AtomicInteger resumeCount = new AtomicInteger();
        while (shouldResume) {
            shouldResume = watchLogs(parts[0], parts[1], container, resumeCount);
            resumeCount.incrementAndGet();
            printer().printf("PodLogs: [resume=%b, count=%d]%n", shouldResume, resumeCount.get());
            sleepWell();
        }

        return 0;
    }

    public boolean watchLogs(String label, String labelValue, String container, AtomicInteger resumeCount) {
        PodList pods = pods().withLabel(label, labelValue).list();

        Pod pod = pods.getItems().stream()
                .filter(p -> p.getStatus().getPhase() != null && !"Terminated".equals(p.getStatus().getPhase()))
                .findFirst()
                .orElse(null);

        if (pod == null) {
            if (resumeCount.get() == 0) {
                printer().printf("Pod for label %s=%s not available - Waiting ...%n".formatted(label, labelValue));
            }

            // use 2-sec delay in waiting for pod logs mode
            sleepWell();
            return resumeCount.get() < maxWaitAttempts;
        }

        String containerName = null;
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            if (container != null && pod.getSpec().getContainers().stream().anyMatch(c -> container.equals(c.getName()))) {
                containerName = container;
            } else if (!pod.getSpec().getContainers().isEmpty()) {
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
                if (messageCount++ > maxLogMessages && maxLogMessages > 0) {
                    return false;
                }
                resumeCount.set(0);
            }
        } catch (IOException e) {
            printer().println("Failed to read pod logs - " + e.getMessage());
        }

        return resumeCount.get() < maxWaitAttempts;
    }

    private void sleepWell() {
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            printer().printf("Interrupted while waiting for pod - %s%n", e.getMessage());
        }
    }
}
