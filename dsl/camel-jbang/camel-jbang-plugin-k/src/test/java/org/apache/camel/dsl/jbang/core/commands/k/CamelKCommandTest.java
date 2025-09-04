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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Deprecated and resource intensive")
class CamelKCommandTest extends CamelKBaseTest {

    @Test
    public void shouldDeleteIntegration() throws IOException {
        KubernetesHelper.setKubernetesClient(kubernetesClient);
        CamelJBangMain.run(createMain(), "k", "delete", "--all");

        Assertions.assertEquals("Integrations deleted", printer.getOutput());
    }

    @Test
    public void shouldListIntegration() throws IOException {
        KubernetesHelper.setKubernetesClient(kubernetesClient);

        kubernetesClient.resources(Integration.class).resource(createIntegration()).create();

        CamelJBangMain.run(createMain(), "k", "get");

        List<String> output = printer.getLines();
        Assertions.assertEquals("NAME    PHASE    KIT  READY", output.get(0));
        Assertions.assertEquals("routes  Unknown        0/1", output.get(1));
    }

    @Test
    public void shouldPrintIntegrationLogs() throws IOException {
        KubernetesHelper.setKubernetesClient(kubernetesClient);

        kubernetesClient.resources(Integration.class).resource(createIntegration()).create();

        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(integration.getMetadata().getName())
                .withLabels(Collections.singletonMap(CamelKCommand.INTEGRATION_LABEL, integration.getMetadata().getName()))
                .endMetadata()
                .withNewSpec()
                .addToContainers(new ContainerBuilder()
                        .withName(CamelKCommand.INTEGRATION_CONTAINER_NAME)
                        .build())
                .endSpec()
                .withNewStatus()
                .withPhase("Running")
                .endStatus()
                .build();

        kubernetesClient.pods().resource(pod).create();

        CamelJBangMain.run(createMain(), "k", "logs", integration.getMetadata().getName());
    }

    @Test
    public void shouldRunIntegration() {
        KubernetesHelper.setKubernetesClient(kubernetesClient);
        CamelJBangMain.run(createMain(), "k", "run", "classpath:route.yaml");

        Integration integration = kubernetesClient.resources(Integration.class).withName("route").get();
        Assertions.assertNotNull(integration);
        Assertions.assertEquals("camel-k", integration.getMetadata().getAnnotations().get(CamelKCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldPrintIntegration() {
        CamelJBangMain.run(createMain(), "k", "run", "classpath:route.yaml", "--output", "yaml");

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - from:
                      uri: timer:tick
                      steps:
                      - setBody:
                          constant: Hello Camel !!!
                      - to: log:info
                  traits: {}""", printer.getOutput());
    }

    @Test
    public void shouldResolvePlugin() {
        FactoryFinder factoryFinder
                = new DefaultFactoryFinder(new DefaultClassResolver(), FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/");
        Assertions.assertTrue(factoryFinder.newInstance("camel-jbang-plugin-k").isPresent());
    }

    private CamelJBangMain createMain() {
        return new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
                Assertions.assertEquals(0, exitCode,
                        "Main finished with exit code %d:%n%s".formatted(exitCode, printer.getOutput()));
            }
        }.withPrinter(printer);
    }

}
