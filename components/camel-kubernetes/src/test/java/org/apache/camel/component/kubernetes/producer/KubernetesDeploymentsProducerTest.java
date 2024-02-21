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
package org.apache.camel.component.kubernetes.producer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
public class KubernetesDeploymentsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/apis/apps/v1/namespaces/test/deployments")
                .andReturn(200, new DeploymentListBuilder().addNewItem().and().build()).once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(1, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect()
                .withPath("/apis/apps/v1/namespaces/test/deployments?labelSelector="
                          + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new DeploymentListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
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
    void createDeployment() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        DeploymentSpec spec = new DeploymentSpecBuilder().withReplicas(13).build();
        Deployment de1
                = new DeploymentBuilder().withNewMetadata().withName("de1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().post().withPath("/apis/apps/v1/namespaces/test/deployments").andReturn(200, de1).once();

        Exchange ex = template.request("direct:createDeployment", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_SPEC, spec);
        });

        Deployment result = ex.getMessage().getBody(Deployment.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("de1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void updateDeployment() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        DeploymentSpec spec = new DeploymentSpecBuilder().withReplicas(13).build();
        Deployment de1
                = new DeploymentBuilder().withNewMetadata().withName("de1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().get().withPath("/apis/apps/v1/namespaces/test/deployments/de1")
                .andReturn(200,
                        new DeploymentBuilder().withNewMetadata().withName("de1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/apis/apps/v1/namespaces/test/deployments/de1").andReturn(200, de1).once();

        Exchange ex = template.request("direct:updateDeployment", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_SPEC, spec);
        });

        Deployment result = ex.getMessage().getBody(Deployment.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("de1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void deleteDeployment() {
        Deployment de1 = new DeploymentBuilder().withNewMetadata().withNamespace("test").withName("de1")
                .withResourceVersion("1").withGeneration(2L).endMetadata().withNewSpec()
                .withReplicas(0).endSpec().withNewStatus().withReplicas(1).withObservedGeneration(1L).endStatus().build();

        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments/de1").andReturn(200, de1).once();
        server.expect().withPath("/apis/extensions/v1beta1/namespaces/test/deployments/de1")
                .andReturn(200,
                        new DeploymentBuilder(de1).editStatus().withReplicas(0).withObservedGeneration(2L).endStatus().build())
                .times(5);
        server.expect().delete().withPath("/apis/apps/v1/namespaces/test/deployments/de1").andReturn(200, de1).once();

        Exchange ex = template.request("direct:deleteDeployment", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
        });

        boolean deDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(deDeleted);
    }

    @Test
    void scaleDeployment() {
        server.expect().withPath("/apis/apps/v1/namespaces/test/deployments/de1")
                .andReturn(200, new DeploymentBuilder().withNewMetadata().withName("de1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .once();

        server.expect().withPath("/apis/apps/v1/namespaces/test/deployments/de1/scale")
                .andReturn(200, new DeploymentBuilder().withNewMetadata().withName("de1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .always();
        Exchange ex = template.request("direct:scaleDeployment", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, 5);
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
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=listDeployments");
                from("direct:listByLabels")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=listDeploymentsByLabels");
                from("direct:deleteDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=deleteDeployment");
                from("direct:createDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=createDeployment");
                from("direct:updateDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=updateDeployment");
                from("direct:scaleDeployment")
                        .toF("kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=scaleDeployment");
            }
        };
    }
}
