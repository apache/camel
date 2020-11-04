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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Requires a running Kubernetes Cluster")
public class KubernetesCustomResourcesConsumerTest extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

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

    @Test
    public void createAndDeleteCustomResource() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedMessageCount(2);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "MODIFIED");
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

        ex = template.request("direct:deleteCustomResource", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, "createtest");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_NAME, "githubsources.sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, "sources.knative.dev");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, "Namespaced");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, "v1alpha1");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, "githubsources");
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
                from("direct:createCustomResource")
                        .toF("kubernetes-custom-resources://%s/?oauthToken=%s&operation=createCustomResource", host, authToken);
                from("direct:deleteCustomResource")
                        .toF("kubernetes-custom-resources://%s/?oauthToken=%s&operation=deleteCustomResource", host, authToken);
                fromF("kubernetes-custom-resources://%s/?oauthToken=%s&namespace=test" +
                      "&crdName=githubsources.sources.knative.dev&crdGroup=sources.knative.dev&crdScope=Namespaced&crdVersion=v1alpha1&crdPlural=githubsources",
                        host, authToken)
                                .process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            String json = exchange.getIn().getBody(String.class);

            log.info("Got event with custom resource instance: " + json + " and action "
                     + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
