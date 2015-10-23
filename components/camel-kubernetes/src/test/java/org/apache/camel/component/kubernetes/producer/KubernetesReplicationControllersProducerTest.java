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
package org.apache.camel.component.kubernetes.producer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EditablePodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class KubernetesReplicationControllersProducerTest extends
        KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<ReplicationController> result = template.requestBody(
                "direct:list", "", List.class);

        boolean fabric8Exists = false;

        Iterator<ReplicationController> it = result.iterator();
        while (it.hasNext()) {
            ReplicationController rc = (ReplicationController) it.next();
            if ("fabric8".equalsIgnoreCase(rc.getMetadata().getName())) {
                fabric8Exists = true;
            }
        }

        assertTrue(fabric8Exists);
    }

    @Test
    public void listByLabelsTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("component", "elasticsearch");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS,
                                labels);
            }
        });

        List<ReplicationController> result = ex.getOut().getBody(List.class);

        boolean rcExists = false;
        Iterator<ReplicationController> it = result.iterator();
        while (it.hasNext()) {
            ReplicationController rc = (ReplicationController) it.next();
            if ("elasticsearch".equalsIgnoreCase(rc.getMetadata().getName())) {
                rcExists = true;
            }
        }

        assertFalse(rcExists);
    }

    @Test
    public void getReplicationControllerTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:getReplicationController",
                new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader(
                                KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                                "default");
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                                        "elasticsearch");
                    }
                });

        ReplicationController result = ex.getOut().getBody(
                ReplicationController.class);

        assertNull(result);
    }

    @Test
    public void createAndDeleteService() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:createReplicationController",
                new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader(
                                KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                                "default");
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                                        "test");
                        Map<String, String> labels = new HashMap<String, String>();
                        labels.put("this", "rocks");
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLERS_LABELS,
                                        labels);
                        ReplicationControllerSpec rcSpec = new ReplicationControllerSpec();
                        rcSpec.setReplicas(2);
                        PodTemplateSpecBuilder builder = new PodTemplateSpecBuilder();
                        EditablePodTemplateSpec t = builder.withNewMetadata()
                                .withName("nginx-template")
                                .addToLabels("server", "nginx").endMetadata()
                                .withNewSpec().addNewContainer()
                                .withName("wildfly").withImage("jboss/wildfly")
                                .addNewPort().withContainerPort(80).endPort()
                                .endContainer().endSpec().build();
                        rcSpec.setTemplate(t);
                        Map<String, String> selectorMap = new HashMap<String, String>();
                        selectorMap.put("server", "nginx");
                        rcSpec.setSelector(selectorMap);
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_SPEC,
                                        rcSpec);
                    }
                });

        ReplicationController rc = ex.getOut().getBody(
                ReplicationController.class);

        assertEquals(rc.getMetadata().getName(), "test");

        ex = template.request("direct:deleteReplicationController",
                new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader(
                                KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                                "default");
                        exchange.getIn()
                                .setHeader(
                                        KubernetesConstants.KUBERNETES_REPLICATION_CONTROLLER_NAME,
                                        "test");
                    }
                });

        boolean rcDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(rcDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=listReplicationControllers",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=listReplicationControllersByLabels",
                                host, authToken);
                from("direct:getReplicationController")
                        .toF("kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=getReplicationController",
                                host, authToken);
                from("direct:createReplicationController")
                        .toF("kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=createReplicationController",
                                host, authToken);
                from("direct:deleteReplicationController")
                        .toF("kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=deleteReplicationController",
                                host, authToken);
            }
        };
    }
}
