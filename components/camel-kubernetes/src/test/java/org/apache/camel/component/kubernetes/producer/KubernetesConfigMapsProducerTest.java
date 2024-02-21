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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
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
public class KubernetesConfigMapsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/configmaps")
                .andReturn(200, new ConfigMapListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);
        assertEquals(3, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/configmaps?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new ConfigMapListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        Exchange ex = template.request("direct:listConfigMapsByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    void getConfigMapTestDefaultNamespace() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("cm1");
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm1")
                .andReturn(200, new ConfigMapBuilder().withMetadata(meta).build()).once();
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm2").andReturn(200, new ConfigMapBuilder().build())
                .once();
        Exchange ex = template.request("direct:getConfigMap",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1"));

        ConfigMap result = ex.getMessage().getBody(ConfigMap.class);

        assertEquals("cm1", result.getMetadata().getName());
    }

    @Test
    void getConfigMapTestCustomNamespace() {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("cm1");
        server.expect().withPath("/api/v1/namespaces/custom/configmaps/cm1")
                .andReturn(200, new ConfigMapBuilder().withMetadata(meta).build()).once();
        server.expect().withPath("/api/v1/namespaces/custom/configmaps/cm2").andReturn(200, new ConfigMapBuilder().build())
                .once();
        Exchange ex = template.request("direct:getConfigMap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "custom");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
        });

        ConfigMap result = ex.getMessage().getBody(ConfigMap.class);

        assertEquals("cm1", result.getMetadata().getName());
    }

    @Test
    void createConfigMap() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        Map<String, String> data = Map.of("my.data.key", "my.data.value");
        ConfigMap cm1 = new ConfigMapBuilder().withNewMetadata().withName("cm1").withNamespace("test").withLabels(labels).and()
                .withData(data).build();
        server.expect().post().withPath("/api/v1/namespaces/test/configmaps").andReturn(200, cm1).once();

        Exchange ex = template.request("direct:createConfigMap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, data);
        });

        ConfigMap result = ex.getMessage().getBody(ConfigMap.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("cm1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void updateConfigMap() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        Map<String, String> data = Map.of("my.data.key", "my.data.value");
        ConfigMap cm1 = new ConfigMapBuilder().withNewMetadata().withName("cm1").withNamespace("test").withLabels(labels).and()
                .withData(data).build();
        server.expect().get().withPath("/api/v1/namespaces/test/configmaps/cm1")
                .andReturn(200,
                        new ConfigMapBuilder().withNewMetadata().withName("cm1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/api/v1/namespaces/test/configmaps/cm1").andReturn(200, cm1).once();

        Exchange ex = template.request("direct:updateConfigMap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, data);
        });

        ConfigMap result = ex.getMessage().getBody(ConfigMap.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("cm1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
    }

    @Test
    void deleteConfigMap() {
        ConfigMap cm1 = new ConfigMapBuilder().withNewMetadata().withName("cm1").withNamespace("test").and().build();
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm1").andReturn(200, cm1).once();

        Exchange ex = template.request("direct:deleteConfigMap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
        });

        boolean configMapDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(configMapDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=listConfigMaps");
                from("direct:listConfigMapsByLabels")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=listConfigMapsByLabels");
                from("direct:getConfigMap")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=getConfigMap");
                from("direct:createConfigMap")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=createConfigMap");
                from("direct:updateConfigMap")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=updateConfigMap");
                from("direct:deleteConfigMap")
                        .to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=deleteConfigMap");
            }
        };
    }
}
