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

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuotaListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesResourcesQuotaProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() throws Exception {
        return server.getClient();
    }

    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/api/v1/resourcequotas").andReturn(200, new ResourceQuotaListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        List<ResourceQuota> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void deleteResourceQuota() throws Exception {
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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=listResourcesQuota");
                from("direct:delete").to("kubernetes-resources-quota:///?kubernetesClient=#kubernetesClient&operation=deleteResourceQuota");
            }
        };
    }
}
