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
import java.util.Collections;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import picocli.CommandLine;

/**
 * Checks how the cluster type and the per-cluster image defaults are resolved by {@link KubernetesRun}, without
 * exporting or building a project.
 */
@EnableKubernetesMockClient
class KubernetesRunClusterTypeTest {

    private static final String DOCKER_ENV_HINT = "eval $(minikube docker-env)";

    KubernetesMockServer server;
    KubernetesClient client;

    private StringPrinter printer;

    @BeforeEach
    void setup() {
        CommandLineHelper.useHomeDir("target");
        printer = new StringPrinter();
        KubernetesHelper.setKubernetesClient(client);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void explicitMinikubeAppliesImageDefaults() {
        KubernetesRun command = detectCluster("--cluster-type=minikube");

        Assertions.assertTrue(ClusterType.MINIKUBE.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("docker", command.imageBuilder);
        Assertions.assertFalse(command.imagePush);
        Assertions.assertFalse(printer.getOutput().contains(DOCKER_ENV_HINT), printer.getOutput());
    }

    @Test
    @ClearEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD")
    @ClearEnvironmentVariable(key = "DOCKER_TLS_VERIFY")
    void explicitMinikubeWithoutDockerEnvPrintsHint() {
        KubernetesRun command = detectCluster("--cluster-type=minikube");

        Assertions.assertTrue(ClusterType.MINIKUBE.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("docker", command.imageBuilder);
        Assertions.assertFalse(command.imagePush);
        Assertions.assertTrue(printer.getOutput().contains(DOCKER_ENV_HINT),
                "Expected the minikube docker-env hint, but the output was: " + printer.getOutput());
    }

    @Test
    @ClearEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD")
    @ClearEnvironmentVariable(key = "DOCKER_TLS_VERIFY")
    void dockerEnvHintIsPrintedOnceAcrossReloads() {
        KubernetesRun command = detectCluster("--cluster-type=minikube");
        // dev mode calls detectCluster() again on every reload
        command.detectCluster();
        command.detectCluster();

        String output = printer.getOutput();
        Assertions.assertEquals(output.indexOf(DOCKER_ENV_HINT), output.lastIndexOf(DOCKER_ENV_HINT), output);
        Assertions.assertTrue(output.contains(DOCKER_ENV_HINT), output);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void explicitMinikubeWithBareImagePushFlagPushes() {
        KubernetesRun command = detectCluster("--cluster-type=minikube", "--image-push");

        Assertions.assertEquals("docker", command.imageBuilder);
        Assertions.assertEquals(Boolean.TRUE, command.imagePush);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void explicitMinikubeKeepsImageOptionsSetByUser() {
        KubernetesRun command = detectCluster("--cluster-type=minikube", "--image-builder=jib", "--image-push=true");

        Assertions.assertTrue(ClusterType.MINIKUBE.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("jib", command.imageBuilder);
        Assertions.assertTrue(command.imagePush);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void detectedMinikubeKeepsImageBuilderSetByUser() {
        setupServerExpectsMinikube();
        KubernetesRun command = detectCluster("--image-builder=jib");

        Assertions.assertTrue(ClusterType.MINIKUBE.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("jib", command.imageBuilder);
        // not set by the user: the minikube no-push default only applies to the docker builder, a jib image is pushed
        Assertions.assertTrue(command.imagePush);
    }

    @Test
    @ClearEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD")
    @ClearEnvironmentVariable(key = "DOCKER_TLS_VERIFY")
    void explicitMinikubeWithoutImageBuildPrintsNoHint() {
        KubernetesRun command = detectCluster("--cluster-type=minikube", "--image-build=false");

        Assertions.assertEquals("docker", command.imageBuilder);
        Assertions.assertFalse(command.imagePush);
        Assertions.assertFalse(printer.getOutput().contains(DOCKER_ENV_HINT), printer.getOutput());
    }

    @Test
    @ClearEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD")
    @ClearEnvironmentVariable(key = "DOCKER_TLS_VERIFY")
    void explicitMinikubeWithOutputPrintsNoHint() {
        KubernetesRun command = detectCluster("--cluster-type=minikube", "--output=yaml");

        Assertions.assertEquals("docker", command.imageBuilder);
        Assertions.assertFalse(command.imagePush);
        Assertions.assertFalse(printer.getOutput().contains(DOCKER_ENV_HINT), printer.getOutput());
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void disableAutoSkipsMinikubeDefaults() {
        KubernetesRun command = detectCluster("--cluster-type=minikube", "--disable-auto");

        Assertions.assertTrue(ClusterType.MINIKUBE.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("jib", command.imageBuilder);
        Assertions.assertTrue(command.imagePush);
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "minikube")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "1")
    void explicitK3sIsNotDetected() {
        setupServerExpectsMinikube();
        KubernetesRun command = detectCluster("--cluster-type=k3s");

        Assertions.assertTrue(ClusterType.K3S.isEqualTo(command.clusterType), command.clusterType);
        Assertions.assertEquals("jib", command.imageBuilder);
        Assertions.assertTrue(command.imagePush);
    }

    private KubernetesRun detectCluster(String... args) {
        KubernetesRun command = new KubernetesRun(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        command.detectCluster();
        return command;
    }

    private void setupServerExpectsMinikube() {
        Node node = new NodeBuilder()
                .withNewMetadata()
                .withName("minikube")
                .withLabels(Collections.singletonMap("minikube.k8s.io/name", "minikube"))
                .endMetadata()
                .build();
        server.expect().get().withPath("/api/v1/nodes?labelSelector=minikube.k8s.io%2Fname")
                .andReturn(HttpURLConnection.HTTP_OK, new NodeListBuilder().addToItems(node).build())
                .once();
    }
}
