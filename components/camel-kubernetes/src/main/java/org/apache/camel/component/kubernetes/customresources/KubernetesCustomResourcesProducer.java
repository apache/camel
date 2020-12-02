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
        String namespace;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getOperation())) {
            operation = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_OPERATION, String.class);
        } else {
            operation = getEndpoint().getKubernetesConfiguration().getOperation();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getNamespace())) {
            namespace = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        } else {
            namespace = getEndpoint().getKubernetesConfiguration().getNamespace();
        }
        if (ObjectHelper.isEmpty(namespace)) {
            throw new IllegalArgumentException("Custom Resource producer requires a namespace argument");
        }

        switch (operation) {

            case KubernetesOperations.LIST_CUSTOMRESOURCES:
                doList(exchange, operation, namespace);
                break;

            case KubernetesOperations.LIST_CUSTOMRESOURCES_BY_LABELS_OPERATION:
                doListByLabels(exchange, operation, namespace);
                break;

            case KubernetesOperations.GET_CUSTOMRESOURCE:
                doGet(exchange, operation, namespace);
                break;

            case KubernetesOperations.DELETE_CUSTOMRESOURCE:
                doDelete(exchange, operation, namespace);
                break;

            case KubernetesOperations.CREATE_CUSTOMRESOURCE:
                doCreate(exchange, operation, namespace);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation, String namespaceName) throws Exception {
        JsonObject customResourcesListJSON = new JsonObject(
                getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).list(namespaceName));
        LOG.info(customResourcesListJSON.toString());

        JsonArray customResourcesListItems;
        if (customResourcesListJSON.getCollection("items") != null) {
            customResourcesListItems = new JsonArray(customResourcesListJSON.getCollection("items"));
        } else {
            customResourcesListItems = new JsonArray();
        }

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourcesListItems);
    }

    protected void doListByLabels(Exchange exchange, String operation, String namespaceName) throws Exception {
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_LABELS, Map.class);
        JsonObject customResourcesListJSON = new JsonObject(
                getEndpoint().getKubernetesClient().customResource(getCRDContext(exchange.getIn())).list(namespaceName));
        LOG.info(customResourcesListJSON.toString());
        JsonArray customResourcesListItems = new JsonArray(customResourcesListJSON.getCollection("items"));

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(customResourcesListItems);
    }

    protected void doGet(Exchange exchange, String operation, String namespaceName) throws Exception {
        String customResourceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, String.class);
        if (ObjectHelper.isEmpty(customResourceName)) {
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

    protected void doDelete(Exchange exchange, String operation, String namespaceName) throws Exception {
        String customResourceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE_NAME, String.class);
        if (ObjectHelper.isEmpty(customResourceName)) {
            LOG.error("Delete a specific deployment require specify a deployment name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a deployment name");
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

    protected void doCreate(Exchange exchange, String operation, String namespaceName) throws Exception {
        String customResourceInstance = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_CRD_INSTANCE, String.class);
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
        String name;
        String group;
        String scope;
        String version;
        String plural;

        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getCrdName())) {
            name = message.getHeader(KubernetesConstants.KUBERNETES_CRD_NAME, String.class);
        } else {
            name = getEndpoint().getKubernetesConfiguration().getCrdName();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getCrdGroup())) {
            group = message.getHeader(KubernetesConstants.KUBERNETES_CRD_GROUP, String.class);
        } else {
            group = getEndpoint().getKubernetesConfiguration().getCrdGroup();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getCrdScope())) {
            scope = message.getHeader(KubernetesConstants.KUBERNETES_CRD_SCOPE, String.class);
        } else {
            scope = getEndpoint().getKubernetesConfiguration().getCrdScope();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getCrdVersion())) {
            version = message.getHeader(KubernetesConstants.KUBERNETES_CRD_VERSION, String.class);
        } else {
            version = getEndpoint().getKubernetesConfiguration().getCrdVersion();
        }
        if (ObjectHelper.isEmpty(getEndpoint().getKubernetesConfiguration().getCrdPlural())) {
            plural = message.getHeader(KubernetesConstants.KUBERNETES_CRD_PLURAL, String.class);
        } else {
            plural = getEndpoint().getKubernetesConfiguration().getCrdPlural();
        }
        if (ObjectHelper.isEmpty(name) || ObjectHelper.isEmpty(group) || ObjectHelper.isEmpty(scope)
                || ObjectHelper.isEmpty(version) || ObjectHelper.isEmpty(plural)) {
            LOG.error("one of more of the custom resource definition argument(s) are missing.");
            throw new IllegalArgumentException("one of more of the custom resource definition argument(s) are missing.");
        }

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
