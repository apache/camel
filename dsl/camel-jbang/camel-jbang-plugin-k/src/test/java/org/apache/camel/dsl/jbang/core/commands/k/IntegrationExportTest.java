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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationExportTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws IOException {
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

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("routes", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("routes", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("routes", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(1, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("routes",
                deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("quay.io/camel-test/routes:1.0-SNAPSHOT",
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

        Deployment deployment = getDeployment(workingDir);
        Assertions.assertEquals("timer-to-log", deployment.getMetadata().getName());
        Assertions.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Assertions.assertEquals("timer-to-log", deployment.getMetadata().getLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("timer-to-log", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName());
        Assertions.assertEquals(1, deployment.getSpec().getSelector().getMatchLabels().size());
        Assertions.assertEquals("timer-to-log",
                deployment.getSpec().getSelector().getMatchLabels().get(BaseTrait.INTEGRATION_LABEL));
        Assertions.assertEquals("quay.io/camel-test/timer-to-log:1.0-SNAPSHOT",
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

    private Deployment getDeployment(File workingDir) throws IOException {
        try (FileInputStream fis = new FileInputStream(new File(workingDir, "src/main/kubernetes/kubernetes.yml"))) {
            return KubernetesHelper.yaml().loadAs(fis, Deployment.class);
        }
    }

}
