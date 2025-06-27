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
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.openshift.api.model.config.v1.ClusterVersion;
import io.fabric8.openshift.api.model.config.v1.ClusterVersionBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
@EnableKubernetesMockClient
class KubernetesDeleteTest {

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
    public void shouldDeleteNonExistentApp() throws Exception {
        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(client);
        command.name = "does-not-exist";
        int exit = command.doCall();
        boolean errorOutput = printer.getOutput().contains("Error trying to delete the app");
        if (errorOutput) {
            Assertions.assertEquals(1, exit, printer.getOutput());
        } else {
            Assertions.assertEquals(0, exit, printer.getOutput());
            Assertions.assertTrue(printer.getOutput().contains("No deployment found with name"), printer.getOutput());
        }
    }

    @Test
    @SetEnvironmentVariable(key = "MINIKUBE_ACTIVE_DOCKERD", value = "foo")
    @SetEnvironmentVariable(key = "DOCKER_TLS_VERIFY", value = "foo")
    public void shouldDeleteNonExistentAppInMinikube() throws Exception {
        Node nodeCR = new NodeBuilder()
                .withNewMetadata()
                .withName("minikube")
                .withLabels(Collections.singletonMap("minikube.k8s.io/name", "minikube"))
                .endMetadata()
                .build();
        NodeList nodeList = new NodeListBuilder().addToItems(nodeCR)
                .build();

        serverExpect("/api/v1/nodes?labelSelector=minikube.k8s.io%2Fname", nodeList);

        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(client);
        command.name = "does-not-exist";
        int exit = command.doCall();
        boolean errorOutput = printer.getOutput().contains("Error trying to delete the app");
        if (errorOutput) {
            Assertions.assertEquals(1, exit, printer.getOutput());
        } else {
            Assertions.assertEquals(0, exit, printer.getOutput());
            Assertions.assertTrue(printer.getOutput().contains("No deployment found with name"), printer.getOutput());
        }
    }

    @Test
    public void shouldDeleteNonExistentAppInOpenshift() throws Exception {
        ClusterVersion versionCR = new ClusterVersionBuilder()
                .withNewMetadata().withName("version").endMetadata()
                .withNewStatus()
                .withNewDesired()
                .withVersion("4.14.5")
                .endDesired()
                .endStatus()
                .build();

        serverExpect("/apis/config.openshift.io/v1/clusterversions/version", versionCR);

        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(client);
        command.name = "does-not-exist";
        int exit = command.doCall();
        boolean errorOutput = printer.getOutput().contains("Error trying to delete the app");
        if (errorOutput) {
            Assertions.assertEquals(1, exit, printer.getOutput());
        } else {
            Assertions.assertEquals(0, exit, printer.getOutput());
            Assertions.assertTrue(printer.getOutput().contains("No deployment found with name"), printer.getOutput());
        }
    }

    @Test
    public void shouldDeleteOnKubernetes() throws Exception {
        String name = "my-route";

        // Mock the delete request expectation
        serverDeleteExpect(
                "/apis/apps/v1/namespaces/test/deployments?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/services?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/apis/serving.knative.dev/v1/namespaces/test/services?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/configmaps?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/secrets?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");

        // Execute delete command
        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(client);
        command.namespace = "test";
        command.name = name;
        int exit = command.doCall();

        // Verify command execution
        Assertions.assertEquals(0, exit, printer.getOutput());

        // Verify deployment was deleted
        Assertions.assertNull(client.apps().deployments().withName(name).get());
        Assertions.assertNull(client.services().withName(name).get());
    }

    @Test
    public void shouldDeleteOnOpenShift() throws Exception {
        ClusterVersion versionCR = new ClusterVersionBuilder()
                .withNewMetadata().withName("version").endMetadata()
                .withNewStatus()
                .withNewDesired()
                .withVersion("4.14.5")
                .endDesired()
                .endStatus()
                .build();

        serverExpect("/apis/config.openshift.io/v1/clusterversions/version", versionCR);

        String name = "my-route";

        // Mock the delete request expectations for openshift resources
        serverDeleteExpect(
                "/apis/apps/v1/namespaces/test/deployments?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/services?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/apis/build.openshift.io/v1/namespaces/test/buildconfigs?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/apis/image.openshift.io/v1/namespaces/test/imagestreams?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/apis/route.openshift.io/v1/namespaces/test/routes?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/apis/serving.knative.dev/v1/namespaces/test/services?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/configmaps?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");
        serverDeleteExpect(
                "/api/v1/namespaces/test/secrets?labelSelector=app.kubernetes.io%2Fname%3Dmy-route");

        // Execute delete command
        KubernetesDelete command = new KubernetesDelete(new CamelJBangMain().withPrinter(printer));
        command.withClient(client);
        command.namespace = "test";
        command.name = name;
        int exit = command.doCall();

        // Verify command execution
        Assertions.assertEquals(0, exit, printer.getOutput());

        OpenShiftClient ocpClient = client.adapt(OpenShiftClient.class);

        // Verify deployment was deleted
        Assertions.assertNull(client.apps().deployments().withName(name).get());
        Assertions.assertNull(client.services().withName(name).get());
        Assertions.assertNull(ocpClient.buildConfigs().withName(name).get());
        Assertions.assertNull(ocpClient.imageStreams().withName(name).get());
        Assertions.assertNull(ocpClient.routes().withName(name).get());
    }

    private void serverDeleteExpect(String path) {
        server.expect().delete()
                .withPath(path)
                .andReturn(HttpURLConnection.HTTP_OK, null)
                .once();
    }

    private void serverExpect(String path, Object response) {
        server.expect().get()
                .withPath(path)
                .andReturn(HttpURLConnection.HTTP_OK, response)
                .once();
    }

}
