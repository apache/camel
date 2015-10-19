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
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaSpec;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class KubernetesResourcesQuotaProducerTest extends KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<ResourceQuota> result = template.requestBody("direct:list", "",
                List.class);

        assertTrue(result.size() == 0);
    }

    @Test
    public void createAndDeleteResourceQuota() throws Exception {
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
                        KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME,
                        "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_LABELS,
                        labels);
                ResourceQuotaSpec rsSpec = new ResourceQuotaSpec();
                Map<String, Quantity> mp = new HashMap<String, Quantity>();
                mp.put("pods", new Quantity("100"));
                rsSpec.setHard(mp);
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_RESOURCE_QUOTA_SPEC,
                        rsSpec);
            }
        });

        ResourceQuota rs = ex.getOut().getBody(ResourceQuota.class);

        assertEquals(rs.getMetadata().getName(), "test");
        
        ex = template.request("direct:get", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME,
                        "test");
            }
        });
        
        ResourceQuota rsGet = ex.getOut().getBody(ResourceQuota.class);
        
        assertEquals(rsGet.getMetadata().getName(), "test");
        assertEquals(rsGet.getSpec().getHard().get("pods"), new Quantity("100"));

        ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME,
                        "test");
            }
        });

        boolean rqDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(rqDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=resourcesQuota&operation=listResourcesQuota",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=resourcesQuota&operation=listResourcesQuotaByLabels",
                                host, authToken);
                from("direct:get")
                        .toF("kubernetes://%s?oauthToken=%s&category=resourcesQuota&operation=getResourceQuota",
                                host, authToken);
                from("direct:create")
                        .toF("kubernetes://%s?oauthToken=%s&category=resourcesQuota&operation=createResourceQuota",
                                host, authToken);
                from("direct:delete")
                        .toF("kubernetes://%s?oauthToken=%s&category=resourcesQuota&operation=deleteResourceQuota",
                                host, authToken);
            }
        };
    }
}
