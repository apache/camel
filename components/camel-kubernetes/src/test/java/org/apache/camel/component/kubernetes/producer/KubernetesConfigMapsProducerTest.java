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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class KubernetesConfigMapsProducerTest extends KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<ConfigMap> result = template.requestBody("direct:list", "", List.class);
        assertEquals(1, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:listConfigMapsByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("component", "elasticsearch");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
            }
        });

        List<ConfigMap> result = ex.getOut().getBody(List.class);

        boolean configMapExists = false;
        Iterator<ConfigMap> it = result.iterator();
        while (it.hasNext()) {
            ConfigMap cfMap = it.next();
            if (cfMap.getMetadata().getLabels().containsValue("elasticsearch")) {
                configMapExists = true;
            }
        }

        assertFalse(configMapExists);
    }

    @Test
    public void getConfigMapTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:getConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAP_NAME,
                        "elasticsearch-7015o");
            }
        });

        ConfigMap result = ex.getOut().getBody(ConfigMap.class);

        assertNull(result);
    }

    @Test
    public void createGetAndDeleteConfigMap() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:createConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);

                Map<String, String> data = new HashMap<String, String>();
                
                data.put("test", "test1");
                data.put("test1", "test2");

                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, data);
            }
        });
        
        ex = template.request("direct:getConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAP_NAME,
                        "test");
            }
        });

        ConfigMap result = ex.getOut().getBody(ConfigMap.class);
        
        assertNotNull(result);
        assertTrue(result.getData().containsKey("test"));
        assertTrue(result.getData().containsKey("test1"));
        assertEquals("test1", result.getData().get("test"));
        assertEquals("test2", result.getData().get("test1"));

        ex = template.request("direct:deleteConfigMap", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test");
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
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=configMaps&operation=listConfigMaps",
                                host, authToken);
                from("direct:listConfigMapsByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=configMaps&operation=listConfigMapsByLabels",
                                host, authToken);
                from("direct:getConfigMap")
                        .toF("kubernetes://%s?oauthToken=%s&category=configMaps&operation=getConfigMap",
                                host, authToken);
                from("direct:createConfigMap")
                        .toF("kubernetes://%s?oauthToken=%s&category=configMaps&operation=createConfigMap",
                                host, authToken);
                from("direct:deleteConfigMap")
                        .toF("kubernetes://%s?oauthToken=%s&category=configMaps&operation=deleteConfigMap",
                                host, authToken);
            }
        };
    }
}
