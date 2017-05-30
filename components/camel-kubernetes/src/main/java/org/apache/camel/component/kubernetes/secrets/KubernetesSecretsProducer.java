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
package org.apache.camel.component.kubernetes.secrets;

import java.util.Map;

import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
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

public class KubernetesSecretsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesSecretsProducer.class);

    public KubernetesSecretsProducer(AbstractKubernetesEndpoint endpoint) {
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

        case KubernetesOperations.LIST_SECRETS:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_SECRETS_BY_LABELS_OPERATION:
            doListSecretsByLabels(exchange, operation);
            break;

        case KubernetesOperations.GET_SECRET_OPERATION:
            doGetSecret(exchange, operation);
            break;

        case KubernetesOperations.CREATE_SECRET_OPERATION:
            doCreateSecret(exchange, operation);
            break;

        case KubernetesOperations.DELETE_SECRET_OPERATION:
            doDeleteSecret(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        SecretList secretsList = getEndpoint().getKubernetesClient().secrets().inAnyNamespace()
                .list();
        exchange.getOut().setBody(secretsList.getItems());
    }

    protected void doListSecretsByLabels(Exchange exchange, String operation)
            throws Exception {
        SecretList secretsList = null;
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SECRETS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            NonNamespaceOperation<Secret, SecretList, DoneableSecret, Resource<Secret, DoneableSecret>> secrets; 
            secrets = getEndpoint().getKubernetesClient().secrets()
                    .inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                secrets.withLabel(entry.getKey(), entry.getValue());
            }
            secretsList = secrets.list();
        } else {
            FilterWatchListMultiDeletable<Secret, SecretList, Boolean, Watch, Watcher<Secret>> secrets; 
            secrets = getEndpoint().getKubernetesClient().secrets().inAnyNamespace();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                secrets.withLabel(entry.getKey(), entry.getValue());
            }
            secretsList = secrets.list();
        }
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(secretsList.getItems());
    }

    protected void doGetSecret(Exchange exchange, String operation)
            throws Exception {
        Secret secret = null;
        String secretName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SECRET_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            LOG.error("Get a specific Secret require specify a Secret name");
            throw new IllegalArgumentException(
                    "Get a specific Secret require specify a Secret name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Secret require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific Secret require specify a namespace name");
        }
        secret = getEndpoint().getKubernetesClient().secrets()
                .inNamespace(namespaceName).withName(secretName).get();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(secret);
    }

    protected void doCreateSecret(Exchange exchange, String operation)
            throws Exception {
        Secret secret = null;
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Secret secretToCreate = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SECRET, Secret.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific secret require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific secret require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(secretToCreate)) {
            LOG.error("Create a specific secret require specify a secret bean");
            throw new IllegalArgumentException(
                    "Create a specific secret require specify a secret bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SECRETS_LABELS, Map.class);
        secret = getEndpoint().getKubernetesClient().secrets()
                .inNamespace(namespaceName).create(secretToCreate);
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(secret);
    }

    protected void doDeleteSecret(Exchange exchange, String operation)
            throws Exception {
        String secretName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_SECRET_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            LOG.error("Delete a specific secret require specify a secret name");
            throw new IllegalArgumentException(
                    "Delete a specific secret require specify a secret name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific secret require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific secret require specify a namespace name");
        }
        boolean secretDeleted = getEndpoint().getKubernetesClient().secrets()
                .inNamespace(namespaceName).withName(secretName).delete();
        
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(secretDeleted);
    }
}
