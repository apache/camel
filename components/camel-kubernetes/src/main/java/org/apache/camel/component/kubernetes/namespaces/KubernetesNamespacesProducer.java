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
package org.apache.camel.component.kubernetes.namespaces;

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneableNamespace;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
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

public class KubernetesNamespacesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesNamespacesProducer.class);

    public KubernetesNamespacesProducer(AbstractKubernetesEndpoint endpoint) {
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

        case KubernetesOperations.LIST_NAMESPACE_OPERATION:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_NAMESPACE_BY_LABELS_OPERATION:
            doListNamespaceByLabel(exchange, operation);
            break;

        case KubernetesOperations.GET_NAMESPACE_OPERATION:
            doGetNamespace(exchange, operation);
            break;

        case KubernetesOperations.CREATE_NAMESPACE_OPERATION:
            doCreateNamespace(exchange, operation);
            break;

        case KubernetesOperations.DELETE_NAMESPACE_OPERATION:
            doDeleteNamespace(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        NamespaceList namespacesList = getEndpoint().getKubernetesClient()
                .namespaces().list();
        exchange.getOut().setBody(namespacesList.getItems());
    }

    protected void doListNamespaceByLabel(Exchange exchange, String operation) {
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get a specific namespace by labels require specify a labels set");
            throw new IllegalArgumentException(
                    "Get a specific namespace by labels require specify a labels set");
        }
        NonNamespaceOperation<Namespace, NamespaceList, DoneableNamespace, Resource<Namespace, DoneableNamespace>> namespaces = getEndpoint().getKubernetesClient().namespaces();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            namespaces.withLabel(entry.getKey(), entry.getValue());
        }
        NamespaceList namespace = namespaces.list();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(namespace.getItems());
    }

    protected void doGetNamespace(Exchange exchange, String operation) {
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific namespace require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific namespace require specify a namespace name");
        }
        Namespace namespace = getEndpoint().getKubernetesClient().namespaces()
                .withName(namespaceName).get();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(namespace);
    }

    protected void doCreateNamespace(Exchange exchange, String operation) {
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific namespace require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific namespace require specify a namespace name");
        }
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_LABELS, Map.class);
        Namespace ns = new NamespaceBuilder().withNewMetadata()
                .withName(namespaceName).withLabels(labels).endMetadata()
                .build();
        Namespace namespace = getEndpoint().getKubernetesClient().namespaces()
                .create(ns);
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(namespace);
    }

    protected void doDeleteNamespace(Exchange exchange, String operation) {
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific namespace require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific namespace require specify a namespace name");
        }
        Boolean namespace = getEndpoint().getKubernetesClient().namespaces()
                .withName(namespaceName).delete();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(namespace);
    }
}
