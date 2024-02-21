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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimListBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpecBuilder;
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
public class KubernetesPersistentVolumesClaimsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/namespaces/test/persistentvolumeclaims")
                .andReturn(200,
                        new PersistentVolumeClaimListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect()
                .withPath("/api/v1/namespaces/test/persistentvolumeclaims?labelSelector="
                          + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200,
                        new PersistentVolumeClaimListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);
        assertEquals(3, result.size());
    }

    @Test
    void createPersistentVolumeClaim() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        PersistentVolumeClaimSpec spec = new PersistentVolumeClaimSpecBuilder().withVolumeName("SomeVolumeName").build();
        PersistentVolumeClaim vc1 = new PersistentVolumeClaimBuilder().withNewMetadata().withName("vc1").withNamespace("test")
                .withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().post().withPath("/api/v1/namespaces/test/persistentvolumeclaims").andReturn(200, vc1).once();

        Exchange ex = template.request("direct:create", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, "vc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC, spec);
        });

        PersistentVolumeClaim result = ex.getMessage().getBody(PersistentVolumeClaim.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("vc1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeVolumeName", result.getSpec().getVolumeName());
    }

    @Test
    void updatePersistentVolumeClaim() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        PersistentVolumeClaimSpec spec = new PersistentVolumeClaimSpecBuilder().withVolumeName("SomeVolumeName").build();
        PersistentVolumeClaim vc1 = new PersistentVolumeClaimBuilder().withNewMetadata().withName("vc1").withNamespace("test")
                .withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().get().withPath("/api/v1/namespaces/test/persistentvolumeclaims/vc1")
                .andReturn(200, new PersistentVolumeClaimBuilder().withNewMetadata().withName("vc1").withNamespace("test")
                        .endMetadata().build())
                .once();
        server.expect().put().withPath("/api/v1/namespaces/test/persistentvolumeclaims/vc1").andReturn(200, vc1).once();

        Exchange ex = template.request("direct:update", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, "vc1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC, spec);
        });

        PersistentVolumeClaim result = ex.getMessage().getBody(PersistentVolumeClaim.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("vc1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeVolumeName", result.getSpec().getVolumeName());
    }

    @Test
    void deletePersistentVolumeClaim() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("pvc1");
        server.expect().withPath("/api/v1/namespaces/test/persistentvolumeclaims/pvc1")
                .andReturn(200, new PersistentVolumeClaimBuilder().withMetadata(meta).build()).once();
        Exchange ex = template.request("direct:delete", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, "pvc1");
        });

        boolean pvcDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(pvcDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to(
                        "kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumesClaims");
                from("direct:listByLabels").to(
                        "kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumesClaimsByLabels");
                from("direct:create").to(
                        "kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=createPersistentVolumeClaim");
                from("direct:update").to(
                        "kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=updatePersistentVolumeClaim");
                from("direct:delete").to(
                        "kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=deletePersistentVolumeClaim");
            }
        };
    }
}
