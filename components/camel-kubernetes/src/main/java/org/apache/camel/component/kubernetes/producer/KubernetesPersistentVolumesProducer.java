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

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneablePersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeList;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesPersistentVolumesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesPersistentVolumesProducer.class);

    public KubernetesPersistentVolumesProducer(KubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public KubernetesEndpoint getEndpoint() {
        return (KubernetesEndpoint) super.getEndpoint();
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

        case KubernetesOperations.LIST_PERSISTENT_VOLUMES:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_PERSISTENT_VOLUMES_BY_LABELS_OPERATION:
            doListPersistentVolumesByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_PERSISTENT_VOLUME_OPERATION:
            doGetPersistentVolume(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        PersistentVolumeList persistentVolumeList = getEndpoint()
                .getKubernetesClient().persistentVolumes().list();
        exchange.getOut().setBody(persistentVolumeList.getItems());
    }

    protected void doListPersistentVolumesByLabels(Exchange exchange,
            String operation) throws Exception {
        PersistentVolumeList pvList = null;
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_LABELS,
                Map.class);
        ClientNonNamespaceOperation<PersistentVolume, PersistentVolumeList, DoneablePersistentVolume, ClientResource<PersistentVolume, DoneablePersistentVolume>> pvs; 
        pvs = getEndpoint().getKubernetesClient().persistentVolumes();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            pvs.withLabel(entry.getKey(), entry.getValue());
        }
        pvList = pvs.list();
        exchange.getOut().setBody(pvList.getItems());
    }

    protected void doGetPersistentVolume(Exchange exchange, String operation)
            throws Exception {
        PersistentVolume pv = null;
        String pvName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_NAME,
                String.class);
        if (ObjectHelper.isEmpty(pvName)) {
            LOG.error("Get a specific Persistent Volume require specify a Persistent Volume name");
            throw new IllegalArgumentException(
                    "Get a specific Persistent Volume require specify a Persistent Volume name");
        }
        pv = getEndpoint().getKubernetesClient().persistentVolumes().withName(pvName).get();
        exchange.getOut().setBody(pv);
    }
}
