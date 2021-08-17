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
package org.apache.camel.component.kubernetes.services;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceSpec;
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

public class KubernetesServicesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesServicesProducer.class);

    public KubernetesServicesProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_SERVICES_OPERATION:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_SERVICES_BY_LABELS_OPERATION:
                doListServiceByLabels(exchange);
                break;

            case KubernetesOperations.GET_SERVICE_OPERATION:
                doGetService(exchange);
                break;

            case KubernetesOperations.CREATE_SERVICE_OPERATION:
                doCreateService(exchange);
                break;

            case KubernetesOperations.DELETE_SERVICE_OPERATION:
                doDeleteService(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        ServiceList servicesList = null;
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            servicesList = getEndpoint().getKubernetesClient().services().inNamespace(namespaceName).list();
        } else {
            servicesList = getEndpoint().getKubernetesClient().services().inAnyNamespace().list();
        }
        prepareOutboundMessage(exchange, servicesList.getItems());
    }

    protected void doListServiceByLabels(Exchange exchange) {
        ServiceList servicesList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, Map.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (!ObjectHelper.isEmpty(namespaceName)) {
            servicesList = getEndpoint()
                    .getKubernetesClient()
                    .services()
                    .inNamespace(namespaceName)
                    .withLabels(labels)
                    .list();
        } else {
            servicesList = getEndpoint()
                    .getKubernetesClient()
                    .services()
                    .inAnyNamespace()
                    .withLabels(labels)
                    .list();
        }

        prepareOutboundMessage(exchange, servicesList.getItems());
    }

    protected void doGetService(Exchange exchange) {
        Service service = null;
        String serviceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(serviceName)) {
            LOG.error("Get a specific service require specify a service name");
            throw new IllegalArgumentException("Get a specific service require specify a service name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific service require specify a namespace name");
            throw new IllegalArgumentException("Get a specific service require specify a namespace name");
        }
        service = getEndpoint().getKubernetesClient().services().inNamespace(namespaceName).withName(serviceName).get();

        prepareOutboundMessage(exchange, service);
    }

    protected void doCreateService(Exchange exchange) {
        Service service = null;
        String serviceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        ServiceSpec serviceSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_SPEC, ServiceSpec.class);
        if (ObjectHelper.isEmpty(serviceName)) {
            LOG.error("Create a specific service require specify a service name");
            throw new IllegalArgumentException("Create a specific service require specify a service name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific service require specify a namespace name");
            throw new IllegalArgumentException("Create a specific service require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(serviceSpec)) {
            LOG.error("Create a specific service require specify a service spec bean");
            throw new IllegalArgumentException("Create a specific service require specify a service spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_LABELS, Map.class);
        Service serviceCreating = new ServiceBuilder().withNewMetadata().withName(serviceName).withLabels(labels).endMetadata()
                .withSpec(serviceSpec).build();
        service = getEndpoint().getKubernetesClient().services().inNamespace(namespaceName).create(serviceCreating);

        prepareOutboundMessage(exchange, service);
    }

    protected void doDeleteService(Exchange exchange) {
        String serviceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_SERVICE_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(serviceName)) {
            LOG.error("Delete a specific service require specify a service name");
            throw new IllegalArgumentException("Delete a specific service require specify a service name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific service require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific service require specify a namespace name");
        }
        boolean serviceDeleted
                = getEndpoint().getKubernetesClient().services().inNamespace(namespaceName).withName(serviceName).delete();

        prepareOutboundMessage(exchange, serviceDeleted);
    }
}
