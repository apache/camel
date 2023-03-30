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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
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

public class KubernetesDeploymentsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDeploymentsProducer.class);

    public KubernetesDeploymentsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_DEPLOYMENTS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_DEPLOYMENTS_BY_LABELS_OPERATION:
                doListDeploymentsByLabels(exchange);
                break;

            case KubernetesOperations.GET_DEPLOYMENT:
                doGetDeployment(exchange);
                break;

            case KubernetesOperations.DELETE_DEPLOYMENT:
                doDeleteDeployment(exchange);
                break;

            case KubernetesOperations.CREATE_DEPLOYMENT:
                doCreateDeployment(exchange);
                break;

            case KubernetesOperations.UPDATE_DEPLOYMENT:
                doUpdateDeployment(exchange);
                break;

            case KubernetesOperations.SCALE_DEPLOYMENT:
                doScaleDeployment(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        DeploymentList deploymentsList = getEndpoint().getKubernetesClient().apps().deployments().list();

        prepareOutboundMessage(exchange, deploymentsList.getItems());
    }

    protected void doListDeploymentsByLabels(Exchange exchange) {
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = getEndpoint()
                .getKubernetesClient().apps().deployments();

        DeploymentList deploymentList = deployments.withLabels(labels).list();

        prepareOutboundMessage(exchange, deploymentList.getItems());
    }

    protected void doGetDeployment(Exchange exchange) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Get a specific Deployment require specify a Deployment name");
            throw new IllegalArgumentException("Get a specific Deployment require specify a Deployment name");
        }
        Deployment deployment = getEndpoint().getKubernetesClient().apps().deployments().withName(deploymentName).get();

        prepareOutboundMessage(exchange, deployment);
    }

    protected void doDeleteDeployment(Exchange exchange) {
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

        List<StatusDetails> statusDetails = getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName)
                .withName(deploymentName).delete();
        boolean deploymentDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, deploymentDeleted);
    }

    protected void doUpdateDeployment(Exchange exchange) {
        doCreateOrUpdateDeployment(exchange, "Update", Resource::update);
    }

    protected void doCreateDeployment(Exchange exchange) {
        doCreateOrUpdateDeployment(exchange, "Create", Resource::create);
    }

    private void doCreateOrUpdateDeployment(
            Exchange exchange, String operationName, Function<Resource<Deployment>, Deployment> operation) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        DeploymentSpec deSpec
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_SPEC, DeploymentSpec.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("{} a specific Deployment require specify a Deployment name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Deployment require specify a pod name", operationName));
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("{} a specific Deployment require specify a namespace name", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Deployment require specify a namespace name", operationName));
        }
        if (ObjectHelper.isEmpty(deSpec)) {
            LOG.error("{} a specific Deployment require specify a Deployment spec bean", operationName);
            throw new IllegalArgumentException(
                    String.format("%s a specific Deployment require specify a Deployment spec bean", operationName));
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        Deployment deploymentCreating = new DeploymentBuilder().withNewMetadata().withName(deploymentName).withLabels(labels)
                .endMetadata().withSpec(deSpec).build();
        Deployment deployment
                = operation.apply(getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName)
                        .resource(deploymentCreating));

        prepareOutboundMessage(exchange, deployment);
    }

    protected void doScaleDeployment(Exchange exchange) {
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
        Deployment deploymentScaled = getEndpoint().getKubernetesClient().apps().deployments().inNamespace(namespaceName)
                .withName(deploymentName).scale(replicasNumber, false);

        prepareOutboundMessage(exchange, deploymentScaled.getStatus().getReplicas());
    }
}
