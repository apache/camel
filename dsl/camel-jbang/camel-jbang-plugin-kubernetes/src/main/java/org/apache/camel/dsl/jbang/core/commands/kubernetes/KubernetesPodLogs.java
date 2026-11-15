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
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper.getPodPhase;

@CommandDefinition(name = "logs", description = "Print the logs of a Kubernetes pod", generateHelp = true)
public class KubernetesPodLogs extends KubernetesBaseCommand {

    @Argument(description = "The Camel file to get logs from. Integration name is derived from the file name.",
              paramLabel = "<file>")
    protected String filePath;

    @Option(name = "label", description = "Label name and value used as a pod selector.")
    protected String label;

    @Option(name = "container", description = "Name identifying the pod container to grab the logs from.")
    protected String container;

    @Option(name = "tail",
            description = "The number of lines from the end of the logs to show. Defaults to -1 to show all the lines.",
            defaultValue = "-1")
    int tail = -1;

    // total timeout of 60s
    int maxRetryAttempts = 30;
    boolean retryForReload;
    private int retryCount;

    // used for testing
    long maxMessageCount = -1;
    long messageCount = 0;
    // whether a pod with the label showed up, and whether reading its log failed the last time
    private boolean podFound;
    private boolean readFailed;

    public KubernetesPodLogs(CamelJBangMain main) {
        super(main);
        projectNameSuppliers.add(() -> projectNameFromFilePath(() -> filePath));
    }

    public Integer doCall() throws Exception {

        if (label == null) {
            String projectName = getProjectName();
            label = "%s=%s".formatted(BaseTrait.KUBERNETES_LABEL_NAME, projectName);
        }

        String[] parts = label.split("=", 2);
        if (parts.length != 2) {
            throw CliException.usage("--label selector must be in syntax: key=value");
        }

        var retry = watchLogs();
        while ((retry || retryForReload) && ++retryCount < maxRetryAttempts) {
            if (!sleepWell()) {
                break;
            }
            printer().printf("Retry %d/%d Pod log for label %s%n", retryCount, maxRetryAttempts, label);
            retry = watchLogs();
        }

        printer().println("Stopped pod logging!");
        if (!podFound) {
            throw CliException.notFound("No pod with label " + label);
        }
        return readFailed ? CliExitCode.FAILURE : CliExitCode.OK;
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
        podFound = true;

        var terminated = isPodTerminated(podRes);
        if (terminated) {
            // a pod that has finished, such as the pod of a CronJob: its log once, as kubectl logs does
            printLog(podRes);
            return false;
        }

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
            readFailed = false;
        } catch (IOException e) {
            printer().printErr("Failed to read pod logs - " + e.getMessage());
            readFailed = true;
        }

        terminated = isPodTerminated(podRes);

        return !terminated;
    }

    private void printLog(PodResource podRes) {
        String log;
        try {
            if (!ObjectHelper.isEmpty(container)) {
                log = tail < 0
                        ? podRes.inContainer(container).getLog() : podRes.inContainer(container).tailingLines(tail).getLog();
            } else {
                log = tail < 0 ? podRes.getLog() : podRes.tailingLines(tail).getLog();
            }
        } catch (RuntimeException e) {
            printer().printErr("Failed to read pod logs - " + e.getMessage());
            readFailed = true;
            return;
        }
        readFailed = false;
        if (log != null) {
            for (String line : log.split("\\R")) {
                printer().println(line);
                if (maxMessageCount > 0 && ++messageCount > maxMessageCount) {
                    return;
                }
            }
        }
    }

    /**
     * Whether the pod has finished: the phase {@code Succeeded} or {@code Failed}.
     */
    private boolean isPodTerminated(PodResource podRes) {
        // not Terminated, which is no pod phase: the log of a finished pod was printed again and again (CAMEL-26934)
        var phase = "Unknown";
        try {
            phase = Optional.ofNullable(podRes).map(pr -> getPodPhase(pr.get())).orElse(phase);
        } catch (RuntimeException ex) {
            printer().printErr(ex);
        }
        return "Succeeded".equals(phase) || "Failed".equals(phase);
    }

    private boolean sleepWell() {
        try {
            Thread.sleep(2000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            printer().printf("Interrupted while waiting for pod - %s%n", e.getMessage());
            return false;
        }
    }
}
