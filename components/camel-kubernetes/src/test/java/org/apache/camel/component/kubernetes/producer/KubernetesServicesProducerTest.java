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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class KubernetesServicesProducerTest extends KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<Service> result = template.requestBody("direct:list", "",
                List.class);

        boolean fabric8Exists = false;

        Iterator<Service> it = result.iterator();
        while (it.hasNext()) {
            Service service = (Service) it.next();
            if ("fabric8".equalsIgnoreCase(service.getMetadata().getName())) {
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
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
            }
        });

        List<Service> result = ex.getOut().getBody(List.class);

        boolean serviceExists = false;
        Iterator<Service> it = result.iterator();
        while (it.hasNext()) {
            Service service = (Service) it.next();
            if ("elasticsearch".equalsIgnoreCase(service.getMetadata()
                    .getName())) {
                serviceExists = true;
            }
        }

        assertFalse(serviceExists);
    }

    @Test
    public void getServiceTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:getServices", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_NAME,
                        "elasticsearch");
            }
        });

        Service result = ex.getOut().getBody(Service.class);

        assertNull(result);
    }

    @Test
    public void createAndDeleteService() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:createService", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_NAME, "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_LABELS, labels);
                ServiceSpec serviceSpec = new ServiceSpec();
                List<ServicePort> lsp = new ArrayList<ServicePort>();
                ServicePort sp = new ServicePort();
                sp.setPort(8080);
                sp.setTargetPort(new IntOrString(8080));
                sp.setProtocol("TCP");
                lsp.add(sp);
                serviceSpec.setPorts(lsp);
                Map<String, String> selectorMap = new HashMap<String, String>();
                selectorMap.put("containter", "test");
                serviceSpec.setSelector(selectorMap);
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_SPEC,
                        serviceSpec);
            }
        });

        Service serv = ex.getOut().getBody(Service.class);

        assertEquals(serv.getMetadata().getName(), "test");

        ex = template.request("direct:deleteService", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_NAME, "test");
            }
        });

        boolean servDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(servDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=listServices",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=listServicesByLabels",
                                host, authToken);
                from("direct:getServices")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=getService",
                                host, authToken);
                from("direct:createService")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=createService",
                                host, authToken);
                from("direct:deleteService")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=deleteService",
                                host, authToken);
            }
        };
    }
}
