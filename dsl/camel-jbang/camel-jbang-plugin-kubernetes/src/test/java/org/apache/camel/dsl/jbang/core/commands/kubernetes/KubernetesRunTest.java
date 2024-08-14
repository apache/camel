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
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesRunTest extends KubernetesBaseTest {

    private StringPrinter printer;

    @BeforeEach
    public void setup() {
        printer = new StringPrinter();
    }

    @Test
    public void shouldHandleMissingSourceFile() throws Exception {
        KubernetesRun command = createCommand();
        command.filePaths = new String[] { "mickey-mouse.groovy" };
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);

        Assertions.assertTrue(printer.getOutput().contains("Project export failed"));
    }

    @Test
    public void shouldGenerateKubernetesManifest() throws Exception {
        KubernetesRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        List<HasMetadata> resources = kubernetesClient.load(getKubernetesManifestAsStream(printer.getOutput())).items();
        Assertions.assertEquals(3, resources.size());

        Deployment deployment = resources.stream()
                .filter(it -> Deployment.class.isAssignableFrom(it.getClass()))
                .map(Deployment.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));

        Assertions.assertEquals("route", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("route", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("route", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(3, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("route", deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("docker.io/camel-test/route:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals("Always",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy());
    }

    @Test
    public void shouldHandleUnsupportedOutputFormat() throws Exception {
        KubernetesRun command = createCommand();
        command.filePaths = new String[] { "classpath:route.yaml" };
        command.output = "wrong";

        Assertions.assertEquals(1, command.doCall());
        Assertions.assertTrue(printer.getOutput().endsWith("Unsupported output format 'wrong' (supported: yaml, json)"));
    }

    private KubernetesRun createCommand() {
        KubernetesRun command = new KubernetesRun(new CamelJBangMain().withPrinter(printer));
        command.output = "yaml";
        command.imageGroup = "camel-test";
        command.imageBuild = false;
        command.imagePush = false;
        return command;
    }

}
