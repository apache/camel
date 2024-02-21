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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.camel.dsl.jbang.core.commands.StringPrinter;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.IOHelper;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.IntegrationSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.yaml.snakeyaml.Yaml;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KubeBaseTest {

    protected static Integration integration;

    private KubernetesMockServer k8sServer;

    protected KubernetesClient kubernetesClient;

    protected StringPrinter printer;

    @BeforeAll
    public void setupFixtures() {
        k8sServer = new KubernetesMockServer(
                new Context(), new MockWebServer(),
                new HashMap<>(), new KubernetesCrudDispatcher(), false);

        kubernetesClient = k8sServer.createClient();

        CommandLineHelper.useHomeDir("target");
        PluginHelper.enable(PluginType.CAMEL_K);
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

    protected Integration createIntegration() throws IOException {
        return createIntegration("routes");
    }

    protected Integration createIntegration(String name) throws IOException {
        if (integration == null) {
            integration = KubernetesHelper.yaml().loadAs(
                    IOHelper.loadText(KubeBaseTest.class.getResourceAsStream("integration.yaml")), Integration.class);
        }

        Integration created = new Integration();
        created.getMetadata().setName(name);
        created.setSpec(new IntegrationSpec());
        created.getSpec().setTraits(integration.getSpec().getTraits());
        created.getSpec().setFlows(integration.getSpec().getFlows());

        return created;
    }

    protected String[] getDependencies(String yamlSource) {
        Yaml yaml = new Yaml();
        Map<String, Object> obj = yaml.load(yamlSource);
        //noinspection unchecked
        obj = (Map<String, Object>) obj.get("spec");
        //noinspection unchecked
        List<String> specDeps = (List<String>) obj.get("dependencies");
        return specDeps.toArray(new String[specDeps.size()]);
    }

}
