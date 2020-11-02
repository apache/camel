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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext.Builder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.KubernetesServer;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubernetesCustomResourcesProducerTest extends KubernetesTestSupport {

    @RegisterExtension
    public KubernetesServer server = new KubernetesServer();

    private String gitHubSourceString = "{" +
                                        "\"apiVersion\": \"sources.knative.dev/v1alpha1\"," +
                                        "\"kind\": \"GitHubSource\"," +
                                        "\"metadata\": {" +
                                        "   \"name\": \"test\"}," +
                                        "\"spec\": {" +
                                        "   \"eventTypes\": [issues, repository]," +
                                        "   \"ownerAndRepository\": \"akihikokuroda/sample\"," +
                                        "   \"accessToken\": {" +
                                        "       \"secretKeyRef\": {" +
                                        "           \"name\": \"githubsecret\"," +
                                        "           \"key\": \"accessToken\"}}," +
                                        "   \"secretToken\": {" +
                                        "       \"secretKeyRef\": {" +
                                        "           \"name\": \"githubsecret\"," +
                                        "           \"key\": \"secretToken\"}}}," +
                                        "\"githubAPIURL\": \"https://api.github.com/\"," +
                                        "\"sink\": {" +
                                        "    \"ref\": {" +
                                        "       \"apiVersion\": \"messaging.knative.dev/v1beta1\"," +
                                        "       \"kind\": \"Channel\"," +
                                        "       \"name\": \"github\"}}" +
                                        "}";

    private CustomResourceDefinitionContext getCustomResourceContext() {
        return new CustomResourceDefinitionContext.Builder()
                .withName("githubsources.sources.knative.dev")
                .withGroup("sources.knative.dev")
                .withScope("Namespaced")
                .withVersion("v1alpha1")
                .withPlural("githubsources")
                .build();
    }

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() throws Exception {
        return server.getClient();
    }

    @Test
    public void listTest() throws Exception {
        JsonObject instance = new JsonObject(getClient().customResource(getCustomResourceContext()).load(gitHubSourceString));
        JsonObject gitHubSourceList = new JsonObject();
        JsonArray list = new JsonArray();
        list.add(instance);
        gitHubSourceList.put("items", list);

        server.expect().get().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/test/githubsources")
                .andReturn(200, gitHubSourceList.toJson()).once();

        Exchange ex = template.request("direct:listCustomResources", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        List<Map<String, Object>> result = ex.getMessage().getBody(List.class);

        assertTrue(1 == result.size());
    }

    @Test
    public void createAndDeleteTest() throws Exception {
        JsonObject instance = new JsonObject(getClient().customResource(getCustomResourceContext()).load(gitHubSourceString));
        JsonObject gitHubSourceList = new JsonObject();
        JsonArray list = new JsonArray();
        list.add(instance);
        gitHubSourceList.put("items", list);

        server.expect().post().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/test/githubsources")
                .andReturn(200, gitHubSourceList.toJson()).once();
        server.expect().delete().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/test/githubsources/createtest")
                .andReturn(200, gitHubSourceList.toJson()).once();

        Exchange ex = template.request("direct:createCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "createtest");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, gitHubSourceString);
        });

        server.expect().get().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/test/githubsources")
                .andReturn(200, gitHubSourceList.toJson()).once();
        Exchange ex2 = template.request("direct:listCustomResources", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });
        List<Map<String, Object>> result = ex2.getMessage().getBody(List.class);

        Exchange ex3 = template.request("direct:deleteCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "createtest");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        server.expect().get().withPath("/apis/sources.knative.dev/v1alpha1/namespaces/test/githubsources")
                .andReturn(200, "").once();
        Exchange ex4 = template.request("direct:listCustomResources", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
        });

        List<Map<String, Object>> result1 = ex4.getMessage().getBody(List.class);

        assertTrue(result1.size() == 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:getCustomResource")
                        .toF("kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=getCustomResource");
                from("direct:listCustomResources").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=listCustomResources");
                from("direct:deleteCustomResource").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=deleteCustomResource");
                from("direct:createCustomResource").toF(
                        "kubernetes-custom-resources:///?kubernetesClient=#kubernetesClient&operation=createCustomResource");
            }
        };
    }

}
