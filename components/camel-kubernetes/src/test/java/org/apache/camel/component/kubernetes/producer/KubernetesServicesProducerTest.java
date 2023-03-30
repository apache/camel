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

import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
public class KubernetesServicesProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/services")
                .andReturn(200, new ServiceListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/services?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new PodListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);
        assertEquals(3, result.size());
    }

    @Test
    void getServiceTest() {
        Service se1 = new ServiceBuilder().withNewMetadata().withName("se1").withNamespace("test").and().build();

        server.expect().withPath("/api/v1/namespaces/test/services/se1").andReturn(200, se1).once();
        Exchange ex = template.request("direct:getServices", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "se1");
        });

        Service result = ex.getMessage().getBody(Service.class);

        assertNotNull(result);
    }

    @Test
    void createService() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ServiceSpec spec = new ServiceSpecBuilder().withClusterIP("SomeClusterIp").build();
        Service se1 = new ServiceBuilder().withNewMetadata().withName("se1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().post().withPath("/api/v1/namespaces/test/services").andReturn(200, se1).once();

        Exchange ex = template.request("direct:createService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "se1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_SPEC, spec);
        });

        Service result = ex.getMessage().getBody(Service.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("se1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeClusterIp", result.getSpec().getClusterIP());
    }

    @Test
    void updateService() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ServiceSpec spec = new ServiceSpecBuilder().withExternalName("SomeExternalName").build();
        Service se1 = new ServiceBuilder().withNewMetadata().withName("se1").withNamespace("test").withLabels(labels).and()
                .withSpec(spec).build();
        server.expect().get().withPath("/api/v1/namespaces/test/services/se1")
                .andReturn(200, new ServiceBuilder().withNewMetadata().withName("se1").withNamespace("test").and()
                        .withSpec(new ServiceSpecBuilder().withClusterIP("SomeClusterIp").build()).build())
                .times(2);
        server.expect().put().withPath("/api/v1/namespaces/test/services/se1").andReturn(200, se1).once();

        Exchange ex = template.request("direct:updateService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "se1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_SPEC, spec);
        });

        Service result = ex.getMessage().getBody(Service.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("se1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals("SomeExternalName", result.getSpec().getExternalName());
    }

    @Test
    void deleteService() {
        Service se1 = new ServiceBuilder().withNewMetadata().withName("se1").withNamespace("test").and().build();

        server.expect().withPath("/api/v1/namespaces/test/services/se1").andReturn(200, se1).once();

        Exchange ex = template.request("direct:deleteService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, "se1");
        });

        boolean servDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(servDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=listServices");
                from("direct:listByLabels")
                        .to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=listServicesByLabels");
                from("direct:getServices")
                        .to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=getService");
                from("direct:createService")
                        .to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=createService");
                from("direct:updateService")
                        .to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=updateService");
                from("direct:deleteService")
                        .to("kubernetes-services:///?kubernetesClient=#kubernetesClient&operation=deleteService");
            }
        };
    }
}
