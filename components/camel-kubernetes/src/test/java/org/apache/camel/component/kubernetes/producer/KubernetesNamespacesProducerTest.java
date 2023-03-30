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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
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
public class KubernetesNamespacesProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/namespaces")
                .andReturn(200, new NamespaceListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);
        assertEquals(3, result.size());
    }

    @Test
    void getNamespace() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("test");
        server.expect().withPath("/api/v1/namespaces/test").andReturn(200, new NamespaceBuilder().withMetadata(meta).build())
                .once();
        Exchange ex = template.request("direct:getNs",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test"));

        Namespace ns = ex.getMessage().getBody(Namespace.class);

        assertEquals("test", ns.getMetadata().getName());

    }

    @Test
    void createNamespace() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        Namespace ns1 = new NamespaceBuilder().withNewMetadata().withName("ns1").withLabels(labels).endMetadata().build();
        server.expect().post().withPath("/api/v1/namespaces").andReturn(200, ns1).once();

        Exchange ex = template.request("direct:createNamespace", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "ns1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, labels);
        });

        Namespace result = ex.getMessage().getBody(Namespace.class);

        assertEquals("ns1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void updateNamespace() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        Namespace ns1 = new NamespaceBuilder().withNewMetadata().withName("ns1").withLabels(labels).endMetadata().build();
        server.expect().get().withPath("/api/v1/namespaces/ns1")
                .andReturn(200, new NamespaceBuilder().withNewMetadata().withName("ns1").endMetadata().build()).once();
        server.expect().put().withPath("/api/v1/namespaces/ns1").andReturn(200, ns1).once();

        Exchange ex = template.request("direct:updateNamespace", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "ns1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, labels);
        });

        Namespace result = ex.getMessage().getBody(Namespace.class);

        assertEquals("ns1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void deleteNamespace() {
        Namespace ns1 = new NamespaceBuilder().withNewMetadata().withName("ns1").endMetadata().build();
        server.expect().withPath("/api/v1/namespaces/ns1").andReturn(200, ns1).once();

        Exchange ex = template.request("direct:deleteNamespace",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "ns1"));

        boolean nsDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(nsDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to("kubernetes-namespaces:///?kubernetesClient=#kubernetesClient&operation=listNamespaces");
                from("direct:getNs").to("kubernetes-namespaces:///?kubernetesClient=#kubernetesClient&operation=getNamespace");
                from("direct:createNamespace")
                        .to("kubernetes-namespaces:///?kubernetesClient=#kubernetesClient&operation=createNamespace");
                from("direct:updateNamespace")
                        .to("kubernetes-namespaces:///?kubernetesClient=#kubernetesClient&operation=updateNamespace");
                from("direct:deleteNamespace")
                        .to("kubernetes-namespaces:///?kubernetesClient=#kubernetesClient&operation=deleteNamespace");
            }
        };
    }
}
