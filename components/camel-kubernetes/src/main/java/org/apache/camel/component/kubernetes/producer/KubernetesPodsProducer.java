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

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EditablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientLoggableResource;
import io.fabric8.kubernetes.client.dsl.ClientMixedOperation;
import io.fabric8.kubernetes.client.dsl.ClientOperation;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesPodsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(KubernetesPodsProducer.class);

    public KubernetesPodsProducer(KubernetesEndpoint endpoint) {
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

        case KubernetesOperations.LIST_PODS_OPERATION:
            doList(exchange, operation);
            break;

        case KubernetesOperations.LIST_PODS_BY_LABELS_OPERATION:
            doListPodsByLabel(exchange, operation);
            break;

        case KubernetesOperations.GET_POD_OPERATION:
            doGetPod(exchange, operation);
            break;

        case KubernetesOperations.CREATE_POD_OPERATION:
            doCreatePod(exchange, operation);
            break;

        case KubernetesOperations.DELETE_POD_OPERATION:
            doDeletePod(exchange, operation);
            break;

        default:
            throw new IllegalArgumentException("Unsupported operation "
                    + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        PodList podList = getEndpoint().getKubernetesClient().pods().list();
        exchange.getOut().setBody(podList.getItems());
    }

    protected void doListPodsByLabel(Exchange exchange, String operation) {
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        if (ObjectHelper.isEmpty(labels)) {
            LOG.error("Get pods by labels require specify a labels set");
            throw new IllegalArgumentException(
                    "Get pods by labels require specify a labels set");
        }
        ClientMixedOperation<Pod, PodList, DoneablePod, ClientLoggableResource<Pod, DoneablePod>> pods;
        pods = getEndpoint().getKubernetesClient().pods();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            pods.withLabel(entry.getKey(), entry.getValue());
        }
        PodList podList = pods.list();
        exchange.getOut().setBody(podList.getItems());
    }

    protected void doGetPod(Exchange exchange, String operation)
            throws Exception {
        Pod pod = null;
        String podName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Get a specific pod require specify a pod name");
            throw new IllegalArgumentException(
                    "Get a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Get a specific pod require specify a namespace name");
            throw new IllegalArgumentException(
                    "Get a specific pod require specify a namespace name");
        }
        pod = getEndpoint().getKubernetesClient().pods()
                .inNamespace(namespaceName).withName(podName).get();
        exchange.getOut().setBody(pod);
    }

    protected void doCreatePod(Exchange exchange, String operation)
            throws Exception {
        Pod pod = null;
        String podName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        PodSpec podSpec = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_POD_SPEC, PodSpec.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Create a specific pod require specify a pod name");
            throw new IllegalArgumentException(
                    "Create a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific pod require specify a namespace name");
            throw new IllegalArgumentException(
                    "Create a specific pod require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(podSpec)) {
            LOG.error("Create a specific pod require specify a pod spec bean");
            throw new IllegalArgumentException(
                    "Create a specific pod require specify a pod spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_PODS_LABELS, Map.class);
        EditablePod podCreating = new PodBuilder().withNewMetadata()
                .withName(podName).withLabels(labels).endMetadata()
                .withSpec(podSpec).build();
        pod = getEndpoint().getKubernetesClient().pods()
                .inNamespace(namespaceName).create(podCreating);
        exchange.getOut().setBody(pod);
    }

    protected void doDeletePod(Exchange exchange, String operation)
            throws Exception {
        String podName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_POD_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(
                KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(podName)) {
            LOG.error("Delete a specific pod require specify a pod name");
            throw new IllegalArgumentException(
                    "Delete a specific pod require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific pod require specify a namespace name");
            throw new IllegalArgumentException(
                    "Delete a specific pod require specify a namespace name");
        }
        boolean podDeleted = getEndpoint().getKubernetesClient().pods()
                .inNamespace(namespaceName).withName(podName).delete();
        exchange.getOut().setBody(podDeleted);
    }
}
