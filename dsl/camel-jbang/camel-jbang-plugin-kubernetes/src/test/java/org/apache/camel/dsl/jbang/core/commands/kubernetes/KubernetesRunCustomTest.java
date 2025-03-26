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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.openshift.api.model.config.v1.ClusterVersion;
import io.fabric8.openshift.api.model.config.v1.ClusterVersionBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ContainerTrait;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import picocli.CommandLine;

/* This test class doesn't use the KubernetesRunTest since we had to use the @EnableKubernetesMockClient
 * annotation which works differently from KubernetesBaseTest manual creation of the KubernetesMockServer
 * and it seems the @TestInstance(TestInstance.Lifecycle.PER_CLASS) interferes with the way the
 * @EnableKubernetesMockClient works. In another work we can plan to tackle this test inheritance mechanism.
 *
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
@EnableKubernetesMockClient
class KubernetesRunCustomTest {

    protected KubernetesMockServer server;
    protected KubernetesClient client;
    protected StringPrinter printer;

    @BeforeEach
    public void setup() {
        // Set Camel version with system property value, usually set via Maven surefire plugin
        // In case you run this test via local Java IDE you need to provide the system property or a default value here
        VersionHelper.setCamelVersion(System.getProperty("camel.version", ""));
        printer = new StringPrinter();
    }

    @Test
    public void disableAutomaticClusterDetection() throws Exception {
        KubernetesHelper.setKubernetesClient(client);
        setupServerExpectsOpenshift();
        KubernetesRun command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--output=yaml",
                "--disable-auto");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertEquals(ClusterType.KUBERNETES.name().toLowerCase(), command.clusterType.toLowerCase());

        var manifest = KubernetesBaseTest.getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = client.load(manifest).items();
        // expects Service, Deployment manifests in kubernetes.yml
        Assertions.assertEquals(2, resources.size());
    }

    @Test
    public void detectOpenshiftCluster() throws Exception {
        KubernetesHelper.setKubernetesClient(client);
        setupServerExpectsOpenshift();
        KubernetesRun command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--output=yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertEquals(ClusterType.OPENSHIFT.name().toLowerCase(), command.clusterType.toLowerCase());

        var manifest = KubernetesBaseTest.getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = client.load(manifest).items();
        // expects Service, Deployment, Route manifests in openshift.yml
        Assertions.assertEquals(3, resources.size());
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "foo")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "foo")
    public void detectMinikubeCluster() throws Exception {
        KubernetesHelper.setKubernetesClient(client);
        setupServerExpectsMinikube();
        KubernetesRun command = createCommand(new String[] { "classpath:route.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--output=yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertEquals(ClusterType.MINIKUBE.name().toLowerCase(), command.clusterType.toLowerCase());

        var manifest = KubernetesBaseTest.getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = client.load(manifest).items();
        // expects Service, Deployment manifests in kubernetes.yml
        Assertions.assertEquals(2, resources.size());

        Assertions.assertFalse(command.imagePush);
        Assertions.assertEquals("docker", command.imageBuilder);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "foo")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "foo")
    public void shouldGenerateKnativeService() throws Exception {
        KubernetesHelper.setKubernetesClient(client);
        setupServerExpectsMinikube();
        KubernetesRun command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--trait", "knative-service.enabled=true",
                "--image-registry=quay.io", "--image-group=camel-test", "--output=yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertEquals(ClusterType.MINIKUBE.name().toLowerCase(), command.clusterType.toLowerCase());

        // as the k8s:resource task is skipped for knative-service, there won't be a kubernetes.yml
        // so, we add a triple dash to emulate the first line of the kubernetes.yml
        String output = "---" + System.lineSeparator() + printer.getOutput();
        var manifest = KubernetesBaseTest.getKubernetesManifestAsStream(output, command.output);
        List<HasMetadata> resources = client.load(manifest).items();
        // expects KnativeService only
        Assertions.assertEquals(1, resources.size());

        io.fabric8.knative.serving.v1.Service ksvc = resources.stream()
                .filter(it -> io.fabric8.knative.serving.v1.Service.class.isAssignableFrom(it.getClass()))
                .map(io.fabric8.knative.serving.v1.Service.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing KnativeService in Kubernetes manifest"));

        var containers = ksvc.getSpec().getTemplate().getSpec().getContainers();
        Assertions.assertEquals(ContainerTrait.KNATIVE_CONTAINER_PORT_NAME, containers.get(0).getPorts().get(0).getName());
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "foo")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "foo")
    public void shouldGenerateRegularService() throws Exception {
        KubernetesHelper.setKubernetesClient(client);
        setupServerExpectsMinikube();
        KubernetesRun command = createCommand(new String[] { "classpath:route-service.yaml" },
                "--image-registry=quay.io", "--image-group=camel-test", "--output=yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertEquals(ClusterType.MINIKUBE.name().toLowerCase(), command.clusterType.toLowerCase());

        var manifest = KubernetesBaseTest.getKubernetesManifestAsStream(printer.getOutput(), command.output);
        List<HasMetadata> resources = client.load(manifest).items();
        // expects service and deployment only
        Assertions.assertEquals(2, resources.size());

        Service svc = resources.stream()
                .filter(it -> Service.class.isAssignableFrom(it.getClass()))
                .map(Service.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing Service in Kubernetes manifest"));

        Deployment deployment = resources.stream()
                .filter(it -> Deployment.class.isAssignableFrom(it.getClass()))
                .map(Deployment.class::cast)
                .findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Missing deployment in Kubernetes manifest"));

        var containers = deployment.getSpec().getTemplate().getSpec().getContainers();
        Assertions.assertEquals(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME, containers.get(0).getPorts().get(0).getName());
        Assertions.assertEquals(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME, svc.getSpec().getPorts().get(0).getName());
    }

    private void setupServerExpectsMinikube() {
        Node nodeCR = new NodeBuilder()
                .withNewMetadata()
                .withName("minikube")
                .withLabels(Collections.singletonMap("minikube.k8s.io/name", "minikube"))
                .endMetadata()
                .build();
        NodeList nodeList = new NodeListBuilder().addToItems(nodeCR)
                .build();
        server.expect().get().withPath("/api/v1/nodes?labelSelector=minikube.k8s.io%2Fname")
                .andReturn(HttpURLConnection.HTTP_OK, nodeList)
                .once();
    }

    private void setupServerExpectsOpenshift() {
        ClusterVersion versionCR = new ClusterVersionBuilder()
                .withNewMetadata().withName("version").endMetadata()
                .withNewStatus()
                .withNewDesired()
                .withVersion("4.14.5")
                .endDesired()
                .endStatus()
                .build();

        server.expect().get().withPath("/apis/config.openshift.io/v1/clusterversions/version")
                .andReturn(HttpURLConnection.HTTP_OK, versionCR)
                .once();
    }

    private KubernetesRun createCommand(String[] files, String... args) {
        var argsArr = Optional.ofNullable(args).orElse(new String[0]);
        var argsLst = new ArrayList<>(Arrays.asList(argsArr));
        var jbangMain = new CamelJBangMain().withPrinter(printer);
        KubernetesRun command = new KubernetesRun(jbangMain, files);
        CommandLine.populateCommand(command, argsLst.toArray(new String[0]));
        command.imageBuild = false;
        command.imagePush = false;
        return command;
    }

}
