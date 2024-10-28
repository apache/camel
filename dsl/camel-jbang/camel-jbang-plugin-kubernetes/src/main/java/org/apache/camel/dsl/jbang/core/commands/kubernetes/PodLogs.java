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
import java.util.Optional;

import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper.getPodPhase;

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

    // total timeout of 60s
    int maxRetryAttempts = 30;
    boolean retryForReload;
    private int retryCount;

    // used for testing
    long maxMessageCount = -1;
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
            label = "%s=%s".formatted(BaseTrait.KUBERNETES_NAME_LABEL, projectName);
        }

        String[] parts = label.split("=", 2);
        if (parts.length != 2) {
            printer().println("--label selector must be in syntax: key=value");
        }

        var retry = watchLogs();
        while ((retry || retryForReload) && ++retryCount < maxRetryAttempts) {
            sleepWell();
            printer().printf("Retry %d/%d Pod log for label %s%n", retryCount, maxRetryAttempts, label);
            retry = watchLogs();
        }

        printer().println("Stopped pod logging!");
        return 0;
    }

    // Returns true if a retry should be attempted
    private boolean watchLogs() {

        PodResource podRes = pods().withLabel(label)
                .resources()
                .findFirst()
                .orElse(null);
        if (podRes == null) {
            printer().printf("Pod for label %s not available%n", label);
            return true;
        }

        var terminated = isPodTerminated(podRes);
        if (!terminated) {

            LogWatch logs;
            if (tail < 0) {
                if (!ObjectHelper.isEmpty(container)) {
                    logs = podRes.inContainer(container).watchLog();
                } else {
                    logs = podRes.watchLog();
                }
            } else {
                if (!ObjectHelper.isEmpty(container)) {
                    logs = podRes.inContainer(container).tailingLines(tail).watchLog();
                } else {
                    logs = podRes.tailingLines(tail).watchLog();
                }
            }

            try (logs; BufferedReader reader = new BufferedReader(new InputStreamReader(logs.getOutput()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    printer().println(line);
                    if (maxMessageCount > 0 && ++messageCount > maxMessageCount) {
                        return false;
                    }
                    retryCount = 0;
                }
            } catch (IOException e) {
                printer().println("Failed to read pod logs - " + e.getMessage());
            }

            terminated = isPodTerminated(podRes);
        }

        return !terminated;
    }

    private boolean isPodTerminated(PodResource podRes) {
        var phase = Optional.ofNullable(podRes).map(pr -> getPodPhase(pr.get())).orElse("Unknown");
        return "Terminated".equals(phase);
    }

    private void sleepWell() {
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            printer().printf("Interrupted while waiting for pod - %s%n", e.getMessage());
        }
    }
}
