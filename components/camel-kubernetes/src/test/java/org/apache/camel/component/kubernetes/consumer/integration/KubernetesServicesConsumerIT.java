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

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesServicesConsumerIT extends KubernetesTestSupport {
    private static final String TEST_SERVICE_NAME = "test" + ThreadLocalRandom.current().nextInt(1, 100);

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    @Order(1)
    void createService() {
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED");

        Exchange ex = template.request("direct:createService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, TEST_SERVICE_NAME);
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
            ServiceSpec serviceSpec = new ServiceSpec();
            List<ServicePort> lsp = new ArrayList<>();
            ServicePort sp = new ServicePort();
            sp.setPort(8080);
            sp.setTargetPort(new IntOrString(8080));
            sp.setProtocol("TCP");
            lsp.add(sp);
            serviceSpec.setPorts(lsp);
            Map<String, String> selectorMap = new HashMap<>();
            selectorMap.put("containter", "test");
            serviceSpec.setSelector(selectorMap);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_SPEC, serviceSpec);
        });

        assertFalse(ex.isFailed());
        assertNull(ex.getException());

        Service serv = ex.getMessage().getBody(Service.class);

        assertEquals(TEST_SERVICE_NAME, serv.getMetadata().getName());
    }

    @Test
    @Order(2)
    void deleteService() {
        Exchange ex = template.request("direct:deleteService", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, TEST_SERVICE_NAME);
        });

        assertFalse(ex.isFailed());
        assertNull(ex.getException());

        boolean servDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(servDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createService").toF("kubernetes-services://%s?oauthToken=%s&operation=createService", host,
                        authToken);
                from("direct:deleteService").toF("kubernetes-services://%s?oauthToken=%s&operation=deleteService", host,
                        authToken);
                fromF("kubernetes-services://%s?oauthToken=%s&labelKey=this&labelValue=rocks", host, authToken)
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
