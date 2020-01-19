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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.PodSpec;
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
public class KubernetesNodesConsumerTest extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void createAndDeletePod() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "MODIFIED");
        Exchange ex = template.request("direct:createPod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "test");
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
        });

        ex = template.request("direct:deletePod", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_POD_NAME, "test");
        });

        boolean podDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(podDeleted);

        Thread.sleep(3000);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF("kubernetes-pods://%s?oauthToken=%s&operation=listPods", host, authToken);
                from("direct:listByLabels").toF("kubernetes-pods://%s?oauthToken=%s&operation=listPodsByLabels", host, authToken);
                from("direct:getPod").toF("kubernetes-pods://%s?oauthToken=%s&operation=getPod", host, authToken);
                from("direct:createPod").toF("kubernetes-pods://%s?oauthToken=%s&operation=createPod", host, authToken);
                from("direct:deletePod").toF("kubernetes-pods://%s?oauthToken=%s&operation=deletePod", host, authToken);
                fromF("kubernetes-nodes://%s?oauthToken=%s&resourceName=minikube", host, authToken).process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Node node = exchange.getIn().getBody(Node.class);
            log.info("Got event with node name: " + node.getMetadata().getName() + " and action " + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
