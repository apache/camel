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

import java.util.Collections;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watcher;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesCustomResourcesConsumerIT extends KubernetesTestSupport {

    private static final KubernetesClient CLIENT = new KubernetesClientBuilder().build();
    private static final String CRD_SOURCE_STRING = "{\n" +
                                                    "  \"apiVersion\": \"camel.apache.org/v1\",\n" +
                                                    "  \"kind\": \"CamelTest\",\n" +
                                                    "  \"metadata\": {\n" +
                                                    "    \"name\": \"camel-crd-itest\"\n" +
                                                    "  },\n" +
                                                    "  \"spec\": {\n" +
                                                    "    \"message\": \"Apache Camel Rocks!\"\n" +
                                                    "  }\n" +
                                                    "}";
    private static CustomResourceDefinition crd;

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @BeforeAll
    public static void beforeAll() {
        crd = new CustomResourceDefinitionBuilder()
                .withNewMetadata().withName("cameltests.camel.apache.org").endMetadata()
                .withNewSpec()
                .withGroup("camel.apache.org")
                .addAllToVersions(Collections.singletonList(new CustomResourceDefinitionVersionBuilder()
                        .withName("v1")
                        .withServed(true)
                        .withStorage(true)
                        .withNewSchema()
                        .withNewOpenAPIV3Schema()
                        .withType("object")
                        .addToProperties("spec", new JSONSchemaPropsBuilder()
                                .withType("object")
                                .addToProperties("message", new JSONSchemaPropsBuilder()
                                        .withType("string")
                                        .build())
                                .build())
                        .endOpenAPIV3Schema()
                        .endSchema()
                        .build()))
                .withScope("Namespaced")
                .withNewNames()
                .withPlural("cameltests")
                .withSingular("cameltest")
                .withShortNames("ct")
                .withKind("CamelTest")
                .endNames()
                .endSpec()
                .build();

        CLIENT.resource(crd).serverSideApply();
    }

    @AfterAll
    public static void afterAll() {
        if (crd != null) {
            CLIENT.resource(crd).delete();
        }
    }

    @Test
    @Order(1)
    void createCustomResource() throws Exception {
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_CRD_EVENT_ACTION,
                Watcher.Action.ADDED);
        Exchange ex = template.request("direct:createCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, CRD_SOURCE_STRING);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "camel-crd-itest");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "cameltests.camel.apache.org");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "camel.apache.org");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "cameltests");
        });

        mockResultEndpoint.assertIsSatisfied(5000);

        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());
    }

    @Test
    @Order(2)
    void deleteCustomResource() throws Exception {
        mockResultEndpoint.reset();
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_CRD_EVENT_ACTION,
                Watcher.Action.ADDED, Watcher.Action.DELETED);

        Exchange ex = template.request("direct:deleteCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "camel-crd-itest");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "cameltests.camel.apache.org");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "camel.apache.org");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "cameltests");
        });

        mockResultEndpoint.assertIsSatisfied(5000);

        assertNotNull(ex);
        assertNull(ex.getMessage().getBody());
        assertTrue(ex.getMessage().getHeader(KubernetesConstants.KUBERNETES_DELETE_RESULT, Boolean.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createCustomResource")
                        .toF("kubernetes-custom-resources://%s/?oauthToken=%s&operation=createCustomResource", host, authToken);
                from("direct:deleteCustomResource")
                        .toF("kubernetes-custom-resources://%s/?oauthToken=%s&operation=deleteCustomResource", host, authToken);
                fromF("kubernetes-custom-resources://%s/?oauthToken=%s&namespace=default" +
                      "&crdName=cameltests.camel.apache.org&crdGroup=camel.apache.org&crdScope=Namespaced&crdVersion=v1&crdPlural=cameltests",
                        host, authToken)
                        .process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            String json = exchange.getIn().getBody(String.class);

            log.info("Got event with custom resource instance: {} and action {}", json,
                    in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
