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
package org.apache.camel.component.kubernetes.config_maps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
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

public class KubernetesConfigMapsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesConfigMapsProducer.class);

    public KubernetesConfigMapsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_CONFIGMAPS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_CONFIGMAPS_BY_LABELS_OPERATION:
                doListConfigMapsByLabels(exchange);
                break;

            case KubernetesOperations.GET_CONFIGMAP_OPERATION:
                doGetConfigMap(exchange);
                break;

            case KubernetesOperations.CREATE_CONFIGMAP_OPERATION:
                doCreateConfigMap(exchange);
                break;

            case KubernetesOperations.UPDATE_CONFIGMAP_OPERATION:
                doUpdateConfigMap(exchange);
                break;

            case KubernetesOperations.DELETE_CONFIGMAP_OPERATION:
                doDeleteConfigMap(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        ConfigMapList configMapsList = getEndpoint().getKubernetesClient().configMaps().inAnyNamespace().list();

        prepareOutboundMessage(exchange, configMapsList.getItems());
    }

    protected void doListConfigMapsByLabels(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, Map.class);
        ConfigMapList configMapsList = getEndpoint().getKubernetesClient()
                .configMaps()
                .inAnyNamespace()
                .withLabels(labels)
                .list();

        prepareOutboundMessage(exchange, configMapsList.getItems());
    }

    protected void doGetConfigMap(Exchange exchange) {
        String cfMapName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(cfMapName)) {
            LOG.error("Get a specific ConfigMap require specify a ConfigMap name");
            throw new IllegalArgumentException("Get a specific ConfigMap require specify a ConfigMap name");
        }
        ConfigMap configMap;
        if (namespaceName != null) {
            configMap = getEndpoint().getKubernetesClient().configMaps().inNamespace(namespaceName).withName(cfMapName).get();
        } else {
            configMap = getEndpoint().getKubernetesClient().configMaps().withName(cfMapName).get();
        }

        prepareOutboundMessage(exchange, configMap);
    }

    protected void doUpdateConfigMap(Exchange exchange) {
        doCreateOrUpdateConfigMap(exchange, "Update", Resource::update);
    }

    protected void doCreateConfigMap(Exchange exchange) {
        doCreateOrUpdateConfigMap(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateConfigMap(
            Exchange exchange, String operationName, Function<Resource<ConfigMap>, ConfigMap> operation) {
        String cfMapName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        HashMap<String, String> configMapData
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, HashMap.class);
        if (ObjectHelper.isEmpty(cfMapName)) {
            LOG.error("{} a specific configMap require specify a configMap name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific configMap require specify a configMap name", operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific configMap require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific configMap require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(configMapData)) {
            LOG.error("{} a specific configMap require specify a data map", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific configMap require specify a data map", operationName));
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, Map.class);
        ConfigMap cfMapCreating = new ConfigMapBuilder().withNewMetadata().withName(cfMapName).withLabels(labels).endMetadata()
                .withData(configMapData).build();
        ConfigMap configMap
                = operation.apply(
                        getEndpoint().getKubernetesClient().configMaps().inNamespace(namespaceName).resource(cfMapCreating));

        prepareOutboundMessage(exchange, configMap);
    }

    protected void doDeleteConfigMap(Exchange exchange) {
        String configMapName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(configMapName)) {
            LOG.error("Delete a specific config map require specify a config map name");
            throw new IllegalArgumentException("Delete a specific config map require specify a config map name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific config map require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific config map require specify a namespace name");
        }
        List<StatusDetails> statusDetails
                = getEndpoint().getKubernetesClient().configMaps().inNamespace(namespaceName).withName(configMapName).delete();
        boolean cfMapDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, cfMapDeleted);
    }
}
