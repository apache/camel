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
package org.apache.camel.component.openshift.deploymentconfigs;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigList;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.client.OpenShiftClient;
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

public class OpenshiftDeploymentConfigsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftDeploymentConfigsProducer.class);

    public OpenshiftDeploymentConfigsProducer(AbstractKubernetesEndpoint endpoint) {
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

            case KubernetesOperations.LIST_DEPLOYMENT_CONFIGS:
                doList(exchange);
                break;

            case KubernetesOperations.LIST_DEPLOYMENT_CONFIGS_BY_LABELS_OPERATION:
                doListDeploymentConfigsByLabels(exchange);
                break;

            case KubernetesOperations.GET_DEPLOYMENT_CONFIG:
                doGetDeploymentConfig(exchange);
                break;

            case KubernetesOperations.DELETE_DEPLOYMENT_CONFIG:
                doDeleteDeploymentConfig(exchange);
                break;

            case KubernetesOperations.CREATE_DEPLOYMENT_CONFIG:
                doCreateDeployment(exchange);
                break;

            case KubernetesOperations.SCALE_DEPLOYMENT_CONFIG:
                doScaleDeploymentConfig(exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doList(Exchange exchange) {
        DeploymentConfigList deploymentConfigList
                = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs().list();
        prepareOutboundMessage(exchange, deploymentConfigList.getItems());
    }

    protected void doListDeploymentConfigsByLabels(Exchange exchange) {
        Map<String, String> labels
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        DeploymentConfigList deploymentConfigList
                = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs()
                        .withLabels(labels).list();

        prepareOutboundMessage(exchange, deploymentConfigList.getItems());
    }

    protected void doGetDeploymentConfig(Exchange exchange) {
        String deploymentConfigName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        if (ObjectHelper.isEmpty(deploymentConfigName)) {
            LOG.error("Get a specific Deployment Config require specify a Deployment name");
            throw new IllegalArgumentException("Get a specific Deployment Config require specify a Deployment Config name");
        }
        DeploymentConfig deploymentConfig = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs()
                .withName(deploymentConfigName).get();

        prepareOutboundMessage(exchange, deploymentConfig);
    }

    protected void doDeleteDeploymentConfig(Exchange exchange) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Delete a specific deployment config require specify a deployment name");
            throw new IllegalArgumentException("Delete a specific deployment require specify a deployment config name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Delete a specific deployment config require specify a namespace name");
            throw new IllegalArgumentException("Delete a specific deployment config require specify a namespace name");
        }

        List<StatusDetails> statusDetails = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs()
                .inNamespace(namespaceName)
                .withName(deploymentName).delete();
        boolean deploymentConfigDeleted = ObjectHelper.isNotEmpty(statusDetails);

        prepareOutboundMessage(exchange, deploymentConfigDeleted);
    }

    protected void doCreateDeployment(Exchange exchange) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        DeploymentConfigSpec dcSpec
                = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_CONFIG_SPEC, DeploymentConfigSpec.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Create a specific Deployment Config require specify a Deployment name");
            throw new IllegalArgumentException("Create a specific Deployment Config require specify a pod name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Create a specific Deployment Config require specify a namespace name");
            throw new IllegalArgumentException("Create a specific Deployment Config require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(dcSpec)) {
            LOG.error("Create a specific Deployment Config require specify a Deployment Config spec bean");
            throw new IllegalArgumentException(
                    "Create a specific Deployment Config require specify a Deployment Config spec bean");
        }
        Map<String, String> labels = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENTS_LABELS, Map.class);
        DeploymentConfig deploymentCreating = new DeploymentConfigBuilder().withNewMetadata().withName(deploymentName)
                .withLabels(labels).endMetadata().withSpec(dcSpec).build();
        DeploymentConfig deploymentConfig = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs()
                .inNamespace(namespaceName)
                .create(deploymentCreating);

        prepareOutboundMessage(exchange, deploymentConfig);
    }

    protected void doScaleDeploymentConfig(Exchange exchange) {
        String deploymentName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_NAME, String.class);
        String namespaceName = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, String.class);
        Integer replicasNumber = exchange.getIn().getHeader(KubernetesConstants.KUBERNETES_DEPLOYMENT_REPLICAS, Integer.class);
        if (ObjectHelper.isEmpty(deploymentName)) {
            LOG.error("Scale a specific deployment config require specify a deployment config name");
            throw new IllegalArgumentException("Scale a specific deployment config require specify a deployment config name");
        }
        if (ObjectHelper.isEmpty(namespaceName)) {
            LOG.error("Scale a specific deployment config require specify a namespace name");
            throw new IllegalArgumentException("Scale a specific deployment config require specify a namespace name");
        }
        if (ObjectHelper.isEmpty(replicasNumber)) {
            LOG.error("Scale a specific deployment config require specify a replicas number");
            throw new IllegalArgumentException("Scale a specific deployment config require specify a replicas number");
        }
        DeploymentConfig deploymentConfigScaled
                = getEndpoint().getKubernetesClient().adapt(OpenShiftClient.class).deploymentConfigs()
                        .inNamespace(namespaceName)
                        .withName(deploymentName).scale(replicasNumber, false);

        prepareOutboundMessage(exchange, deploymentConfigScaled.getStatus().getReplicas());
    }
}
