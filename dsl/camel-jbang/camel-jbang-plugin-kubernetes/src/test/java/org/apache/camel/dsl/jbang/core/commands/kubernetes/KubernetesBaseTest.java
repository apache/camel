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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KubernetesBaseTest {

    private KubernetesMockServer k8sServer;

    protected KubernetesClient kubernetesClient;

    protected StringPrinter printer;

    public static boolean isDockerAvailable() {
        return ContainerEnvironmentUtil.isDockerAvailable();
    }

    @BeforeAll
    public void setupFixtures() {
        k8sServer = new KubernetesMockServer(
                new Context(), new MockWebServer(),
                new HashMap<>(), new KubernetesCrudDispatcher(), false);

        kubernetesClient = k8sServer.createClient();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.enable(PluginType.KUBERNETES);
    }

    @BeforeEach
    public void setup() {
        printer = new StringPrinter();
        k8sServer.reset();
    }

    @AfterAll
    public void cleanup() {
        k8sServer.destroy();
    }

    protected InputStream getKubernetesManifestAsStream(String printerOutput) {
        return getKubernetesManifestAsStream(printerOutput, "yaml");
    }

    protected InputStream getKubernetesManifestAsStream(String printerOutput, String output) {
        if (output.equals("yaml")) {
            String manifest = StringHelper.after(printerOutput, "---");
            if (manifest == null) {
                throw new RuntimeException("Failed to find Kubernetes manifest in output: %n%s%n".formatted(printerOutput));
            }
            return new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
        }
        throw new RuntimeException("Unsupported output format: " + output);
    }
}
