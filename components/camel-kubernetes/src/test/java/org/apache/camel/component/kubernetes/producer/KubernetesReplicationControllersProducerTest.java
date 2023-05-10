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
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerListBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
public class KubernetesReplicationControllersProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/replicationcontrollers")
                .andReturn(200,
                        new ReplicationControllerListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/replicationcontrollers?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200,
                        new ReplicationControllerListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void getReplicationControllerTest() {
        ReplicationController rc1
                = new ReplicationControllerBuilder().withNewMetadata().withName("rc1").withNamespace("test").and().build();

        server.expect().withPath("/api/v1/namespaces/test/replicationcontrollers/rc1").andReturn(200, rc1).once();
        Exchange ex = template.request("direct:getReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "rc1");
        });

        ReplicationController result = ex.getMessage().getBody(ReplicationController.class);

        assertNotNull(result);
    }

    @Test
    void createReplicationController() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ReplicationControllerSpec spec = new ReplicationControllerSpecBuilder().withReplicas(13).build();
        ReplicationController rc1 = new ReplicationControllerBuilder().withNewMetadata().withName("rc1").withNamespace("test")
                .withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().post().withPath("/api/v1/namespaces/test/replicationcontrollers").andReturn(200, rc1).once();

        Exchange ex = template.request("direct:createReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "rc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC, spec);
        });

        ReplicationController result = ex.getMessage().getBody(ReplicationController.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("rc1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void updateReplicationController() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ReplicationControllerSpec spec = new ReplicationControllerSpecBuilder().withReplicas(13).build();
        ReplicationController rc1 = new ReplicationControllerBuilder().withNewMetadata().withName("rc1").withNamespace("test")
                .withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().get().withPath("/api/v1/namespaces/test/replicationcontrollers/rc1")
                .andReturn(200, new ReplicationControllerBuilder().withNewMetadata().withName("rc1").withNamespace("test")
                        .endMetadata().build())
                .once();
        server.expect().put().withPath("/api/v1/namespaces/test/replicationcontrollers/rc1").andReturn(200, rc1).once();

        Exchange ex = template.request("direct:updateReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "rc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC, spec);
        });

        ReplicationController result = ex.getMessage().getBody(ReplicationController.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("rc1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(13, result.getSpec().getReplicas());
    }

    @Test
    void deleteReplicationController() {
        server.expect().withPath("/api/v1/namespaces/test/replicationcontrollers/repl1")
                .andReturn(200, new ReplicationControllerBuilder().withNewMetadata().withName("repl1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(0).endSpec().withNewStatus()
                        .withReplicas(1).endStatus().build())
                .once();

        server.expect().withPath("/api/v1/namespaces/test/replicationcontrollers/repl1")
                .andReturn(200, new ReplicationControllerBuilder().withNewMetadata().withName("repl1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(0).endSpec().withNewStatus()
                        .withReplicas(0).endStatus().build())
                .times(5);

        Exchange ex = template.request("direct:deleteReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "repl1");
        });

        boolean rcDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(rcDeleted);
    }

    @Test
    void scaleReplicationController() {
        server.expect().withPath("/api/v1/namespaces/test/replicationcontrollers/repl1")
                .andReturn(200, new ReplicationControllerBuilder().withNewMetadata().withName("repl1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .once();

        server.expect().withPath("/api/v1/namespaces/test/replicationcontrollers/repl1/scale")
                .andReturn(200, new ReplicationControllerBuilder().withNewMetadata().withName("repl1")
                        .withResourceVersion("1").endMetadata().withNewSpec().withReplicas(5).endSpec().withNewStatus()
                        .withReplicas(5).endStatus().build())
                .always();
        Exchange ex = template.request("direct:scaleReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "repl1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_REPLICAS, 1);
        });

        Awaitility.await().atMost(3, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(5, ex.getMessage().getBody(Integer.class)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=listReplicationControllers");
                from("direct:listByLabels").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=listReplicationControllersByLabels");
                from("direct:getReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=getReplicationController");
                from("direct:scaleReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=scaleReplicationController");
                from("direct:createReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=createReplicationController");
                from("direct:updateReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=updateReplicationController");
                from("direct:deleteReplicationController").to(
                        "kubernetes-replication-controllers:///?kubernetesClient=#kubernetesClient&operation=deleteReplicationController");
            }
        };
    }
}
