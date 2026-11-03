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
package org.apache.camel.component.openshift.producer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.openshift.api.model.BuildBuilder;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The namespace of the openshift producers: the CamelKubernetesNamespaceName header, else the namespace option, else
 * the namespace of the client (deployment configs) or all namespaces (builds and build configs).
 */
@EnableKubernetesMockClient(crud = true)
class OpenshiftProducersNamespaceTest extends KubernetesTestSupport {

    private static final Map<String, String> LABELS = Map.of("app", "demo");

    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public NamespacedKubernetesClient getClient() {
        return client;
    }

    @BeforeEach
    void createResources() {
        OpenShiftClient openShift = client.adapt(OpenShiftClient.class);
        // the mock client uses the namespace "test"
        for (String namespace : List.of("test", "other")) {
            openShift.deploymentConfigs().inNamespace(namespace).resource(new DeploymentConfigBuilder().withNewMetadata()
                    .withName("dc-" + namespace).withLabels(LABELS).endMetadata()
                    .withSpec(new DeploymentConfigSpecBuilder().withReplicas(1).build()).build()).create();
            openShift.builds().inNamespace(namespace).resource(new BuildBuilder().withNewMetadata()
                    .withName("build-" + namespace).withLabels(LABELS).endMetadata().build()).create();
            openShift.buildConfigs().inNamespace(namespace).resource(new BuildConfigBuilder().withNewMetadata()
                    .withName("bc-" + namespace).withLabels(LABELS).endMetadata().build()).create();
        }
    }

    @Test
    void deploymentConfigsGetUsesNamespaceHeader() {
        DeploymentConfig dc = template.requestBodyAndHeaders("direct:dc-get", null,
                Map.of(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other",
                        KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc-other"),
                DeploymentConfig.class);

        assertNotNull(dc);
        assertEquals("other", dc.getMetadata().getNamespace());
    }

    @Test
    void deploymentConfigsGetUsesNamespaceOption() {
        DeploymentConfig dc = template.requestBodyAndHeader("direct:dc-get-option", null,
                KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc-other", DeploymentConfig.class);

        assertNotNull(dc);
        assertEquals("other", dc.getMetadata().getNamespace());
    }

    @Test
    void deploymentConfigsHeaderWinsOverNamespaceOption() {
        DeploymentConfig dc = template.requestBodyAndHeaders("direct:dc-get-option", null,
                Map.of(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test",
                        KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc-other"),
                DeploymentConfig.class);

        assertNull(dc);
    }

    @Test
    void deploymentConfigsListUsesNamespaceHeader() {
        List<?> result = template.requestBodyAndHeader("direct:dc-list", null,
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other", List.class);

        assertEquals(List.of("dc-other"), names(result));
    }

    @Test
    void deploymentConfigsListWithoutNamespaceUsesClientNamespace() {
        List<?> result = template.requestBody("direct:dc-list", null, List.class);

        assertEquals(List.of("dc-test"), names(result));
    }

    @Test
    void deploymentConfigsListByLabelsUsesNamespaceHeader() {
        List<?> result = template.requestBodyAndHeaders("direct:dc-list-labels", null,
                Map.of(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other",
                        KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, LABELS),
                List.class);

        assertEquals(List.of("dc-other"), names(result));
    }

    @Test
    void deploymentConfigsListByLabelsRequiresLabels() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:dc-list-labels", ""));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Listing Deployment Configs by labels requires specifying labels", e.getCause().getMessage());
    }

    @Test
    void deploymentConfigsCreateUsesNamespaceOption() {
        DeploymentConfig dc = template.requestBodyAndHeaders("direct:dc-create-option", null,
                Map.of(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc-new",
                        KubernetesConstants.KUBERNETES_DEPLOYMENT_CONFIG_SPEC,
                        new DeploymentConfigSpecBuilder().withReplicas(2).build()),
                DeploymentConfig.class);

        assertEquals("other", dc.getMetadata().getNamespace());
        assertNotNull(client.adapt(OpenShiftClient.class).deploymentConfigs().inNamespace("other").withName("dc-new").get());
    }

    @Test
    void deploymentConfigsScaleWithoutStatusReturnsRequestedReplicas() {
        Exchange exchange = template.request("direct:dc-scale", e -> {
            e.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other");
            e.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc-other");
            e.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, 3);
        });

        assertNull(exchange.getException());
        assertEquals(3, exchange.getMessage().getBody(Integer.class));
        assertEquals(3, client.adapt(OpenShiftClient.class).deploymentConfigs().inNamespace("other").withName("dc-other")
                .get().getSpec().getReplicas());
    }

    @Test
    void buildsListUsesNamespaceHeader() {
        List<?> result = template.requestBodyAndHeader("direct:builds-list", null,
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other", List.class);

        assertEquals(List.of("build-other"), names(result));
    }

    @Test
    void buildsListUsesNamespaceOption() {
        List<?> result = template.requestBody("direct:builds-list-option", null, List.class);

        assertEquals(List.of("build-other"), names(result));
    }

    @Test
    void buildsListWithoutNamespaceListsAllNamespaces() {
        List<?> result = template.requestBody("direct:builds-list", null, List.class);

        assertEquals(List.of("build-other", "build-test"), names(result));
    }

    @Test
    void buildsGetUsesNamespaceOption() {
        Object build = template.requestBodyAndHeader("direct:builds-get-option", null,
                KubernetesConstants.KUBERNETES_BUILD_NAME, "build-other");

        assertEquals("other", ((HasMetadata) build).getMetadata().getNamespace());
    }

    @Test
    void buildsListByLabelsRequiresLabels() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:builds-list-labels", ""));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Listing Builds by labels requires specifying labels", e.getCause().getMessage());
    }

