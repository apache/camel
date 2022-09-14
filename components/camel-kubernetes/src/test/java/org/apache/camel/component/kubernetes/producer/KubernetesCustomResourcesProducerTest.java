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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.KubernetesServer;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KubernetesCustomResourcesProducerTest extends KubernetesTestSupport {
    private static String githubSourceString;

    @RegisterExtension
    public KubernetesServer server = new KubernetesServer();

    private CustomResourceDefinitionContext getCustomResourceContext() {
        return new CustomResourceDefinitionContext.Builder()
                .withName("githubsources.sources.knative.dev")
                .withGroup("sources.knative.dev")
                .withScope("Namespaced")
                .withVersion("v1alpha1")
                .withPlural("githubsources")
                .build();
    }

    @BeforeAll
    public static void readResource() throws IOException {
        try (InputStream stream = KubernetesCustomResourcesProducerTest.class.getResourceAsStream("sample-cr.json")) {
            githubSourceString = IOHelper.loadText(stream);
        }
    }

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() {
        return server.getClient();
    }

    private String setupGithubSourceList() throws Exception {
        GenericKubernetesResourceList list = new GenericKubernetesResourceList();
        list.getItems().add(Serialization.unmarshal(githubSourceString, GenericKubernetesResource.class));
        return Serialization.asJson(list);
    }

    @Test
    @Order(1)
    public void createTest() throws Exception {
        server.expect().post().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources")
                .andReturn(200, githubSourceString).once();
        server.expect().delete().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources/samplecr")
                .andReturn(200, githubSourceString).once();

        Exchange ex = template.request("direct:createCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "samplecr");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, githubSourceString);
        });

        assertFalse(ex.isFailed());
        assertNull(ex.getException());

        assertNotNull(ex.getMessage());
        assertNotNull(ex.getMessage().getBody());
    }

    @Test
    @Order(2)
    public void listTest() throws Exception {
        server.expect().get().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources")
                .andReturn(200, setupGithubSourceList()).once();

        Exchange ex = template.request("direct:listCustomResources", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        assertFalse(ex.isFailed());
        assertNull(ex.getException());

        List<Map<String, Object>> result = ex.getMessage().getBody(List.class);

        assertEquals(1, result.size());
    }

    @Test
    @Order(3)
    public void listByLabelsTest() throws Exception {
        server.expect().get()
                .withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources?labelSelector="
                          + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, setupGithubSourceList()).once();

        Exchange ex = template.request("direct:listCustomResourcesByLabels", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_LABELS, labels);
        });

        assertFalse(ex.isFailed());
        assertNull(ex.getException());

        List<Map<String, Object>> result = ex.getMessage().getBody(List.class);

        assertEquals(1, result.size());
    }

    @Test
    @Order(4)
    public void deleteTest() throws Exception {
        server.expect().post().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources")
                .andReturn(200, githubSourceString).once();
        server.expect().delete().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources/samplecr")
                .andReturn(200, githubSourceString).once();

        Exchange ex3 = template.request("direct:deleteCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "samplecr");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        assertNotNull(ex3.getMessage());
        assertTrue(ex3.getMessage().getHeader(KubernetesConstants.KUBERNETES_DELETE_RESULT, Boolean.class));
    }

    @Test
    @Order(5)
    public void testListNotFound() {
        server.expect().get().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/testnamespace/githubsources")
                .andReturn(404, "").once();
        Exchange ex4 = template.request("direct:listCustomResources", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "testnamespace");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        assertNotNull(ex4.getMessage());
        assertNull(ex4.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getCustomResource")
                        .toF("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=getCustomResource");
                from("direct:listCustomResources").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=listCustomResources");
                from("direct:listCustomResourcesByLabels").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=listCustomResourcesByLabels");
                from("direct:deleteCustomResource").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=deleteCustomResource");
                from("direct:createCustomResource").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=createCustomResource");
            }
        };
    }

}
