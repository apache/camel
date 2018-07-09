/**
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

import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerListBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesHPAProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("kubernetesClient", server.getClient());
        return registry;
    }

    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/apis/autoscaling/v1/namespaces/test/horizontalpodautoscalers")
            .andReturn(200, new HorizontalPodAutoscalerListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        List<HorizontalPodAutoscaler> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/apis/autoscaling/v1/namespaces/test/horizontalpodautoscalers?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new PodListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> labels = new HashMap<>();
                labels.put("key1", "value1");
                labels.put("key2", "value2");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, labels);
            }
        });

        List<HorizontalPodAutoscaler> result = ex.getOut().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void getHPATest() throws Exception {
        HorizontalPodAutoscaler hpa1 = new HorizontalPodAutoscalerBuilder().withNewMetadata().withName("hpa1").withNamespace("test").and().build();
        HorizontalPodAutoscaler hpa2 = new HorizontalPodAutoscalerBuilder().withNewMetadata().withName("hpa2").withNamespace("ns1").and().build();

        server.expect().withPath("/apis/autoscaling/v1/namespaces/test/horizontalpodautoscalers/hpa1").andReturn(200, hpa1).once();
        server.expect().withPath("/apis/autoscaling/v1/namespaces/ns1/horizontalpodautoscalers/hpa2").andReturn(200, hpa2).once();
        Exchange ex = template.request("direct:getHPA", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_HPA_NAME, "hpa1");
            }
        });

        HorizontalPodAutoscaler result = ex.getOut().getBody(HorizontalPodAutoscaler.class);

        assertEquals("hpa1", result.getMetadata().getName());
    }

    @Test
    public void deleteHPATest() throws Exception {
        HorizontalPodAutoscaler hpa1 = new HorizontalPodAutoscalerBuilder().withNewMetadata().withName("hpa1").withNamespace("test").and().build();
        server.expect().withPath("/apis/autoscaling/v1/namespaces/test/horizontalpodautoscalers/hpa1").andReturn(200, hpa1).once();

        Exchange ex = template.request("direct:deleteHPA", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_HPA_NAME, "hpa1");
            }
        });

        boolean podDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(podDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-hpa:///?kubernetesClient=#kubernetesClient&operation=listHPA");
                from("direct:listByLabels").to("kubernetes-hpa:///?kubernetesClient=#kubernetesClient&operation=listHPAByLabels");
                from("direct:getHPA").to("kubernetes-hpa:///?kubernetesClient=#kubernetesClient&operation=getHPA");
                from("direct:deleteHPA").to("kubernetes-hpa:///?kubernetesClient=#kubernetesClient&operation=deleteHPA");
            }
        };
    }
}
