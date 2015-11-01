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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ServiceAccount;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

public class KubernetesServiceAccountsProducerTest extends KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<ServiceAccount> result = template.requestBody("direct:list", "",
                List.class);

        boolean fabric8Exists = false;

        Iterator<ServiceAccount> it = result.iterator();
        while (it.hasNext()) {
            ServiceAccount service = (ServiceAccount) it.next();
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
                        KubernetesConstants.KUBERNETES_SERVICE_ACCOUNTS_LABELS, labels);
            }
        });

        List<ServiceAccount> result = ex.getOut().getBody(List.class);

        assertTrue(result.size() == 0);
    }

    @Test
    public void createAndDeleteServiceAccount() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:create", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT_NAME, "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_ACCOUNTS_LABELS, labels);
                ServiceAccount s = new ServiceAccount();
                s.setKind("ServiceAccount");
                Map<String, String> mp = new HashMap<String, String>();
                mp.put("username", Base64.encodeBase64String("pippo".getBytes()));
                mp.put("password", Base64.encodeBase64String("password".getBytes()));
                ObjectMeta meta = new ObjectMeta();
                meta.setName("test");
                s.setMetadata(meta);
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT, s);
            }
        });

        ServiceAccount sec = ex.getOut().getBody(ServiceAccount.class);

        assertEquals(sec.getMetadata().getName(), "test");

        ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT_NAME, "test");
            }
        });

        boolean secDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(secDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=listServiceAccounts",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=listServiceAccountsByLabels",
                                host, authToken);
                from("direct:getServices")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=getServiceAccount",
                                host, authToken);
                from("direct:create")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=createServiceAccount",
                                host, authToken);
                from("direct:delete")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=deleteServiceAccount",
                                host, authToken);
            }
        };
    }
}
