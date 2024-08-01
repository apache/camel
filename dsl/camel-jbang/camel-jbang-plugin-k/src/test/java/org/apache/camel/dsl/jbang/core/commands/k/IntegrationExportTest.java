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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationExportTest extends CamelKBaseTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws Exception {
        workingDir = Files.createTempDirectory("camel-k-integration-export").toFile();
        workingDir.deleteOnExit();
    }

    @Test
    public void shouldExportFromIntegration() throws Exception {
        IntegrationExport command
                = createCommand(new String[] { "classpath:org/apache/camel/dsl/jbang/core/commands/k/integration.yaml" },
                        workingDir.toString());
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(RuntimeType.quarkus);
        Assertions.assertEquals("routes", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("routes", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("routes", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(1, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("routes",
                deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("camel-test/routes:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().size());
        Assertions.assertEquals("MY_ENV_VAR",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getName());
        Assertions.assertEquals("foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getValue());
    }

    @Test
    public void shouldExportFromPipe() throws Exception {
        IntegrationExport command = createCommand(
                new String[] { "classpath:org/apache/camel/dsl/jbang/core/commands/k/pipe.yaml" }, workingDir.toString());
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);

        Deployment deployment = getDeployment(RuntimeType.quarkus);
        Assertions.assertEquals("timer-to-log", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("timer-to-log", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("timer-to-log", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(1, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("timer-to-log",
                deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("camel-test/timer-to-log:1.0-SNAPSHOT",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().size());
        Assertions.assertEquals("MY_ENV_VAR",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getName());
        Assertions.assertEquals("foo",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().get(0).getValue());
    }

    private IntegrationExport createCommand(String[] files, String exportDir) {
        return new IntegrationExport(
                new CamelJBangMain(),
                RuntimeType.quarkus, files, exportDir, "camel-test", false);
    }

    private Deployment getDeployment(RuntimeType rt) throws IOException {
        return getResource(Deployment.class)
                .orElseThrow(() -> new RuntimeCamelException("Cannot find deployment for: %s".formatted(rt.runtime())));
    }

    private <T extends HasMetadata> Optional<T> getResource(Class<T> type) throws IOException {
        var kind = type.getSimpleName().toLowerCase();
        File file = new File(workingDir, "src/main/jkube/%s.yml".formatted(kind));
        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                List<HasMetadata> resources = kubernetesClient.load(fis).items();
                return resources.stream()
                        .filter(it -> type.isAssignableFrom(it.getClass()))
                        .map(type::cast)
                        .findFirst();
            }
        }
        return Optional.empty();
    }
}
