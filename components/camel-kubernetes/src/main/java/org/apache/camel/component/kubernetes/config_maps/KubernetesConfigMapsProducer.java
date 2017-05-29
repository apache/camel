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
package org.apache.camel.component.kubernetes.config_maps;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
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

public class KubernetesConfigMapsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesConfigMapsProducer.class);

    public KubernetesConfigMapsProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getOperation())) {
            operation = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = getEndpoint().getKubernetesConfiguration().getOperation();
        }

        switch (operation) {

        case KubernetesOperations.LIST_CONFIGMAPS:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_CONFIGMAPS_BY_LABELS_OPERATION:
            doListConfigMapsByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_CONFIGMAP_OPERATION:
            doGetConfigMap(exchange, operation);
            break;

        case KubernetesOperations.CREATE_CONFIGMAP_OPERATION:
            doCreateConfigMap(exchange, operation);
            break;

        case KubernetesOperations.DELETE_CONFIGMAP_OPERATION:
            doDeleteConfigMap(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        ConfigMapList configMapsList = getEndpoint().getKubernetesClient().configMaps().inAnyNamespace().list();
        exchange.getOut().setBody(configMapsList.getItems());
    }

    protected void doListConfigMapsByLabels(Exchange exchange, String operation) throws Exception {
        ConfigMapList configMapsList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, Map.class);
        FilterWatchListMultiDeletable<ConfigMap, ConfigMapList, Boolean, Watch, Watcher<ConfigMap>> configMaps = getEndpoint().getKubernetesClient().configMaps().inAnyNamespace();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            configMaps.withLabel(entry.getKey(), entry.getValue());
        }
        configMapsList = configMaps.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(configMapsList.getItems());
    }

    protected void doGetConfigMap(Exchange exchange, String operation) throws Exception {
        ConfigMap configMap = null;
        String cfMapName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, String.class);
        if (ObjectHelper.isEmpty(cfMapName)) {
            LOG.error("Get a specific ConfigMap require specify a ConfigMap name");
            throw new IllegalArgumentException("Get a specific ConfigMap require specify a ConfigMap name");
        }
        configMap = getEndpoint().getKubernetesClient().configMaps().withName(cfMapName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(configMap);
    }

    protected void doCreateConfigMap(Exchange exchange, String operation) throws Exception {
        ConfigMap configMap = null;
        String cfMapName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        HashMap<String, String> configMapData = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAP_DATA, HashMap.class);
        if (ObjectHelper.isEmpty(cfMapName)) {
            LOG.error("Create a specific configMap require specify a configMap name");
            throw new IllegalArgumentException("Create a specific configMap require specify a configMap name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific configMap require specify a namespace name");
            throw new IllegalArgumentException("Create a specific configMap require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(configMapData)) {
            LOG.error("Create a specific configMap require specify a data map");
            throw new IllegalArgumentException("Create a specific configMap require specify a data map");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CONFIGMAPS_LABELS, Map.class);
        ConfigMap cfMapCreating = new ConfigMapBuilder().withNewMetadata().withName(cfMapName).withLabels(labels).endMetadata().withData(configMapData).build();
        configMap = getEndpoint().getKubernetesClient().configMaps().inNamespace(namespaceName).create(cfMapCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(configMap);
    }

    protected void doDeleteConfigMap(Exchange exchange, String operation) throws Exception {
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
        boolean cfMapDeleted = getEndpoint().getKubernetesClient().configMaps().inNamespace(namespaceName).withName(configMapName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(cfMapDeleted);
    }
}
