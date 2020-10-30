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
package org.apache.camel.component.kubernetes.customresources;

import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesCustomResourcesProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesCustomResourcesProducer.class);

    public KubernetesCustomResourcesProducer(AbstractKubernetesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public AbstractKubernetesEndpoint getEndpoint() {
        return (AbstractKubernetesEndpoint) super.getEndpoint();
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

            case KubernetesOperations.LIST_CUSTOMRESOURCES:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_CUSTOMRESOURCES_BY_LABELS_OPERATION:
                doListByLabels(exchange, operation);
                break;

            case KubernetesOperations.GET_CUSTOMRESOURCE:
                doGet(exchange, operation);
                break;

            case KubernetesOperations.DELETE_CUSTOMRESOURCE:
                doDelete(exchange, operation);
                break;

            case KubernetesOperations.CREATE_CUSTOMRESOURCE:
                doCreate(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        JsonObject customResourcesListJSON = new JsonObject(
                getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).list(namespaceName));
        LOG.info(customResourcesListJSON.toString());
        JsonArray customResourcesListItems = new JsonArray(customResourcesListJSON.getCollection("items"));

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourcesListItems);
    }

    protected void doListByLabels(Exchange exchange, String operation) throws Exception {
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_LABELS, Map.class);
        JsonObject customResourcesListJSON = new JsonObject(
                getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).list(namespaceName));
        LOG.info(customResourcesListJSON.toString());
        JsonArray customResourcesListItems = new JsonArray(customResourcesListJSON.getCollection("items"));

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourcesListItems);
    }

    protected void doGet(Exchange exchange, String operation) throws Exception {
        String customResourceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(customResourceName)) {
            LOG.error("Get a specific Deployment require specify a Deployment name");
            throw new IllegalArgumentException("Get a specific Deployment require specify a Deployment name");
        }
        JsonObject customResourceJSON = new JsonObject();
        try {
            customResourceJSON = new JsonObject(
                    getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).get(namespaceName,
                            customResourceName));
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                LOG.info("Custom resource instance not found", e);
            } else {
                throw e;
            }
        }
        LOG.info(customResourceJSON.toString());

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourceJSON);
    }

    protected void doDelete(Exchange exchange, String operation) throws Exception {
        String customResourceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(customResourceName)) {
            LOG.error("Delete a specific deployment require specify a deployment name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a deployment name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific deployment require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a namespace name");
        }

        JsonObject customResourceJSON = new JsonObject();
        try {
            customResourceJSON = new JsonObject(
                    getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).delete(namespaceName,
                            customResourceName));
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                LOG.info("Custom resource instance not found", e);
            } else {
                throw e;
            }
        }

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourceJSON);
    }

    protected void doCreate(Exchange exchange, String operation) throws Exception {
        String customResourceInstance = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);

        JsonObject gitHubSourceJSON = new JsonObject();
        try {
            gitHubSourceJSON = new JsonObject(
                    getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).create(namespaceName,
                            customResourceInstance));
        } catch (KubernetesClientException e) {
            if (e.getCode() == 409) {
                LOG.info("Custom resoure instance already exists", e);
            } else {
                throw e;
            }
        }
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(gitHubSourceJSON);
    }

    private CustomResourceDefinitionContext getCRDContext(Message message) {
        CustomResourceDefinitionContext cRDContext = new CustomResourceDefinitionContext.Builder()
                .withName(message.getHeader(KubernetesConstants.KUBERNETES_CRD_NAME, String.class))       // example: "githubsources.sources.knative.dev"
                .withGroup(message.getHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, String.class))     // example: "sources.knative.dev"
                .withScope(message.getHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, String.class))     // example: "Namespaced"
                .withVersion(message.getHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, String.class)) // example: "v1alpha1"
                .withPlural(message.getHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, String.class))   // example: "githubsources"
                .build();
        return cRDContext;
    }

}
