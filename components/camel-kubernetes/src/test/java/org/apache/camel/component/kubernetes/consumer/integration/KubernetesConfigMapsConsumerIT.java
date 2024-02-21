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
package org.apache.camel.component.kubernetes.consumer.integration;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesConfigMapsConsumerIT extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    public void configureMock() {
        mockResultEndpoint.expectedMessageCount(3);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "MODIFIED", "MODIFIED");
    }

    @BeforeEach
    public void waitForSettle() throws InterruptedException {
        Thread.sleep(1000);
    }

    @Test
    @Order(1)
    void createConfigMapWithProperties() {
        configureMock();

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

        Message message = ex.getMessage();

        assertNotNull(message);
        assertNotNull(message.getBody());
    }

    @Test
    @Order(2)
    void createConfigMap() {
        configureMock();

        Exchange ex = template.request("direct:createConfigmap", exchange -> {
            exchange.getIn().removeHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test1");
            HashMap<String, String> configMapData = new HashMap<>();
            configMapData.put("test", "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, configMapData);
        });

        Message message = ex.getMessage();

        assertNotNull(message);
        assertNotNull(message.getBody());
    }

    @Test
    @Order(3)
    void updateConfigMap() {
        configureMock();

        Map<String, String> configMapData = Map.of("test1", "test1");
        Exchange ex = template.request("direct:updateConfigmap", exchange -> {
            exchange.getIn().removeHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, "test1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, configMapData);
        });

        Message message = ex.getMessage();

        assertNotNull(message);
        ConfigMap result = ex.getMessage().getBody(ConfigMap.class);

        assertEquals("default", result.getMetadata().getNamespace());
        assertEquals("test1", result.getMetadata().getName());
        assertEquals(configMapData, result.getData());
    }

    @ParameterizedTest
    @Order(4)
    @ValueSource(strings = { "test", "test1" })
    void deleteConfigMaps(String configMapName) {
        Exchange ex = template.request("direct:deleteConfigmap", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, configMapName);
        });

        Message message = ex.getMessage();

        assertNotNull(message);

        // To avoid a NPE if unable to convert for any reason
        assertNotNull(message.getBody());
        boolean cmDeleted = message.getBody(Boolean.class);
        assertTrue(cmDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createConfigmap")
                        .toF("kubernetes-config-maps://%s?oauthToken=%s&operation=createConfigMap", host,
                                authToken);
                from("direct:updateConfigmap")
                        .toF("kubernetes-config-maps://%s?oauthToken=%s&operation=updateConfigMap", host,
                                authToken);

                from("direct:deleteConfigmap")
                        .toF("kubernetes-config-maps://%s?oauthToken=%s&operation=deleteConfigMap", host,
                                authToken);

                fromF("kubernetes-config-maps://%s?oauthToken=%s&operation=listConfigMaps", host, authToken)
                        .process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            ConfigMap cm = exchange.getIn().getBody(ConfigMap.class);

            log.info("Got event with configmap name: {} and action {}", cm.getMetadata().getName(),
                    in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
