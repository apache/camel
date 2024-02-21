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
package org.apache.camel.component.openshift.producer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.openshift.api.model.BuildListBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnableKubernetesMockClient
public class OpenshiftBuildsProducerTest extends KubernetesTestSupport {

    KubernetesMockServer server;
    NamespacedKubernetesClient client;

    @BindToRegistry("client")
    public NamespacedKubernetesClient loadClient() throws Exception {
        server.expect().withPath("/apis/build.openshift.io/v1/builds")
                .andReturn(200, new BuildListBuilder().addNewItem().and().addNewItem().and().build()).once();
        server.expect().withPath("/apis/build.openshift.io/v1/builds?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
                .andReturn(200, new BuildListBuilder().addNewItem().and().addNewItem().and().build()).once();
        server.expect().withPath("/apis")
                .andReturn(200,
                        new APIGroupListBuilder().addNewGroup().withApiVersion("v1").withName("autoscaling.k8s.io").endGroup()
                                .addNewGroup()
                                .withApiVersion("v1").withName("security.openshift.io").endGroup().build())
                .always();
        return client;
    }

    @Test
    void listTest() {
        List<?> result = template.requestBody("direct:list", "", List.class);

        assertEquals(2, result.size());
    }

    @Test
    void listByLabelsTest() {
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_BUILDS_LABELS, labels);
        });

        List<?> result = ex.getMessage().getBody(List.class);

        assertEquals(2, result.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:list").to("openshift-builds:///?operation=listBuilds&kubernetesClient=#client");
                from("direct:listByLabels").to("openshift-builds:///?operation=listBuildsByLabels&kubernetesClient=#client");
            }
        };
    }
}
