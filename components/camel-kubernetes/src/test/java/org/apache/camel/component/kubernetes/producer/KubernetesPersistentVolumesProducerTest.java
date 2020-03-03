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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesPersistentVolumesProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @BindToRegistry("kubernetesClient")
    public KubernetesClient getClient() throws Exception {
        return server.getClient();
    }

    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/api/v1/persistentvolumes").andReturn(200, new PersistentVolumeListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build())
            .once();
        List<PersistentVolume> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/persistentvolumes?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new PersistentVolumeListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", exchange -> {
            Map<String, String> labels = new HashMap<>();
            labels.put("key1", "value1");
            labels.put("key2", "value2");
            exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_LABELS, labels);
        });

        List<PersistentVolume> result = ex.getMessage().getBody(List.class);

        assertEquals(3, result.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-persistent-volumes:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumes");
                from("direct:listByLabels").to("kubernetes-persistent-volumes:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumesByLabels");
            }
        };
    }
}
