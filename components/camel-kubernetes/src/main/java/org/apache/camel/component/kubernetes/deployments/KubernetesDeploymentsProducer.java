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
package org.apache.camel.component.kubernetes.deployments;

import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDeploymentsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDeploymentsProducer.class);

    public KubernetesDeploymentsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_DEPLOYMENTS:
                doList(exchange, operation);
                break;

            case KubernetesOperations.LIST_DEPLOYMENTS_BY_LABELS_OPERATION:
                doListDeploymentsByLabels(exchange, operation);
                break;

            case KubernetesOperations.GET_DEPLOYMENT:
                doGetDeployment(exchange, operation);
                break;

            case KubernetesOperations.DELETE_DEPLOYMENT:
                doDeleteDeployment(exchange, operation);
                break;

            case KubernetesOperations.CREATE_DEPLOYMENT:
                doCreateDeployment(exchange, operation);
                break;

            case KubernetesOperations.SCALE_DEPLOYMENT:
                doScaleDeployment(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange, String operation) throws Exception {
        DeploymentList deploymentsList = getEndpoint().getKubernetesClient().apps().deployments().list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deploymentsList.getItems());
    }

    protected void doListDeploymentsByLabels(Exchange exchange, String operation) throws Exception {
        DeploymentList deploymentList = null;
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        NonNamespaceOperation<Deployment, DeploymentList, DoneableDeployment, RollableScalableResource<Deployment, DoneableDeployment>> deployments = getEndpoint()
                .getKubernetesClient().apps().deployments();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            deployments.withLabel(entry.getKey(), entry.getValue());
        }
        deploymentList = deployments.list();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deploymentList.getItems());
    }

    protected void doGetDeployment(Exchange exchange, String operation) throws Exception {
        Deployment deployment = null;
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Get a specific Deployment require specify a Deployment name");
            throw new IllegalArgumentException("Get a specific Deployment require specify a Deployment name");
        }
        deployment = getEndpoint().getKubernetesClient().apps().deployments().withName(deploymentName).get();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deployment);
    }

    protected void doDeleteDeployment(Exchange exchange, String operation) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Delete a specific deployment require specify a deployment name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a deployment name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific deployment require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a namespace name");
        }

        Boolean deployment = getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName).withName(deploymentName).delete();

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deployment);
    }

    protected void doCreateDeployment(Exchange exchange, String operation) throws Exception {
        Deployment deployment = null;
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        DeploymentSpec deSpec = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_SPEC, DeploymentSpec.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Create a specific Deployment require specify a Deployment name");
            throw new IllegalArgumentException("Create a specific Deployment require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific Deployment require specify a namespace name");
            throw new IllegalArgumentException("Create a specific Deployment require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(deSpec)) {
            LOG.error("Create a specific Deployment require specify a Deployment spec bean");
            throw new IllegalArgumentException("Create a specific Deployment require specify a Deployment spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        Deployment deploymentCreating = new DeploymentBuilder().withNewMetadata().withName(deploymentName).withLabels(labels).endMetadata().withSpec(deSpec).build();
        deployment = getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName).create(deploymentCreating);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deployment);
    }

    protected void doScaleDeployment(Exchange exchange, String operation) throws Exception {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Integer replicasNumber = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, Integer.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Scale a specific deployment require specify a deployment name");
            throw new IllegalArgumentException("Scale a specific deployment require specify a deployment name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Scale a specific deployment require specify a namespace name");
            throw new IllegalArgumentException("Scale a specific deployment require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(replicasNumber)) {
            LOG.error("Scale a specific deployment require specify a replicas number");
            throw new IllegalArgumentException("Scale a specific deployment require specify a replicas number");
        }
        Deployment deploymentScaled = getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName).withName(deploymentName).scale(replicasNumber, false);

        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), true);
        exchange.getOut().setBody(deploymentScaled.getStatus().getReplicas());
    }
}
