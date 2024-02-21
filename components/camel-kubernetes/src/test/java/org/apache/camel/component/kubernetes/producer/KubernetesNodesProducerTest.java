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

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.NodeSpecBuilder;
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
public class KubernetesNodesProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/nodes").andReturn(200, new NodeListBuilder().addNewItem().and().build()).once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(1, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/nodes?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new NodeListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void createNodeTest() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("test");
        server.expect().withPath("/api/v1/nodes").andReturn(200, new NodeBuilder().withMetadata(meta).build()).once();
        Exchange ex = template.request("direct:createNode", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_NAME, "test");
            NodeSpec spec = new NodeSpecBuilder().build();
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_SPEC, spec);
        });

        Node result = ex.getMessage().getBody(Node.class);

        assertEquals("test", result.getMetadata().getName());
    }

    @Test
    void updateNodeTest() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("test");
        server.expect().get().withPath("/api/v1/nodes/test").andReturn(200, new NodeBuilder().build()).once();
        server.expect().put().withPath("/api/v1/nodes/test").andReturn(200, new NodeBuilder().withMetadata(meta).build())
                .once();
        Exchange ex = template.request("direct:updateNode", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODES_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_NAME, "test");
            NodeSpec spec = new NodeSpecBuilder().build();
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_SPEC, spec);
        });

        Node result = ex.getMessage().getBody(Node.class);

        assertEquals("test", result.getMetadata().getName());
    }

    @Test
    void deleteNode() {
        Node node1 = new NodeBuilder().withNewMetadata().withName("node1").withNamespace("test").and().build();
        server.expect().withPath("/api/v1/nodes/node1").andReturn(200, node1).once();

        Exchange ex = template.request("direct:deleteNode",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NODE_NAME, "node1"));

        boolean nodeDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(nodeDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").toF("kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=listNodes");
                from("direct:listByLabels")
                        .toF("kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=listNodesByLabels");
                from("direct:createNode").toF("kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=createNode");
                from("direct:updateNode").toF("kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=updateNode");
                from("direct:deleteNode").toF("kubernetes-nodes:///?kubernetesClient=#kubernetesClient&operation=deleteNode");
            }
        };
    }
}
