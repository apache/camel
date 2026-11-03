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
package org.apache.camel.component.kubernetes.producer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The custom resources producer against the mock server in CRUD mode, with the custom resource definition and the
 * namespace in the endpoint options only.
 */
@EnableKubernetesMockClient(crud = true)
public class KubernetesCustomResourcesProducerCrudTest extends KubernetesTestSupport {

    private static final String CRD_OPTIONS = "&namespace=testnamespace&crdName=githubsources.sources.knative.dev"
                                              + "&crdGroup=sources.knative.dev&crdScope=Namespaced&crdVersion=v1alpha1"
                                              + "&crdPlural=githubsources";

    private static String githubSourceString;

    NamespacedKubernetesClient client;

    @BeforeAll
    public static void readResource() throws IOException {
        try (InputStream stream = KubernetesCustomResourcesProducerCrudTest.class.getResourceAsStream("sample-cr.json")) {
            githubSourceString = IOHelper.loadText(stream);
        }
    }

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return client;
    }

    @Test
    void crdOptionsOnly() {
        Exchange created = template.request("direct:create",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, githubSourceString));
        assertFalse(created.isFailed(), () -> "create failed: " + created.getException());
        assertEquals("samplecr", created.getMessage().getBody(GenericKubernetesResource.class).getMetadata().getName());

        Exchange listed = template.request("direct:list", exchange -> {
        });
        assertFalse(listed.isFailed(), () -> "list failed: " + listed.getException());
        assertEquals(1, listed.getMessage().getBody(List.class).size());

        Exchange got = template.request("direct:get",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "samplecr"));
        assertFalse(got.isFailed(), () -> "get failed: " + got.getException());
        JsonObject json = got.getMessage().getBody(JsonObject.class);
        assertEquals("https://api.github.com/", json.get("githubAPIURL"));

        Exchange deleted = template.request("direct:delete",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "samplecr"));
        assertFalse(deleted.isFailed(), () -> "delete failed: " + deleted.getException());
        assertTrue(deleted.getMessage().getHeader(KubernetesConstants.KUBERNETES_DELETE_RESULT, Boolean.class));
    }

    @Test
    void getMissingGivesEmptyJsonObject() {
        Exchange got = template.request("direct:getWithHeaders", exchange -> {
            setCrdHeaders(exchange);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "missing");
        });

        assertFalse(got.isFailed(), () -> "get failed: " + got.getException());
        assertTrue(got.getMessage().getBody(JsonObject.class).isEmpty());
    }

    @Test
    void deleteMissingGivesFalse() {
        Exchange deleted = template.request("direct:delete",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "missing"));

        assertFalse(deleted.isFailed(), () -> "delete failed: " + deleted.getException());
        assertEquals(Boolean.FALSE, deleted.getMessage().getHeader(KubernetesConstants.KUBERNETES_DELETE_RESULT));
    }

    @Test
    void createExistingFails() {
        GenericKubernetesResource resource = Serialization.unmarshal(githubSourceString, GenericKubernetesResource.class);
        resource.getMetadata().setName("existing");
        String instance = Serialization.asJson(resource);

        Exchange first = template.request("direct:createWithHeaders", exchange -> {
            setCrdHeaders(exchange);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, instance);
        });
        assertFalse(first.isFailed(), () -> "create failed: " + first.getException());

        Exchange second = template.request("direct:createWithHeaders", exchange -> {
            setCrdHeaders(exchange);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, instance);
        });
        assertTrue(second.isFailed());
        assertEquals(409, assertInstanceOf(KubernetesClientException.class, second.getException()).getCode());
    }

    @Test
    void listByLabelsWithoutLabelsFails() {
        Exchange listed = template.request("direct:listByLabelsWithHeaders", this::setCrdHeaders);

        assertTrue(listed.isFailed());
        assertInstanceOf(IllegalArgumentException.class, listed.getException());
    }

    @Test
    void listByLabels() {
        GenericKubernetesResource resource = Serialization.unmarshal(githubSourceString, GenericKubernetesResource.class);
        resource.getMetadata().setName("labelled");
        resource.getMetadata().setLabels(Map.of("app", "camel"));
        String instance = Serialization.asJson(resource);
        Exchange created = template.request("direct:create",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, instance));
        assertFalse(created.isFailed(), () -> "create failed: " + created.getException());

        Exchange listed = template.request("direct:listByLabels",
                exchange -> exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_LABELS, Map.of("app", "camel")));

        assertFalse(listed.isFailed(), () -> "list failed: " + listed.getException());
        assertEquals(1, listed.getMessage().getBody(List.class).size());
    }

    private void setCrdHeaders(Exchange exchange) {
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:create").to("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                         + "&operation=createCustomResource" + CRD_OPTIONS);
                from("direct:list").to("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                       + "&operation=listCustomResources" + CRD_OPTIONS);
                from("direct:listByLabels").to("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                               + "&operation=listCustomResourcesByLabels" + CRD_OPTIONS);
                from("direct:get").to("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                      + "&operation=getCustomResource" + CRD_OPTIONS);
                from("direct:createWithHeaders").to(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=createCustomResource");
                from("direct:getWithHeaders").to(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=getCustomResource");
                from("direct:listByLabelsWithHeaders").to(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                                          + "&operation=listCustomResourcesByLabels");
                from("direct:delete").to("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient"
                                         + "&operation=deleteCustomResource" + CRD_OPTIONS);
            }
        };
    }
}
