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
package org.apache.camel.component.kubernetes.persistent_volumes;

import java.util.Map;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeList;
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

public class KubernetesPersistentVolumesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPersistentVolumesProducer.class);

    public KubernetesPersistentVolumesProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_PERSISTENT_VOLUMES:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_PERSISTENT_VOLUMES_BY_LABELS_OPERATION:
                doListPersistentVolumesByLabels(exchange);
                break;

            case KubernetesOperations.GET_PERSISTENT_VOLUME_OPERATION:
                doGetPersistentVolume(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        PersistentVolumeList persistentVolumeList = getEndpoint().getKubernetesClient().persistentVolumes().list();

        prepareOutboundMessage(exchange, persistentVolumeList.getItems());
    }

    protected void doListPersistentVolumesByLabels(Exchange exchange) {
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUMES_LABELS, Map.class);
        PersistentVolumeList pvList = getEndpoint().getKubernetesClient().persistentVolumes().withLabels(labels).list();

        prepareOutboundMessage(exchange, pvList.getItems());
    }

    protected void doGetPersistentVolume(Exchange exchange) {
        String pvName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_PERSISTENT_VOLUME_NAME, String.class);
        if (ObjectHelper.isEmpty(pvName)) {
            LOG.error("Get a specific Persistent Volume require specify a Persistent Volume name");
            throw new IllegalArgumentException("Get a specific Persistent Volume require specify a Persistent Volume name");
        }
        PersistentVolume pv = getEndpoint().getKubernetesClient().persistentVolumes().withName(pvName).get();

        prepareOutboundMessage(exchange, pv);
    }
}
