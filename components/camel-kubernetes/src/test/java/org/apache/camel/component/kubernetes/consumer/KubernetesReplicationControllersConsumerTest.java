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
import org.apache.camel.util.ObjectHelper;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Requires a running Kubernetes Cluster")
public class KubernetesReplicationControllersConsumerTest extends KubernetesTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void createAndDeleteReplicationController() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }

        mockResultEndpoint.expectedHeaderValuesReceivedInAnyOrder(KubernetesConstants.KUBERNETES_EVENT_ACTION, "ADDED", "DELETED", "MODIFIED", "MODIFIED", "MODIFIED");
        Exchange ex = template.request("direct:createReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "test");
            Map<String, String> labels = new HashMap<>();
            labels.put("this", "rocks");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS, labels);
            ReplicationControllerSpec rcSpec = new ReplicationControllerSpec();
            rcSpec.setReplicas(2);
            PodTemplateSpecBuilder builder = new PodTemplateSpecBuilder();
            PodTemplateSpec t = builder.withNewMetadata().withName("nginx-template").addToLabels("server", "nginx").endMetadata().withNewSpec().addNewContainer()
                .withName("wildfly").withImage("jboss/wildfly").addNewPort().withContainerPort(80).endPort().endContainer().endSpec().build();
            rcSpec.setTemplate(t);
            Map<String, String> selectorMap = new HashMap<>();
            selectorMap.put("server", "nginx");
            rcSpec.setSelector(selectorMap);
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC, rcSpec);
        });

        ReplicationController rc = ex.getMessage().getBody(ReplicationController.class);

        assertEquals(rc.getMetadata().getName(), "test");

        ex = template.request("direct:deleteReplicationController", exchange -> {
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "default");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME, "test");
        });

        boolean rcDeleted = ex.getMessage().getBody(Boolean.class);

        assertTrue(rcDeleted);

        Thread.sleep(3000);

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF("kubernetes-replication-controllers://%s?oauthToken=%s&operation=listReplicationControllers", host, authToken);
                from("direct:listByLabels").toF("kubernetes-replication-controllers://%s?oauthToken=%s&operation=listReplicationControllersByLabels", host, authToken);
                from("direct:getReplicationController").toF("kubernetes-replication-controllers://%s?oauthToken=%s&operation=getReplicationController", host, authToken);
                from("direct:createReplicationController").toF("kubernetes-replication-controllers://%s?oauthToken=%s&operation=createReplicationController", host, authToken);
                from("direct:deleteReplicationController").toF("kubernetes-replication-controllers://%s?oauthToken=%s&operation=deleteReplicationController", host, authToken);
                fromF("kubernetes-replication-controllers://%s?oauthToken=%s&resourceName=wildfly", host, authToken).process(new KubernetesProcessor()).to(mockResultEndpoint);
            }
        };
    }

    public class KubernetesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            log.info("Got event with body: " + in.getBody() + " and action " + in.getHeader(KubernetesConstants.KUBERNETES_EVENT_ACTION));
        }
    }
}
