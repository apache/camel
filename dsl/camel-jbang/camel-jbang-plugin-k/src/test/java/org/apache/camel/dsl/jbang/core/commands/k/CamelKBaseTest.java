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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.IntegrationSpec;
import org.apache.camel.v1.Pipe;
import org.apache.camel.v1.PipeSpec;
import org.junit.jupiter.api.*;

@Disabled("Deprecated and resource intensive")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CamelKBaseTest {

    protected static Integration integration;
    protected static Pipe pipe;

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
    public void setup() throws Exception {
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
                    IOHelper.loadText(CamelKBaseTest.class.getResourceAsStream("integration.yaml")), Integration.class);
        }

        Integration created = new Integration();
        created.getMetadata().setName(name);
        created.setSpec(new IntegrationSpec());
        created.getSpec().setTraits(integration.getSpec().getTraits());
        created.getSpec().setFlows(integration.getSpec().getFlows());

        return created;
    }

    protected Pipe createPipe() throws IOException {
        return createPipe("pipe");
    }

    protected Pipe createPipe(String name) throws IOException {
        if (pipe == null) {
            pipe = KubernetesHelper.yaml().loadAs(
                    IOHelper.loadText(CamelKBaseTest.class.getResourceAsStream("pipe.yaml")), Pipe.class);
        }

        Pipe created = new Pipe();
        created.getMetadata().setName(name);
        created.setSpec(new PipeSpec());
        created.getSpec().setSource(pipe.getSpec().getSource());
        created.getSpec().setSink(pipe.getSpec().getSink());
        created.getSpec().setSteps(pipe.getSpec().getSteps());
        created.getSpec().setErrorHandler(pipe.getSpec().getErrorHandler());
        created.getSpec().setIntegration(pipe.getSpec().getIntegration());

        return created;
    }

}
