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
package org.apache.camel.component.kubernetes;

public interface KubernetesConstants {
    // Producer
    String KUBERNETES_OPERATION = "CamelKubernetesOperation";
    String KUBERNETES_NAMESPACE_NAME = "CamelKubernetesNamespaceName";
    String KUBERNETES_NAMESPACE_LABELS = "CamelKubernetesNamespaceLabels";
    String KUBERNETES_SERVICE_LABELS = "CamelKubernetesServiceLabels";
    String KUBERNETES_SERVICE_NAME = "CamelKubernetesServiceName";
    String KUBERNETES_SERVICE_SPEC = "CamelKubernetesServiceSpec";
    String KUBERNETES_REPLICATION_CONTROLLERS_LABELS = "CamelKubernetesReplicationControllersLabels";
    String KUBERNETES_REPLICATION_CONTROLLER_NAME = "CamelKubernetesReplicationControllerName";
    String KUBERNETES_REPLICATION_CONTROLLER_SPEC = "CamelKubernetesReplicationControllerSpec";
    String KUBERNETES_REPLICATION_CONTROLLER_REPLICAS = "CamelKubernetesReplicationControllerReplicas";
    String KUBERNETES_PODS_LABELS = "CamelKubernetesPodsLabels";
    String KUBERNETES_POD_NAME = "CamelKubernetesPodName";
    String KUBERNETES_POD_SPEC = "CamelKubernetesPodSpec";
    String KUBERNETES_PERSISTENT_VOLUMES_LABELS = "CamelKubernetesPersistentVolumesLabels";
    String KUBERNETES_PERSISTENT_VOLUME_NAME = "CamelKubernetesPersistentVolumeName";
    String KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS = "CamelKubernetesPersistentVolumesClaimsLabels";
    String KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME = "CamelKubernetesPersistentVolumeClaimName";
    String KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC = "CamelKubernetesPersistentVolumeClaimSpec";
    String KUBERNETES_SECRETS_LABELS = "CamelKubernetesSecretsLabels";
    String KUBERNETES_SECRET_NAME = "CamelKubernetesSecretName";
    String KUBERNETES_SECRET = "CamelKubernetesSecret";
    String KUBERNETES_RESOURCES_QUOTA_LABELS = "CamelKubernetesResourcesQuotaLabels";
    String KUBERNETES_RESOURCES_QUOTA_NAME = "CamelKubernetesResourcesQuotaName";
    String KUBERNETES_RESOURCE_QUOTA_SPEC = "CamelKubernetesResourceQuotaSpec";
    String KUBERNETES_SERVICE_ACCOUNTS_LABELS = "CamelKubernetesServiceAccountsLabels";
    String KUBERNETES_SERVICE_ACCOUNT_NAME = "CamelKubernetesServiceAccountName";
    String KUBERNETES_SERVICE_ACCOUNT = "CamelKubernetesServiceAccount";
    String KUBERNETES_NODES_LABELS = "CamelKubernetesNodesLabels";
    String KUBERNETES_NODE_NAME = "CamelKubernetesNodeName";
    String KUBERNETES_DEPLOYMENTS_LABELS = "CamelKubernetesDeploymentsLabels";
    String KUBERNETES_DEPLOYMENT_NAME = "CamelKubernetesDeploymentName";
    String KUBERNETES_DEPLOYMENT_SPEC = "CamelKubernetesDeploymentSpec";
    String KUBERNETES_CONFIGMAPS_LABELS = "CamelKubernetesConfigMapsLabels";
    String KUBERNETES_CONFIGMAP_NAME = "CamelKubernetesConfigMapName";
    String KUBERNETES_CONFIGMAP_DATA = "CamelKubernetesConfigData";
    String KUBERNETES_BUILDS_LABELS = "CamelKubernetesBuildsLabels";
    String KUBERNETES_BUILD_NAME = "CamelKubernetesBuildName";
    String KUBERNETES_BUILD_CONFIGS_LABELS = "CamelKubernetesBuildConfigsLabels";
    String KUBERNETES_BUILD_CONFIG_NAME = "CamelKubernetesBuildConfigName";

    // Consumer
    String KUBERNETES_EVENT_ACTION = "CamelKubernetesEventAction";
    String KUBERNETES_EVENT_TIMESTAMP = "CamelKubernetesEventTimestamp";
}
