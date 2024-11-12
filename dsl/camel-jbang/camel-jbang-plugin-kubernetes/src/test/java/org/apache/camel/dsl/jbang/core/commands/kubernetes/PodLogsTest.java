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

import java.util.Map;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
class PodLogsTest extends KubernetesBaseTest {

    @Test
    public void shouldHandlePodNotFound() throws Exception {
        PodLogs command = createCommand();
        command.name = "mickey-mouse";
        command.maxRetryAttempts = 2; // total timeout of 4 seconds
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        Assertions.assertTrue(
                printer.getOutput().contains("Pod for label app.kubernetes.io/name=mickey-mouse not available"));
    }

    @Test
    public void shouldGetPodLogs() throws Exception {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("pod")
                .withLabels(Map.of(BaseTrait.KUBERNETES_NAME_LABEL, "routes"))
                .endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .endStatus()
                .build();

        kubernetesClient.pods().resource(pod).create();

        var podLog = createCommand();
        podLog.maxMessageCount = 10;
        podLog.name = "routes";
        int exit = podLog.doCall();
        Assertions.assertEquals(0, exit);
    }

    private PodLogs createCommand() {
        PodLogs command = new PodLogs(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
