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
package org.apache.camel.component.kubernetes.secrets;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
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

public class KubernetesSecretsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesSecretsProducer.class);

    public KubernetesSecretsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_SECRETS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_SECRETS_BY_LABELS_OPERATION:
                doListSecretsByLabels(exchange);
                break;

            case KubernetesOperations.GET_SECRET_OPERATION:
                doGetSecret(exchange);
                break;

            case KubernetesOperations.CREATE_SECRET_OPERATION:
                doCreateSecret(exchange);
                break;

            case KubernetesOperations.UPDATE_SECRET_OPERATION:
                doUpdateSecret(exchange);
                break;

            case KubernetesOperations.DELETE_SECRET_OPERATION:
                doDeleteSecret(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        SecretList secretsList = getEndpoint().getKubernetesClient().secrets().inAnyNamespace().list();
        prepareOutboundMessage(exchange, secretsList.getItems());
    }

    protected void doListSecretsByLabels(Exchange exchange) {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SECRETS_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        SecretList secretsList;
        if (!ObjectHelper.isEmpty(namespaceName)) {
            secretsList = getEndpoint()
                    .getKubernetesClient()
                    .secrets()
                    .inNamespace(namespaceName)
                    .withLabels(labels)
                    .list();
        } else {
            secretsList = getEndpoint()
                    .getKubernetesClient()
                    .secrets()
                    .inAnyNamespace()
                    .withLabels(labels).list();
        }

        prepareOutboundMessage(exchange, secretsList.getItems());
    }

    protected void doGetSecret(Exchange exchange) {
        String secretName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            LOG.error("Get a specific Secret require specify a Secret name");
            throw new IllegalArgumentException("Get a specific Secret require specify a Secret name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Secret require specify a namespace name");
            throw new IllegalArgumentException("Get a specific Secret require specify a namespace name");
        }
        Secret secret = getEndpoint().getKubernetesClient().secrets().inNamespace(namespaceName).withName(secretName).get();

        prepareOutboundMessage(exchange, secret);
    }

    protected void doUpdateSecret(Exchange exchange) {
        doCreateOrUpdateSecret(exchange, "Update", Resource::update);
    }

    protected void doCreateSecret(Exchange exchange) {
        doCreateOrUpdateSecret(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateSecret(Exchange exchange, String operationName, Function<Resource<Secret>, Secret> operation) {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Secret secretToCreate = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SECRET, Secret.class);
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific secret require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific secret require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(secretToCreate)) {
            LOG.error("{} a specific secret require specify a secret bean", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific secret require specify a secret bean", operationName));
        }
        Secret secret
                = operation.apply(
                        getEndpoint().getKubernetesClient().secrets().inNamespace(namespaceName).resource(secretToCreate));

        prepareOutboundMessage(exchange, secret);
    }

    protected void doDeleteSecret(Exchange exchange) {
        String secretName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SECRET_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(secretName)) {
            LOG.error("Delete a specific secret require specify a secret name");
            throw new IllegalArgumentException("Delete a specific secret require specify a secret name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific secret require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific secret require specify a namespace name");
        }

        List<StatusDetails> statusDetails
                = getEndpoint().getKubernetesClient().secrets().inNamespace(namespaceName).withName(secretName).delete();
        boolean secretDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, secretDeleted);
    }
}
