/**
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Namespace;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Requires a running Kubernetes Cluster")
public class KubernetesNamespacesConsumerTest extends KubernetesTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void createAndDeletePod() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedMessageCount(5);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED",
                "MODIFIED", "MODIFIED", "MODIFIED", "DELETED");
        
        Exchange ex = template.request("direct:createNamespace",
                new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader(
                                KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                                "test");
                        Map<String, String> labels = new HashMap<String, String>();
                        labels.put("this", "rocks");
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_NAMESPACE_LABELS,
                                        labels);
                    }
                });

        Namespace ns = ex.getOut().getBody(Namespace.class);

        assertEquals(ns.getMetadata().getName(), "test");

        ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_NAMESPACE_LABELS,
                                labels);
            }
        });

        List<Namespace> result = ex.getOut().getBody(List.class);

        boolean testExists = false;

        Iterator<Namespace> it = result.iterator();
        while (it.hasNext()) {
            Namespace namespace = it.next();
            if ("test".equalsIgnoreCase(namespace.getMetadata().getName())) {
                testExists = true;
            }
        }

        assertTrue(testExists);

        ex = template.request("direct:deleteNamespace", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
            }
        });

        boolean nsDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(nsDeleted);
        
        Thread.sleep(3000);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF("kubernetes-namespaces://%s?oauthToken=%s&operation=listNamespaces",
                        host, authToken);
                from("direct:listByLabels").toF(
                        "kubernetes-namespaces://%s?oauthToken=%s&operation=listNamespacesByLabels", host,
                        authToken);
                from("direct:getNs").toF("kubernetes-namespaces://%s?oauthToken=%s&operation=getNamespace",
                        host, authToken);
                from("direct:createNamespace").toF(
                        "kubernetes-namespaces://%s?oauthToken=%s&operation=createNamespace", host, authToken);
                from("direct:deleteNamespace").toF(
                        "kubernetes-namespaces://%s?oauthToken=%s&operation=deleteNamespace", host, authToken);
                fromF("kubernetes-namespaces://%s?oauthToken=%s", host, authToken).process(
                        new KubernertesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernertesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            log.info("Got event with body: " + in.getBody() + " and action "
                    + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
