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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesPersistentVolumesClaimsProducerTest extends KubernetesTestSupport {

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("kubernetesClient", server.getClient());
        return registry;
    }

    @Test
    public void listTest() throws Exception {
        server.expect().withPath("/api/v1/namespaces/test/persistentvolumeclaims")
            .andReturn(200, new PersistentVolumeClaimListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        List<PersistentVolumeClaim> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Test
    public void listByLabelsTest() throws Exception {
        server.expect().withPath("/api/v1/namespaces/test/persistentvolumeclaims?labelSelector=" + toUrlEncoded("key1=value1,key2=value2"))
            .andReturn(200, new PersistentVolumeClaimListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();
        Exchange ex = template.request("direct:listByLabels", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                Map<String, String> labels = new HashMap<>();
                labels.put("key1", "value1");
                labels.put("key2", "value2");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, labels);
            }
        });

        List<PersistentVolume> result = ex.getOut().getBody(List.class);
        assertEquals(3, result.size());
    }

    @Test
    public void createListAndDeletePersistentVolumeClaim() throws Exception {
        ObjectMeta meta = new ObjectMeta();
        meta.setName("pvc1");
        server.expect().withPath("/api/v1/namespaces/test/persistentvolumeclaims/pvc1").andReturn(200, new PersistentVolumeClaimBuilder().withMetadata(meta).build()).once();
        Exchange ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, "test");
                exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, "pvc1");
            }
        });

        boolean pvcDeleted = ex.getOut().getBody(Boolean.class);

        assertTrue(pvcDeleted);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumesClaims");
                from("direct:listByLabels").to("kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=listPersistentVolumesClaimsByLabels");
                from("direct:delete").to("kubernetes-persistent-volumes-claims:///?kubernetesClient=#kubernetesClient&operation=deletePersistentVolumeClaim");
            }
        };
    }
}
