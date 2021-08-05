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
package org.apache.camel.component.kubernetes.resources_quota;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.ResourceQuotaList;
import io.fabric8.kubernetes.api.model.ResourceQuotaSpec;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
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

public class KubernetesResourcesQuotaProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesResourcesQuotaProducer.class);

    public KubernetesResourcesQuotaProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_RESOURCES_QUOTA:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_SECRETS_BY_LABELS_OPERATION:
                doListResourceQuotasByLabels(exchange);
                break;

            case KubernetesOperations.GET_RESOURCE_QUOTA_OPERATION:
                doGetResourceQuota(exchange);
                break;

            case KubernetesOperations.CREATE_RESOURCE_QUOTA_OPERATION:
                doCreateResourceQuota(exchange);
                break;

            case KubernetesOperations.DELETE_RESOURCE_QUOTA_OPERATION:
                doDeleteResourceQuota(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        ResourceQuotaList resList = getEndpoint().getKubernetesClient().resourceQuotas().inAnyNamespace().list();

        prepareOutboundMessage(exchange, resList.getItems());
    }

    protected void doListResourceQuotasByLabels(Exchange exchange) {
        ResourceQuotaList resList = null;
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            NonNamespaceOperation<ResourceQuota, ResourceQuotaList, Resource<ResourceQuota>> resQuota
                    = getEndpoint().getKubernetesClient().resourceQuotas().inNamespace(namespaceName);
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                resQuota.withLabel(entry.getKey(), entry.getValue());
            }
            resList = resQuota.list();
        } else {
            MixedOperation<ResourceQuota, ResourceQuotaList, Resource<ResourceQuota>> resQuota
                    = getEndpoint().getKubernetesClient().resourceQuotas();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                resQuota.withLabel(entry.getKey(), entry.getValue());
            }
            resList = resQuota.list();
        }

        prepareOutboundMessage(exchange, resList.getItems());
    }

    protected void doGetResourceQuota(Exchange exchange) {
        ResourceQuota rq = null;
        String rqName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rqName)) {
            LOG.error("Get a specific Resource Quota require specify a Resource Quota name");
            throw new IllegalArgumentException("Get a specific Resource Quota require specify a Resource Quota name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific Resource Quota require specify a namespace name");
            throw new IllegalArgumentException("Get a specific Resource Quota require specify a namespace name");
        }
        rq = getEndpoint().getKubernetesClient().resourceQuotas().inNamespace(namespaceName).withName(rqName).get();

        prepareOutboundMessage(exchange, rq);
    }

    protected void doCreateResourceQuota(Exchange exchange) {
        ResourceQuota rq = null;
        String rqName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ResourceQuotaSpec rqSpec
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCE_QUOTA_SPEC, ResourceQuotaSpec.class);
        if (ObjectHelper.isEmpty(rqName)) {
            LOG.error("Create a specific resource quota require specify a resource quota name");
            throw new IllegalArgumentException("Create a specific resource quota require specify a resource quota name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific resource quota require specify a namespace name");
            throw new IllegalArgumentException("Create a specific resource quota require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(rqSpec)) {
            LOG.error("Create a specific resource quota require specify a resource quota spec bean");
            throw new IllegalArgumentException("Create a specific resource quota require specify a resource quota spec bean");
        }
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_LABELS, Map.class);
        ResourceQuota rqCreating = new ResourceQuotaBuilder().withNewMetadata().withName(rqName).withLabels(labels)
                .endMetadata().withSpec(rqSpec).build();
        rq = getEndpoint().getKubernetesClient().resourceQuotas().inNamespace(namespaceName).create(rqCreating);

        prepareOutboundMessage(exchange, rq);
    }

    protected void doDeleteResourceQuota(Exchange exchange) {
        String rqName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_RESOURCES_QUOTA_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(rqName)) {
            LOG.error("Delete a specific resource quota require specify a resource quota name");
            throw new IllegalArgumentException("Delete a specific resource quota require specify a resource quota name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific resource quota require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific resource quota require specify a namespace name");
        }
        boolean rqDeleted
                = getEndpoint().getKubernetesClient().resourceQuotas().inNamespace(namespaceName).withName(rqName).delete();

        prepareOutboundMessage(exchange, rqDeleted);
    }
}
