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
package org.apache.camel.component.kubernetes;

public final class KubernetesConstants {
    // Producer
    public static final String KUBERNETES_OPERATION = "CamelKubernetesOperation";
    public static final String KUBERNETES_NAMESPACE_NAME = "CamelKubernetesNamespaceName";
    public static final String KUBERNETES_NAMESPACE_LABELS = "CamelKubernetesNamespaceLabels";
    public static final String KUBERNETES_SERVICE_LABELS = "CamelKubernetesServiceLabels";
    public static final String KUBERNETES_SERVICE_NAME = "CamelKubernetesServiceName";
    public static final String KUBERNETES_SERVICE_SPEC = "CamelKubernetesServiceSpec";
    public static final String KUBERNETES_REPLICATION_CONTROLLERS_LABELS = "CamelKubernetesReplicationControllersLabels";
    public static final String KUBERNETES_REPLICATION_CONTROLLER_NAME = "CamelKubernetesReplicationControllerName";
    public static final String KUBERNETES_REPLICATION_CONTROLLER_SPEC = "CamelKubernetesReplicationControllerSpec";
    public static final String KUBERNETES_REPLICATION_CONTROLLER_REPLICAS = "CamelKubernetesReplicationControllerReplicas";
    public static final String KUBERNETES_PODS_LABELS = "CamelKubernetesPodsLabels";
    public static final String KUBERNETES_POD_NAME = "CamelKubernetesPodName";
    public static final String KUBERNETES_POD_SPEC = "CamelKubernetesPodSpec";
    public static final String KUBERNETES_PERSISTENT_VOLUMES_LABELS = "CamelKubernetesPersistentVolumesLabels";
    public static final String KUBERNETES_PERSISTENT_VOLUME_NAME = "CamelKubernetesPersistentVolumeName";
    public static final String KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS = "CamelKubernetesPersistentVolumesClaimsLabels";
    public static final String KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME = "CamelKubernetesPersistentVolumeClaimName";
    public static final String KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC = "CamelKubernetesPersistentVolumeClaimSpec";
    public static final String KUBERNETES_SECRETS_LABELS = "CamelKubernetesSecretsLabels";
    public static final String KUBERNETES_SECRET_NAME = "CamelKubernetesSecretName";
    public static final String KUBERNETES_SECRET = "CamelKubernetesSecret";
    public static final String KUBERNETES_RESOURCES_QUOTA_LABELS = "CamelKubernetesResourcesQuotaLabels";
    public static final String KUBERNETES_RESOURCES_QUOTA_NAME = "CamelKubernetesResourcesQuotaName";
    public static final String KUBERNETES_RESOURCE_QUOTA_SPEC = "CamelKubernetesResourceQuotaSpec";
    public static final String KUBERNETES_SERVICE_ACCOUNTS_LABELS = "CamelKubernetesServiceAccountsLabels";
    public static final String KUBERNETES_SERVICE_ACCOUNT_NAME = "CamelKubernetesServiceAccountName";
    public static final String KUBERNETES_SERVICE_ACCOUNT = "CamelKubernetesServiceAccount";
    public static final String KUBERNETES_NODES_LABELS = "CamelKubernetesNodesLabels";
    public static final String KUBERNETES_NODE_NAME = "CamelKubernetesNodeName";
    public static final String KUBERNETES_NODE_SPEC = "CamelKubernetesNodeSpec";
    public static final String KUBERNETES_DEPLOYMENTS_LABELS = "CamelKubernetesDeploymentsLabels";
    public static final String KUBERNETES_DEPLOYMENT_NAME = "CamelKubernetesDeploymentName";
    public static final String KUBERNETES_DEPLOYMENT_SPEC = "CamelKubernetesDeploymentSpec";
    public static final String KUBERNETES_CONFIGMAPS_LABELS = "CamelKubernetesConfigMapsLabels";
    public static final String KUBERNETES_CONFIGMAP_NAME = "CamelKubernetesConfigMapName";
    public static final String KUBERNETES_CONFIGMAP_DATA = "CamelKubernetesConfigData";
    public static final String KUBERNETES_BUILDS_LABELS = "CamelKubernetesBuildsLabels";
    public static final String KUBERNETES_BUILD_NAME = "CamelKubernetesBuildName";
    public static final String KUBERNETES_BUILD_CONFIGS_LABELS = "CamelKubernetesBuildConfigsLabels";
    public static final String KUBERNETES_BUILD_CONFIG_NAME = "CamelKubernetesBuildConfigName";
    public static final String KUBERNETES_DEPLOYMENT_REPLICAS = "CamelKubernetesDeploymentReplicas";
    public static final String KUBERNETES_HPA_NAME = "CamelKubernetesHPAName";
    public static final String KUBERNETES_HPA_SPEC = "CamelKubernetesHPASpec";
    public static final String KUBERNETES_HPA_LABELS = "CamelKubernetesHPALabels";
    public static final String KUBERNETES_JOB_NAME = "CamelKubernetesJobName";
    public static final String KUBERNETES_JOB_SPEC = "CamelKubernetesJobSpec";
    public static final String KUBERNETES_JOB_LABELS = "CamelKubernetesJobLabels";
    public static final String KUBERNETES_CRD_INSTANCE_NAME = "CamelKubernetesCRDInstanceName";
    public static final String KUBERNETES_CRD_EVENT_TIMESTAMP = "CamelKubernetesCRDEventTimestamp";
    public static final String KUBERNETES_CRD_EVENT_ACTION = "CamelKubernetesCRDEventAction";
    public static final String KUBERNETES_CRD_NAME = "CamelKubernetesCRDName";
    public static final String KUBERNETES_CRD_GROUP = "CamelKubernetesCRDGroup";
    public static final String KUBERNETES_CRD_SCOPE = "CamelKubernetesCRDScope";
    public static final String KUBERNETES_CRD_VERSION = "CamelKubernetesCRDVersion";
    public static final String KUBERNETES_CRD_PLURAL = "CamelKubernetesCRDPlural";
    public static final String KUBERNETES_CRD_LABELS = "CamelKubernetesCRDLabels";
    public static final String KUBERNETES_CRD_INSTANCE = "CamelKubernetesCRDInstance";

    public static final String KUBERNETES_DELETE_RESULT = "CamelKubernetesDeleteResult";

    // Consumer
    public static final String KUBERNETES_EVENT_ACTION = "CamelKubernetesEventAction";
    public static final String KUBERNETES_EVENT_TIMESTAMP = "CamelKubernetesEventTimestamp";

    private KubernetesConstants() {

    }
}
