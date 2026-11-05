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

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeSpecBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Producers of the kubernetes components against the mock server in CRUD mode.
 */
@EnableKubernetesMockClient(crud = true)
public class KubernetesProducersCrudTest extends KubernetesTestSupport {

    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void scaleDeploymentWithoutStatus() {
        client.apps().deployments().inNamespace("test").resource(new DeploymentBuilder().withNewMetadata().withName("de1")
                .endMetadata().withNewSpec().withReplicas(1).endSpec().build()).create();

        Exchange ex = template.request("direct:scaleDeployment", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, "de1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, 3);
        });

        assertFalse(ex.isFailed(), () -> "scale failed: " + ex.getException());
        assertEquals(3, ex.getMessage().getBody(Integer.class));
        assertEquals(3, client.apps().deployments().inNamespace("test").withName("de1").get().getSpec().getReplicas());
    }

    @Test
    void scaleReplicationControllerWithoutStatus() {
        client.replicationControllers().inNamespace("test").resource(new ReplicationControllerBuilder().withNewMetadata()
                .withName("rc1").endMetadata().withNewSpec().withReplicas(1).endSpec().build()).create();

        Exchange ex = template.request("direct:scaleReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "rc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_REPLICAS, 2);
        });

        assertFalse(ex.isFailed(), () -> "scale failed: " + ex.getException());
        assertEquals(2, ex.getMessage().getBody(Integer.class));
    }

    @Test
    void namespaceOptionWhenNoHeader() {
        client.services().inNamespace("test").resource(new ServiceBuilder().withNewMetadata().withName("svc1")
                .endMetadata().build()).create();
        client.services().inNamespace("other").resource(new ServiceBuilder().withNewMetadata().withName("svc2")
                .endMetadata().build()).create();

        Exchange got = template.request("direct:getService",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "svc1"));
        assertFalse(got.isFailed(), () -> "get failed: " + got.getException());
        assertEquals("svc1", got.getMessage().getBody(Service.class).getMetadata().getName());

        Exchange listed = template.request("direct:listServices", exchange -> {
        });
        assertFalse(listed.isFailed(), () -> "list failed: " + listed.getException());
        List<?> services = listed.getMessage().getBody(List.class);
        assertEquals(1, services.size());
        assertEquals("svc1", ((Service) services.get(0)).getMetadata().getName());
    }

    @Test
    void namespaceHeaderWinsOverOption() {
        client.configMaps().inNamespace("other").resource(new ConfigMapBuilder().withNewMetadata().withName("cm1")
                .endMetadata().addToData("key", "value").build()).create();

        Exchange got = template.request("direct:getConfigMap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "other");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
        });

        assertFalse(got.isFailed(), () -> "get failed: " + got.getException());
        assertNotNull(got.getMessage().getBody());
    }

    @Test
    void listPersistentVolumesByLabelsWithoutLabelsFails() {
        Exchange ex = template.request("direct:listPersistentVolumesByLabels", exchange -> {
        });

        assertTrue(ex.isFailed());
        assertInstanceOf(IllegalArgumentException.class, ex.getException());
    }

    @Test
    void listNodesByLabelsWithoutLabelsFails() {
        Exchange ex = template.request("direct:listNodesByLabels", exchange -> {
        });

        assertTrue(ex.isFailed());
        assertInstanceOf(IllegalArgumentException.class, ex.getException());
    }

    @Test
    void createNodeUsesTheNodesLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "camel");

        Exchange ex = template.request("direct:createNode", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_NAME, "node1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_SPEC, new NodeSpecBuilder().build());
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, labels);
        });

        assertFalse(ex.isFailed(), () -> "create failed: " + ex.getException());
        assertEquals(labels, ex.getMessage().getBody(Node.class).getMetadata().getLabels());
        assertEquals(labels, client.nodes().withName("node1").get().getMetadata().getLabels());
    }

    @Test
    void createSecretWithAnnotationsKeepsTheSecretOfTheHeader() {
        Secret secret = new SecretBuilder().withNewMetadata().withName("secret1").endMetadata()
                .addToStringData("password", "changeit").build();
        Map<String, String> annotations = new HashMap<>();
        annotations.put("app", "camel");

        Exchange ex = template.request("direct:createSecret", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET, secret);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRETS_ANNOTATIONS, annotations);
        });

        assertFalse(ex.isFailed(), () -> "create failed: " + ex.getException());
        assertEquals(annotations, ex.getMessage().getBody(Secret.class).getMetadata().getAnnotations());
        assertTrue(secret.getMetadata().getAnnotations() == null || secret.getMetadata().getAnnotations().isEmpty());
    }

    @Test
    void createSecretWithAnnotationsWithoutMetadata() {
        Secret secret = new SecretBuilder().addToStringData("password", "changeit").build();
        Map<String, String> annotations = new HashMap<>();
        annotations.put("app", "camel");

        Exchange ex = template.request("direct:createSecret", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRET, secret);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SECRETS_ANNOTATIONS, annotations);
        });

        // the mock server takes a Secret without a name: what matters is that it is sent, not a NullPointerException
        assertFalse(ex.getException() instanceof NullPointerException, () -> "create failed: " + ex.getException());
        assertNull(secret.getMetadata());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:scaleDeployment").to(
                        "kubernetes-deployments:///?kubernetesClient=#kubernetesClient&operation=scaleDeployment");
                from("direct:scaleReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient"
                                                             + "&operation=scaleReplicationController");
                from("direct:getService").to(
                        "kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=getService&namespace=test");
                from("direct:listServices").to(
                        "kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=listServices&namespace=test");
                from("direct:getConfigMap").to(
                        "kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=getConfigMap&namespace=test");
                from("direct:listPersistentVolumesByLabels").to(
                        "kubernetes-persistent-volumes:///?kubernetesClient=#kubernetesClient"
                                                                + "&operation=listPersistentVolumesByLabels");
                from("direct:listNodesByLabels").to(
                        "kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=listNodesByLabels");
                from("direct:createNode").to(
                        "kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=createNode");
                from("direct:createSecret").to(
                        "kubernetes-secrets:///?kubernetesClient=#kubernetesClient&operation=createSecret");
            }
        };
    }
}
