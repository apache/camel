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

import java.util.Collections;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationLogsTest extends KubeBaseTest {

    @Test
    public void shouldHandleIntegrationsNotFound() throws Exception {
        IntegrationLogs command = createCommand();
        command.name = "mickey-mouse";
        command.doCall();

        Assertions.assertEquals("Integration mickey-mouse not found", printer.getOutput());
    }

    @Test
    public void shouldGetIntegrationLogs() throws Exception {
        Integration integration = createIntegration();
        kubernetesClient.resources(Integration.class).resource(integration).create();

        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(integration.getMetadata().getName())
                .withLabels(Collections.singletonMap(KubeCommand.INTEGRATION_LABEL, integration.getMetadata().getName()))
                .endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .endStatus()
                .build();

        kubernetesClient.pods().resource(pod).create();

        IntegrationLogs command = createCommand();

        command.name = "routes";
        command.doCall();
    }

    private IntegrationLogs createCommand() {
        IntegrationLogs command = new IntegrationLogs(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        return command;
    }

}
