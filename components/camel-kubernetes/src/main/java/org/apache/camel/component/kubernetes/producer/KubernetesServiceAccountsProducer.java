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

import io.fabric8.kubernetes.api.model.DoneableServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientMixedOperation;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientOperation;
import io.fabric8.kubernetes.client.dsl.ClientResource;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesServiceAccountsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesServiceAccountsProducer.class);

    public KubernetesServiceAccountsProducer(KubernetesEndpoint endpoint) {
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

        case KubernetesOperations.LIST_SERVICE_ACCOUNTS:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_SERVICE_ACCOUNTS_BY_LABELS_OPERATION:
            doListServiceAccountsByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_SECRET_OPERATION:
            doGetServiceAccount(exchange, operation);
            break;

        case KubernetesOperations.CREATE_SERVICE_ACCOUNT_OPERATION:
            doCreateServiceAccount(exchange, operation);
            break;

        case KubernetesOperations.DELETE_SERVICE_ACCOUNT_OPERATION:
            doDeleteServiceAccount(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        ServiceAccountList saList = getEndpoint().getKubernetesClient().serviceAccounts()
                .list();
        exchange.getOut().setBody(saList.getItems());
    }

    protected void doListServiceAccountsByLabels(Exchange exchange, String operation)
            throws Exception {
        ServiceAccountList saList = null;
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SERVICE_ACCOUNTS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            ClientNonNamespaceOperation<ServiceAccount, ServiceAccountList, DoneableServiceAccount, ClientResource<ServiceAccount, DoneableServiceAccount>> serviceAccounts; 
            serviceAccounts = getEndpoint().getKubernetesClient().serviceAccounts()
                    .inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                serviceAccounts.withLabel(entry.getKey(), entry.getValue());
            }
            saList = serviceAccounts.list();
        } else {
            ClientMixedOperation<ServiceAccount, ServiceAccountList, DoneableServiceAccount, ClientResource<ServiceAccount, DoneableServiceAccount>> serviceAccounts; 
            serviceAccounts = getEndpoint().getKubernetesClient().serviceAccounts();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                serviceAccounts.withLabel(entry.getKey(), entry.getValue());
            }
            saList = serviceAccounts.list();
        }
        exchange.getOut().setBody(saList.getItems());
    }

    protected void doGetServiceAccount(Exchange exchange, String operation)
            throws Exception {
        ServiceAccount sa = null;
        String saName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(saName)) {
            LOG.error("Get a specific Service Account require specify a Service Account name");
            throw new IllegalArgumentException(
                    "Get a specific Service Account require specify a Service Account name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Service Account require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific Service Account require specify a namespace name");
        }
        sa = getEndpoint().getKubernetesClient().serviceAccounts()
                .inNamespace(namespaceName).withName(saName).get();
        exchange.getOut().setBody(sa);
    }

    protected void doCreateServiceAccount(Exchange exchange, String operation)
            throws Exception {
        ServiceAccount sa = null;
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ServiceAccount saToCreate = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT, ServiceAccount.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific Service Account require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific Service Account require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(saToCreate)) {
            LOG.error("Create a specific Service Account require specify a Service Account bean");
            throw new IllegalArgumentException(
                    "Create a specific Service Account require specify a Service Account bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SERVICE_ACCOUNTS_LABELS, Map.class);
        sa = getEndpoint().getKubernetesClient().serviceAccounts()
                .inNamespace(namespaceName).create(saToCreate);
        exchange.getOut().setBody(sa);
    }

    protected void doDeleteServiceAccount(Exchange exchange, String operation)
            throws Exception {
        String saName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SERVICE_ACCOUNT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(saName)) {
            LOG.error("Delete a specific Service Account require specify a Service Account name");
            throw new IllegalArgumentException(
                    "Delete a specific Service Account require specify a Service Account name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific Service Account require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific Service Account require specify a namespace name");
        }
        boolean saDeleted = getEndpoint().getKubernetesClient().serviceAccounts()
                .inNamespace(namespaceName).withName(saName).delete();
        exchange.getOut().setBody(saDeleted);
    }
}
