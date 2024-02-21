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

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuotaListBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuotaSpec;
import io.fabric8.kubernetes.api.model.ResourceQuotaSpecBuilder;
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
public class KubernetesResourcesQuotaProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void listTest() {
        server.expect().withPath("/api/v1/resourcequotas")
                .andReturn(200, new ResourceQuotaListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
                .once();
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    void createResourceQuota() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ResourceQuotaSpec spec = new ResourceQuotaSpecBuilder().withScopes("SomeScope").build();
        ResourceQuota rq1
                = new ResourceQuotaBuilder().withNewMetadata().withName("rq1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().post().withPath("/api/v1/namespaces/test/resourcequotas").andReturn(200, rq1).once();

        Exchange ex = template.request("direct:create", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, "rq1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCE_QUOTA_SPEC, spec);
        });

        ResourceQuota result = ex.getMessage().getBody(ResourceQuota.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("rq1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(List.of("SomeScope"), result.getSpec().getScopes());
    }

    @Test
    void updateResourceQuota() {
        Map<String, String> labels = Map.of("my.label.key", "my.label.value");
        ResourceQuotaSpec spec = new ResourceQuotaSpecBuilder().withScopes("SomeScope").build();
        ResourceQuota rq1
                = new ResourceQuotaBuilder().withNewMetadata().withName("rq1").withNamespace("test").withLabels(labels).and()
                        .withSpec(spec).build();
        server.expect().get().withPath("/api/v1/namespaces/test/resourcequotas/rq1").andReturn(200,
                new ResourceQuotaBuilder().withNewMetadata().withName("rq1").withNamespace("test").endMetadata().build())
                .once();
        server.expect().put().withPath("/api/v1/namespaces/test/resourcequotas/rq1").andReturn(200, rq1).once();

        Exchange ex = template.request("direct:update", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_LABELS, labels);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, "rq1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCE_QUOTA_SPEC, spec);
        });

        ResourceQuota result = ex.getMessage().getBody(ResourceQuota.class);

        assertEquals("test", result.getMetadata().getNamespace());
        assertEquals("rq1", result.getMetadata().getName());
        assertEquals(labels, result.getMetadata().getLabels());
        assertEquals(List.of("SomeScope"), result.getSpec().getScopes());
    }

    @Test
    void deleteResourceQuota() {
        ResourceQuota rq1 = new ResourceQuotaBuilder().withNewMetadata().withName("rq1").withNamespace("test").and().build();
        server.expect().withPath("/api/v1/namespaces/test/resourcequotas/rq1").andReturn(200, rq1).once();

        Exchange ex = template.request("direct:delete", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, "rq1");
        });

        boolean rqDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(rqDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list")
                        .to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=listResourcesQuota");
                from("direct:create")
                        .to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=createResourceQuota");
                from("direct:update")
                        .to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=updateResourceQuota");
                from("direct:delete")
                        .to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=deleteResourceQuota");
            }
        };
    }
}
