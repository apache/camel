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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.v1.Integration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KubeCommandMainTest extends KubeBaseTest {

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
                .withLabels(Collections.singletonMap(KubeCommand.INTEGRATION_LABEL, integration.getMetadata().getName()))
                .endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .endStatus()
                .build();

        kubernetesClient.pods().resource(pod).create();

        CamelJBangMain.run(createMain(), "k", "logs", "routes");
    }

    @Test
    public void shouldRunIntegration() {
        KubernetesHelper.setKubernetesClient(kubernetesClient);
        CamelJBangMain.run(createMain(), "k", "run", "classpath:route.yaml");

        Integration integration = kubernetesClient.resources(Integration.class).withName("route").get();
        Assertions.assertNotNull(integration);
        Assertions.assertEquals(integration.getMetadata().getAnnotations().get(KubeCommand.OPERATOR_ID_LABEL), "camel-k");
    }

    @Test
    public void shouldPrintIntegration() {
        CamelJBangMain.run(createMain(), "k", "run", "classpath:route.yaml", "-o", "yaml");

        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  annotations:
                    camel.apache.org/operator.id: camel-k
                  name: route
                spec:
                  flows:
                  - additionalProperties:
                      from:
                        uri: timer:tick
                        steps:
                        - set-body:
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
                if (exitCode != 0) {
                    Assertions.fail("Main finished with exit code %d".formatted(exitCode));
                }
            }
        }.withPrinter(printer);
    }

}
