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
package org.apache.camel.component.kubernetes.consumer;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Requires a running Kubernetes Cluster")
public class KubernetesConfigMapsConsumerTest extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void createAndDeleteConfigMap() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedMessageCount(3);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED", "MODIFIED", "MODIFIED");
        Exchange ex = template.request("direct:createConfigmap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test");
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, labels);
            HashMap<String, String> configMapData = new HashMap<>();
            configMapData.put("test", "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, configMapData);
        });

        ex = template.request("direct:createConfigmap", exchange -> {
            exchange.getIn().removeHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test1");
            HashMap<String, String> configMapData = new HashMap<>();
            configMapData.put("test", "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, configMapData);
        });

        ex = template.request("direct:deleteConfigmap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test");
        });

        ex = template.request("direct:deleteConfigmap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test1");
        });

        boolean cmDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(cmDeleted);

        Thread.sleep(3000);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createConfigmap").toF("kubernetes-config-maps://%s?oauthToken=%s&operation=createConfigMap", host, authToken);
                from("direct:deleteConfigmap").toF("kubernetes-config-maps://%s?oauthToken=%s&operation=deleteConfigMap", host, authToken);
                fromF("kubernetes-config-maps://%s?oauthToken=%s&namespace=default&resourceName=test", host, authToken).process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            ConfigMap cm = exchange.getIn().getBody(ConfigMap.class);
            log.info("Got event with configmap name: " + cm.getMetadata().getName() + " and action " + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
