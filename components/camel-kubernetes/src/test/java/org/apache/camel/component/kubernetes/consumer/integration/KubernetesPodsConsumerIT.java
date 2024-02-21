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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesPodsConsumerIT extends KubernetesTestSupport {
    private static final String TEST_POD_NAME = "test" + ThreadLocalRandom.current().nextInt(1, 100);

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    private void setupPod(Exchange exchange) {
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, TEST_POD_NAME);
        Map<String, String> labels = new HashMap<>();
        labels.put("this", "rocks");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PODS_LABELS, labels);
        PodSpec podSpec = new PodSpec();
        podSpec.setHostname("localhost");
        Container cont = new Container();
        cont.setImage("docker.io/jboss/wildfly:latest");
        cont.setName("pippo");

        List<ContainerPort> containerPort = new ArrayList<>();
        ContainerPort port = new ContainerPort();
        port.setHostIP("0.0.0.0");
        port.setHostPort(8080);
        port.setContainerPort(8080);

        containerPort.add(port);

        cont.setPorts(containerPort);

        List<Container> list = new ArrayList<>();
        list.add(cont);

        podSpec.setContainers(list);

        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_SPEC, podSpec);
    }

    @Test
    @Order(1)
    void createPod() throws Exception {
        mockResultEndpoint.expectedMessageCount(2);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "ADDED", "ADDED");
        Exchange ex = template.request("direct:createPod", this::setupPod);

        assertNotNull(ex);
        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());

        mockResultEndpoint.assertIsSatisfied();
    }

    @Test
    @Order(2)
    void deletePod() throws Exception {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION,
                "ADDED", "ADDED", "ADDED");
        Exchange ex = template.request("direct:deletePod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, TEST_POD_NAME);
        });

        boolean podDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(podDeleted);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createPod").toF("kubernetes-pods://%s?oauthToken=%s&operation=createPod", host, authToken);
                from("direct:deletePod").toF("kubernetes-pods://%s?oauthToken=%s&operation=deletePod", host, authToken);
                fromF("kubernetes-pods://%s?oauthToken=%s&namespace=default&labelKey=this&labelValue=rocks", host, authToken)
                        .process(new KubernetesProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            Pod pod = exchange.getIn().getBody(Pod.class);
            log.info("Got event with pod name: {} and action {}", pod.getMetadata().getName(),
                    in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
