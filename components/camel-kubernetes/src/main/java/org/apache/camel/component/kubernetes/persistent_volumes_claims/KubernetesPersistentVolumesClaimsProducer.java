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
package org.apache.camel.component.kubernetes.persistent_volumes_claims;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesHelper;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kubernetes.KubernetesHelper.prepareOutboundMessage;

public class KubernetesPersistentVolumesClaimsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPersistentVolumesClaimsProducer.class);

    public KubernetesPersistentVolumesClaimsProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = KubernetesHelper.extractOperation(getEndpoint(), exchange);

        switch (operation) {

            case KubernetesOperations.LIST_PERSISTENT_VOLUMES_CLAIMS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_PERSISTENT_VOLUMES_CLAIMS_BY_LABELS_OPERATION:
                doListPersistentVolumesClaimsByLabels(exchange);
                break;

            case KubernetesOperations.GET_PERSISTENT_VOLUME_CLAIM_OPERATION:
                doGetPersistentVolumeClaim(exchange);
                break;

            case KubernetesOperations.CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION:
                doCreatePersistentVolumeClaim(exchange);
                break;

            case KubernetesOperations.UPDATE_PERSISTENT_VOLUME_CLAIM_OPERATION:
                doUpdatePersistentVolumeClaim(exchange);
                break;

            case KubernetesOperations.DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION:
                doDeletePersistentVolumeClaim(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        PersistentVolumeClaimList persistentVolumeClaimList
                = getEndpoint().getKubernetesClient().persistentVolumeClaims().list();
        prepareOutboundMessage(exchange, persistentVolumeClaimList.getItems());
    }

    protected void doListPersistentVolumesClaimsByLabels(Exchange exchange) {
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        PersistentVolumeClaimList pvcList;
        if (!ObjectHelper.isEmpty(namespaceName)) {
            pvcList = getEndpoint()
                    .getKubernetesClient()
                    .persistentVolumeClaims()
                    .inNamespace(namespaceName)
                    .withLabels(labels)
                    .list();
        } else {
            pvcList = getEndpoint()
                    .getKubernetesClient()
                    .persistentVolumeClaims()
                    .inAnyNamespace()
                    .withLabels(labels)
                    .list();
        }

        prepareOutboundMessage(exchange, pvcList.getItems());
    }

    protected void doGetPersistentVolumeClaim(Exchange exchange) {
        String pvcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("Get a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
            throw new IllegalArgumentException(
                    "Get a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Persistent Volume Claim require specify a namespace name");
            throw new IllegalArgumentException("Get a specific Persistent Volume Claim require specify a namespace name");
        }
        PersistentVolumeClaim pvc = getEndpoint().getKubernetesClient().persistentVolumeClaims().inNamespace(namespaceName)
                .withName(pvcName).get();

        prepareOutboundMessage(exchange, pvc);
    }

    protected void doUpdatePersistentVolumeClaim(Exchange exchange) {
        doCreateOrUpdatePersistentVolumeClaim(exchange, "Update", Resource::update);
    }

    protected void doCreatePersistentVolumeClaim(Exchange exchange) {
        doCreateOrUpdatePersistentVolumeClaim(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdatePersistentVolumeClaim(
            Exchange exchange, String operationName,
            Function<Resource<PersistentVolumeClaim>, PersistentVolumeClaim> operation) {
        String pvcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        PersistentVolumeClaimSpec pvcSpec = exchange.getIn()
                .getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC, PersistentVolumeClaimSpec.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("{} a specific Persistent Volume Claim require specify a Persistent Volume Claim name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Persistent Volume Claim require specify a Persistent Volume Claim name",
                            operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific Persistent Volume Claim require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Persistent Volume Claim require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(pvcSpec)) {
            LOG.error("{} a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean",
                    operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Persistent Volume Claim require specify a Persistent Volume Claim spec bean",
                            operationName));
        }
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS, Map.class);
        PersistentVolumeClaim pvcCreating = new PersistentVolumeClaimBuilder().withNewMetadata().withName(pvcName)
                .withLabels(labels).endMetadata().withSpec(pvcSpec).build();
        PersistentVolumeClaim pvc
                = operation.apply(getEndpoint().getKubernetesClient().persistentVolumeClaims().inNamespace(namespaceName)
                        .resource(pvcCreating));

        prepareOutboundMessage(exchange, pvc);
    }

    protected void doDeletePersistentVolumeClaim(Exchange exchange) {
        String pvcName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(pvcName)) {
            LOG.error("Delete a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
            throw new IllegalArgumentException(
                    "Delete a specific Persistent Volume Claim require specify a Persistent Volume Claim name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific Persistent Volume Claim require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific Persistent Volume Claim require specify a namespace name");
        }

        List<StatusDetails> statusDetails
                = getEndpoint().getKubernetesClient().persistentVolumeClaims().inNamespace(namespaceName)
                        .withName(pvcName).delete();
        boolean pvcDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, pvcDeleted);
    }
}
