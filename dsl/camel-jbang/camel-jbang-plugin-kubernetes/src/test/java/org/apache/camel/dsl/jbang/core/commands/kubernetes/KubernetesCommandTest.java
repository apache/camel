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

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.FactoryFinder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIf;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
@EnabledIf("isDockerAvailable")
class KubernetesCommandTest extends KubernetesBaseTest {

    @Test
    public void shouldResolvePlugin() {
        FactoryFinder factoryFinder
                = new DefaultFactoryFinder(new DefaultClassResolver(), FactoryFinder.DEFAULT_PATH + "camel-jbang-plugin/");
        Assertions.assertTrue(factoryFinder.newInstance("camel-jbang-plugin-kubernetes").isPresent());
    }

    @Test
    public void shouldPrintKubernetesManifest() {
        CamelJBangMain.run(createMain(), "kubernetes", "run", "classpath:route.yaml",
                "--image-group", "camel-test", "--output", "yaml");

        List<HasMetadata> resources = kubernetesClient.load(getKubernetesManifestAsStream(printer.getOutput())).items();
        Assertions.assertEquals(2, resources.size());

        Deployment deployment = resources.stream()
                .filter(it -> Deployment.class.isAssignableFrom(it.getClass()))
                .map(Deployment.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));

        var matchLabels = deployment.getSpec().getSelector().getMatchLabels();
        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route", deployment.getMetadata().getLabels().get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("route", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals("route", matchLabels.get(BaseTrait.KUBERNETES_LABEL_NAME));
        Assertions.assertEquals("camel-jbang", matchLabels.get(BaseTrait.KUBERNETES_LABEL_MANAGED_BY));
        Assertions.assertEquals("camel-test/route:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals("IfNotPresent",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
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
