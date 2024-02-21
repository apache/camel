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

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesReplicationControllersConsumerIT extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @BeforeEach
    public void waitForSettle() throws InterruptedException {
        Thread.sleep(1000);
    }

    @Test
    @Order(1)
    void createReplicationController() throws Exception {
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "MODIFIED", "MODIFIED");

        Exchange ex = template.request("direct:createReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "test");
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, labels);
            ReplicationControllerSpec rcSpec = new ReplicationControllerSpec();
            rcSpec.setReplicas(2);
            PodTemplateSpecBuilder builder = new PodTemplateSpecBuilder();
            PodTemplateSpec t = builder.withNewMetadata().withName("nginx-template").addToLabels("server", "nginx")
                    .endMetadata().withNewSpec().addNewContainer()
                    .withName("wildfly").withImage("jboss/wildfly").addNewPort().withContainerPort(80).endPort().endContainer()
                    .endSpec().build();
            rcSpec.setTemplate(t);
            Map<String, String> selectorMap = new HashMap<>();
            selectorMap.put("server", "nginx");
            rcSpec.setSelector(selectorMap);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC, rcSpec);
        });

        ReplicationController rc = ex.getMessage().getBody(ReplicationController.class);

        assertEquals("test", rc.getMetadata().getName());

        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    @Order(2)
    void deleteReplicationController() throws Exception {
        Exchange ex = template.request("direct:deleteReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "test");
        });

        boolean rcDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(rcDeleted);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createReplicationController").toF(
                        "kubernetes-replication-controllers://%s?oauthToken=%s&operation=createReplicationController", host,
                        authToken);
                from("direct:deleteReplicationController").toF(
                        "kubernetes-replication-controllers://%s?oauthToken=%s&operation=deleteReplicationController", host,
                        authToken);
                fromF("kubernetes-replication-controllers://%s?oauthToken=%s&resourceName=wildfly", host, authToken)
                        .process(new KubernetesProcessor()).to(mockResultEndpoint);
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
