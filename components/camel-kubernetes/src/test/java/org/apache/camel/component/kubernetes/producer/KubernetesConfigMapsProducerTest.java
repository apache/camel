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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesConfigMapsProducerTest extends KubernetesTestSupport {

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
        server.expect().withPath("/api/v1/configmaps").andReturn(200, new ConfigMapListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        List<ConfigMap> result = template.requestBody("direct:list", "", List.class);
        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/configmaps?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new ConfigMapListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listConfigMapsByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("key1", "value1");
                labels.put("key2", "value2");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
            }
        });

        List<ConfigMap> result = ex.getOut().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void getConfigMapTest() throws Exception {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("cm1");
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm1").andReturn(200, new ConfigMapBuilder().withMetadata(meta).build()).once();
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm2").andReturn(200, new ConfigMapBuilder().build()).once();
        Exchange ex = template.request("direct:getConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
            }
        });

        ConfigMap result = ex.getOut().getBody(ConfigMap.class);

        assertEquals("cm1", result.getMetadata().getName());
    }

    @Test
    public void createGetAndDeleteConfigMap() throws Exception {
        ConfigMap cm1 = new ConfigMapBuilder().withNewMetadata().withName("cm1").withNamespace("test").and().build();
        server.expect().withPath("/api/v1/namespaces/test/configmaps/cm1").andReturn(200, cm1).once();

        Exchange ex = template.request("direct:deleteConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "cm1");
            }
        });

        boolean configMapDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(configMapDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=listConfigMaps");
                from("direct:listConfigMapsByLabels").to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=listConfigMapsByLabels");
                from("direct:getConfigMap").to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=getConfigMap");
                from("direct:createConfigMap").to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=createConfigMap");
                from("direct:deleteConfigMap").to("kubernetes-config-maps:///?kubernetesClient=#kubernetesClient&operation=deleteConfigMap");
            }
        };
    }
}
