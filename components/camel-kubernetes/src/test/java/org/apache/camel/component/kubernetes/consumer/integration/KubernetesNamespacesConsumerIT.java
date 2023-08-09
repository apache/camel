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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.fabric8.kubernetes.api.model.Namespace;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesNamespacesConsumerIT extends KubernetesTestSupport {
    private static final String TEST_NAME_SPACE = "test" + ThreadLocalRandom.current().nextInt(1, 100);

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    @Order(1)
    void createPod() {
        mockResultEndpoint.expectedMessageCount(5);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "MODIFIED", "MODIFIED", "MODIFIED", "DELETED");

        Exchange ex = template.request("direct:createNamespace", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, TEST_NAME_SPACE);
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, labels);
        });

        Namespace ns = ex.getMessage().getBody(Namespace.class);

        assertNotNull(ns);
        assertEquals(TEST_NAME_SPACE, ns.getMetadata().getName());
    }

    @Test
    @Order(2)
    void listByLabels() {
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, labels);
        });

        boolean testNamespaceExists = false;

        for (Object o : ex.getMessage().getBody(List.class)) {
            Namespace namespace = (Namespace) o;
            if (TEST_NAME_SPACE.equalsIgnoreCase(namespace.getMetadata().getName())) {
                testNamespaceExists = true;
            }
        }

        assertTrue(testNamespaceExists);
    }

    @Test
    @Order(3)
    void deletePod() throws Exception {
        Exchange ex = template.request("direct:deleteNamespace",
                exchange -> exchange.getIn()
                        .setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, TEST_NAME_SPACE));

        Object body = ex.getMessage().getBody();
        assertNotNull(body);

        boolean nsDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(nsDeleted);

        mockResultEndpoint.assertIsSatisfied(5100);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listByLabels").toF("kubernetes-namespaces://%s?oauthToken=%s&operation=listNamespacesByLabels",
                        host, authToken);
                from("direct:createNamespace").toF("kubernetes-namespaces://%s?oauthToken=%s&operation=createNamespace", host,
                        authToken);
                from("direct:deleteNamespace").toF("kubernetes-namespaces://%s?oauthToken=%s&operation=deleteNamespace", host,
                        authToken);
                fromF("kubernetes-namespaces://%s?oauthToken=%s", host, authToken).process(new KubernetesProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            log.info("Got event with body: {} and action {}", in.getBody(),
                    in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
