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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
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
public class KubernetesPodsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/pods")
                .andReturn(200, new PodListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addNewItem().and().addNewItem().and().build()).once();
        List<?> result = template.requestBody("direct:list", "", List.class);
        assertEquals(3, result.size());

        Exchange ex = template.request("direct:list",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test"));
        List<?> resultNamespace = ex.getMessage().getBody(List.class);

        assertEquals(2, resultNamespace.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/pods?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new PodListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void getPodTest() {
        Pod pod1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").and().build();
        Pod pod2 = new PodBuilder().withNewMetadata().withName("pod2").withNamespace("ns1").and().build();

        server.expect().withPath("/api/v1/namespaces/test/pods/pod1").andReturn(200, pod1).once();
        server.expect().withPath("/api/v1/namespaces/ns1/pods/pod2").andReturn(200, pod2).once();
        Exchange ex = template.request("direct:getPod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "pod1");
        });

        Pod result = ex.getMessage().getBody(Pod.class);

        assertEquals("pod1", result.getMetadata().getName());
    }

    @Test
    void createPod() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        PodSpec spec = new PodSpecBuilder().withHostname("SomeHostname").build();
        Pod pod1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().post().withPath("/api/v1/namespaces/test/pods").andReturn(200, pod1).once();

        Exchange ex = template.request("direct:createPod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "pod1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_SPEC, spec);
        });

        Pod result = ex.getMessage().getBody(Pod.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("pod1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeHostname", result.getSpec().getHostname());
    }

    @Test
    void updatePod() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        PodSpec spec = new PodSpecBuilder().withHostname("SomeHostname").build();
        Pod pod1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().get().withPath("/api/v1/namespaces/test/pods/pod1")
                .andReturn(200, new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/api/v1/namespaces/test/pods/pod1").andReturn(200, pod1).once();

        Exchange ex = template.request("direct:updatePod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "pod1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_SPEC, spec);
        });

        Pod result = ex.getMessage().getBody(Pod.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("pod1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeHostname", result.getSpec().getHostname());
    }

    @Test
    void deletePod() {
        Pod pod1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").and().build();
        server.expect().withPath("/api/v1/namespaces/test/pods/pod1").andReturn(200, pod1).once();

        Exchange ex = template.request("direct:deletePod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "pod1");
        });

        boolean podDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(podDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=listPods");
                from("direct:listByLabels")
                        .to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=listPodsByLabels");
                from("direct:getPod").to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=getPod");
                from("direct:createPod").to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=createPod");
                from("direct:updatePod").to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=updatePod");
                from("direct:deletePod").to("kubernetes-pods:///?kubernetesClient=#kubernetesClient&operation=deletePod");
            }
        };
    }
}
