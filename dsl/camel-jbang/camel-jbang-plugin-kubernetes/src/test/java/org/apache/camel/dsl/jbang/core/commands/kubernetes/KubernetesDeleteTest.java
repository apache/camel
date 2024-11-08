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

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIf;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
@EnabledIf("isDockerAvailable")
class KubernetesDeleteTest extends KubernetesBaseTest {

    @Test
    public void shouldDeleteKubernetesResources() throws Exception {
        kubernetesClient.apps().deployments().resource(new DeploymentBuilder()
                .withNewMetadata()
                .withName("route")
                .addToLabels(BaseTrait.KUBERNETES_NAME_LABEL, "route")
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToContainers(new ContainerBuilder()
                        .withName("route")
                        .withImage("quay.io/camel-test/route:1.0-SNAPSHOT")
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build()).create();

        kubernetesClient.services().resource(new ServiceBuilder()
                .withNewMetadata()
                .withName("route")
                .endMetadata()
                .withNewSpec()
                .withPorts(new ServicePortBuilder()
                        .withPort(80)
                        .withProtocol("TCP")
                        .withName("http")
                        .withTargetPort(new IntOrString(8080))
                        .build())
                .endSpec()
                .build()).create();

        KubernetesRun run = new KubernetesRun(new CamelJBangMain().withPrinter(printer));
        run.withClient(kubernetesClient);
        run.imageGroup = "camel-test";
        run.imageBuild = false;
        run.imagePush = false;
        run.filePaths = new String[] { "classpath:route.yaml" };
        run.output = "yaml";
        int exit = run.doCall();
        Assertions.assertEquals(0, exit);

        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        command.name = "route";
        exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertNull(kubernetesClient.apps().deployments().withName("route").get());
        Assertions.assertNull(kubernetesClient.services().withName("route").get());
    }

    @Test
    public void shouldHandleNoExportFound() throws Exception {
        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);
        command.name = "does-not-exist";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
        Assertions.assertEquals("Failed to resolve exported project from path '.camel-jbang-run/does-not-exist'",
                printer.getOutput());
    }

}