    @Test
    void buildConfigsListUsesNamespaceHeader() {
        List<?> result = template.requestBodyAndHeader("direct:bc-list", null,
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other", List.class);

        assertEquals(List.of("bc-other"), names(result));
    }

    @Test
    void buildConfigsListUsesNamespaceOption() {
        List<?> result = template.requestBody("direct:bc-list-option", null, List.class);

        assertEquals(List.of("bc-other"), names(result));
    }

    @Test
    void buildConfigsListWithoutNamespaceListsAllNamespaces() {
        List<?> result = template.requestBody("direct:bc-list", null, List.class);

        assertEquals(List.of("bc-other", "bc-test"), names(result));
    }

    @Test
    void buildConfigsListByLabelsUsesNamespaceOption() {
        List<?> result = template.requestBodyAndHeader("direct:bc-list-labels-option", null,
                KubernetesConstants.KUBERNETES_BUILD_CONFIGS_LABELS, LABELS, List.class);

        assertEquals(List.of("bc-other"), names(result));
    }

    @Test
    void buildConfigsListByLabelsRequiresLabels() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:bc-list-labels", ""));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Listing Build Configs by labels requires specifying labels", e.getCause().getMessage());
    }

    private static List<String> names(List<?> resources) {
        return resources.stream().map(r -> ((HasMetadata) r).getMetadata().getName()).sorted()
                .collect(Collectors.toList());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String dc = "openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=";
                from("direct:dc-get").to(dc + "getDeploymentConfig");
                from("direct:dc-get-option").to(dc + "getDeploymentConfig&namespace=other");
                from("direct:dc-list").to(dc + "listDeploymentConfigs");
                from("direct:dc-list-labels").to(dc + "listDeploymentConfigsByLabels");
                from("direct:dc-create-option").to(dc + "createDeploymentConfig&namespace=other");
                from("direct:dc-scale").to(dc + "scaleDeploymentConfig");

                String builds = "openshift-builds:///?kubernetesClient=#kubernetesClient&operation=";
                from("direct:builds-list").to(builds + "listBuilds");
                from("direct:builds-list-option").to(builds + "listBuilds&namespace=other");
                from("direct:builds-get-option").to(builds + "getBuild&namespace=other");
                from("direct:builds-list-labels").to(builds + "listBuildsByLabels");

                String bc = "openshift-build-configs:///?kubernetesClient=#kubernetesClient&operation=";
                from("direct:bc-list").to(bc + "listBuildConfigs");
                from("direct:bc-list-option").to(bc + "listBuildConfigs&namespace=other");
                from("direct:bc-list-labels").to(bc + "listBuildConfigsByLabels");
                from("direct:bc-list-labels-option").to(bc + "listBuildConfigsByLabels&namespace=other");
            }
        };
    }
}
