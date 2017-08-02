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
package org.apache.camel.component.kubernetes.persistent_volumes_claims;

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneablePersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesPersistentVolumesClaimsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesPersistentVolumesClaimsProducer.class);

    public KubernetesPersistentVolumesClaimsProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration()
                .getOperation())) {
            operation = exchange.getIn().getHeader(
                    KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = getEndpoint().getKubernetesConfiguration()
                    .getOperation();
        }

        switch (operation) {

        case KubernetesOperations.LIST_PERSISTENT_VOLUMES_CLAIMS:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_PERSISTENT_VOLUMES_CLAIMS_BY_LABELS_OPERATION:
            doListPersistentVolumesClaimsByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_PERSISTENT_VOLUME_CLAIM_OPERATION:
            doGetPersistentVolumeClaim(exchange, operation);
            break;

        case KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION:
            doCreatePersistentVolumeClaim(exchange, operation);
            break;

        case KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION:
            doDeletePersistentVolumeClaim(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        PersistentVolumeClaimList persistentVolumeClaimList = getEndpoint()
                .getKubernetesClient().persistentVolumeClaims().list();
        exchange.getOut().setBody(persistentVolumeClaimList.getItems());
    }

    protected void doListPersistentVolumesClaimsByLabels(Exchange exchange,
            String operation) throws Exception {
        PersistentVolumeClaimList pvcList = null;
        Map<String, String> labels = exchange
                .getIn()
                .getHeader(
                        KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS,
                        Map.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            NonNamespaceOperation<PersistentVolumeClaim, PersistentVolumeClaimList, DoneablePersistentVolumeClaim, 
            Resource<PersistentVolumeClaim, DoneablePersistentVolumeClaim>> pvcs = getEndpoint().getKubernetesClient().persistentVolumeClaims()
                    .inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                pvcs.withLabel(entry.getKey(), entry.getValue());
            }
            pvcList = pvcs.list();
        } else {
            FilterWatchListMultiDeletable<PersistentVolumeClaim, PersistentVolumeClaimList, Boolean, 
            Watch, Watcher<PersistentVolumeClaim>> pvcs = getEndpoint().getKubernetesClient().persistentVolumeClaims().inAnyNamespace();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                pvcs.withLabel(entry.getKey(), entry.getValue());
            }
            pvcList = pvcs.list();
        }
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pvcList.getItems());
    }

    protected void doGetPersistentVolumeClaim(Exchange exchange,
            String operation) throws Exception {
        PersistentVolumeClaim pvc = null;
        String pvcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("Get a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
            throw new IllegalArgumentException(
                    "Get a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Persistent Volume Claim require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific Persistent Volume Claim require specify a namespace name");
        }
        pvc = getEndpoint().getKubernetesClient().persistentVolumeClaims()
                .inNamespace(namespaceName).withName(pvcName).get();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pvc);
    }

    protected void doCreatePersistentVolumeClaim(Exchange exchange,
            String operation) throws Exception {
        PersistentVolumeClaim pvc = null;
        String pvcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        PersistentVolumeClaimSpec pvcSpec = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC,
                PersistentVolumeClaimSpec.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
            throw new IllegalArgumentException(
                    "Create a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific Persistent Volume Claim require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific Persistent Volume Claim require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(pvcSpec)) {
            LOG.error("Create a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean");
            throw new IllegalArgumentException(
                    "Create a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean");
        }
        Map<String, String> labels = exchange
                .getIn()
                .getHeader(
                        KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS,
                        Map.class);
        PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName(pvcName).withLabels(labels)
                .endMetadata().withSpec(pvcSpec).build();
        pvc = getEndpoint().getKubernetesClient().persistentVolumeClaims()
                .inNamespace(namespaceName).create(pvcCreating);
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pvc);
    }

    protected void doDeletePersistentVolumeClaim(Exchange exchange,
            String operation) throws Exception {
        String pvcName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME,
                String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("Delete a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
            throw new IllegalArgumentException(
                    "Delete a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific Persistent Volume Claim require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific Persistent Volume Claim require specify a namespace name");
        }
        boolean pvcDeleted = getEndpoint().getKubernetesClient()
                .persistentVolumeClaims().inNamespace(namespaceName)
                .withName(pvcName).delete();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(pvcDeleted);
    }
}
