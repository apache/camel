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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigListBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
public class OpenshiftDeploymentConfigsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public NamespacedKubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs")
                .andReturn(200, new DeploymentConfigListBuilder().addNewItem().and().build()).once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(1, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect()
                .withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs?labelSelector="
                          + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200,
                        new DeploymentConfigListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void createDeploymentConfig() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        DeploymentConfigSpec spec = new DeploymentConfigSpecBuilder().withReplicas(13).build();
        DeploymentConfig de1
                = new DeploymentConfigBuilder().withNewMetadata().withName("de1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().post().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs").andReturn(200, de1)
                .once();

        Exchange ex = template.request("direct:create", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_CONFIG_SPEC, spec);
        });

        DeploymentConfig result = ex.getMessage().getBody(DeploymentConfig.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("de1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void updateDeploymentConfig() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        DeploymentConfigSpec spec = new DeploymentConfigSpecBuilder().withReplicas(13).build();
        DeploymentConfig de1
                = new DeploymentConfigBuilder().withNewMetadata().withName("de1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().get().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/de1").andReturn(200,
                new DeploymentConfigBuilder().withNewMetadata().withName("de1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/de1").andReturn(200, de1)
                .once();

        Exchange ex = template.request("direct:update", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_CONFIG_SPEC, spec);
        });

        DeploymentConfig result = ex.getMessage().getBody(DeploymentConfig.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("de1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void deleteDeploymentConfig() {
        DeploymentConfig de1 = new DeploymentConfigBuilder().withNewMetadata().withNamespace("test").withName("dc1")
                .withResourceVersion("1").withGeneration(2L).endMetadata().withNewSpec()
                .withReplicas(0).endSpec().withNewStatus().withReplicas(1).withObservedGeneration(1L).endStatus().build();

        server.expect().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/dc1")
                .andReturn(200, de1).once();
        server.expect().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/dc1")
                .andReturn(200,
                        new DeploymentConfigBuilder(de1).editStatus().withReplicas(0).withObservedGeneration(2L).endStatus()
                                .build())
                .times(5);

        Exchange ex = template.request("direct:delete", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
        });

        boolean deDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(deDeleted);
    }

    @Test
    void scaleDeploymentConfig() {
        server.expect().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/dc1")
                .andReturn(200, new DeploymentConfigBuilder().withNewMetadata().withName("dc1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .once();

        server.expect().withPath("/apis/apps.openshift.io/v1/namespaces/test/deploymentconfigs/dc1/scale")
                .andReturn(200, new DeploymentConfigBuilder().withNewMetadata().withName("dc1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .always();

        Exchange ex = template.request("direct:scale", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "dc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, 1);
        });

        int replicas = ex.getMessage().getBody(Integer.class);

        assertEquals(5, replicas);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=listDeploymentConfigs");
                from("direct:listByLabels")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=listDeploymentConfigsByLabels");
                from("direct:create")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=createDeploymentConfig");
                from("direct:update")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=updateDeploymentConfig");
                from("direct:delete")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=deleteDeploymentConfig");
                from("direct:scale")
                        .toF("openshift-deploymentconfigs:///?kubernetesClient=#kubernetesClient&operation=scaleDeploymentConfig");
            }
        };
    }
}
