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

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

public class KubernetesPersistentVolumesClaimsProducerTest extends
        KubernetesTestSupport {

    @Test
    public void listTest() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        List<PersistentVolumeClaim> result = template.requestBody(
                "direct:list", "", List.class);

        assertTrue(result.size() == 0);
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
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS,
                                labels);
            }
        });

        List<PersistentVolume> result = ex.getOut().getBody(List.class);
    }

    @Test
    public void createListAndDeletePersistentVolumeClaim() throws Exception {
        if (ObjectHelper.isEmpty(authToken)) {
            return;
        }
        Exchange ex = template.request("direct:create", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME,
                                "test");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS,
                                labels);
                PersistentVolumeClaimSpec pvcSpec = new PersistentVolumeClaimSpec();
                ResourceRequirements rr = new ResourceRequirements();
                Map<String, Quantity> mp = new HashMap<String, Quantity>();
                mp.put("storage", new Quantity("100"));
                rr.setLimits(mp);
                Map<String, Quantity> req = new HashMap<String, Quantity>();
                req.put("storage", new Quantity("100"));
                rr.setRequests(req);
                pvcSpec.setResources(rr);
                pvcSpec.setVolumeName("vol001");
                List<String> access = new ArrayList<String>();
                access.add("ReadWriteOnce");
                pvcSpec.setAccessModes(access);
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC,
                                pvcSpec);
            }
        });

        PersistentVolumeClaim pvc = ex.getOut().getBody(
                PersistentVolumeClaim.class);

        assertEquals(pvc.getMetadata().getName(), "test");

        ex = template.request("direct:listByLabels", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                Map<String, String> labels = new HashMap<String, String>();
                labels.put("this", "rocks");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS,
                                labels);
            }
        });

        List<PersistentVolumeClaim> result = ex.getOut().getBody(List.class);

        boolean pvcExists = false;
        Iterator<PersistentVolumeClaim> it = result.iterator();
        while (it.hasNext()) {
            PersistentVolumeClaim pvcLocal = (PersistentVolumeClaim) it.next();
            if ("test".equalsIgnoreCase(pvcLocal.getMetadata().getName())) {
                pvcExists = true;
            }
        }

        assertTrue(pvcExists);

        ex = template.request("direct:delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(
                        KubernetesConstants.KUBERNETES_NAMESPACE_NAME,
                        "default");
                exchange.getIn()
                        .setHeader(
                                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME,
                                "test");
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
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=persistentVolumesClaims&operation=listPersistentVolumesClaims",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=persistentVolumesClaims&operation=listPersistentVolumesClaimsByLabels",
                                host, authToken);
                from("direct:create")
                        .toF("kubernetes://%s?oauthToken=%s&category=persistentVolumesClaims&operation=createPersistentVolumeClaim",
                                host, authToken);
                from("direct:delete")
                        .toF("kubernetes://%s?oauthToken=%s&category=persistentVolumesClaims&operation=deletePersistentVolumeClaim",
                                host, authToken);
            }
        };
    }
}
