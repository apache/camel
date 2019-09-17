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

public interface KubernetesOperations {

    // Namespaces
    String LIST_NAMESPACE_OPERATION = "listNamespaces";
    String LIST_NAMESPACE_BY_LABELS_OPERATION = "listNamespacesByLabels";
    String GET_NAMESPACE_OPERATION = "getNamespace";
    String CREATE_NAMESPACE_OPERATION = "createNamespace";
    String DELETE_NAMESPACE_OPERATION = "deleteNamespace";

    // Services
    String LIST_SERVICES_OPERATION = "listServices";
    String LIST_SERVICES_BY_LABELS_OPERATION = "listServicesByLabels";
    String GET_SERVICE_OPERATION = "getService";
    String CREATE_SERVICE_OPERATION = "createService";
    String DELETE_SERVICE_OPERATION = "deleteService";

    // Replication Controllers
    String LIST_REPLICATION_CONTROLLERS_OPERATION = "listReplicationControllers";
    String LIST_REPLICATION_CONTROLLERS_BY_LABELS_OPERATION = "listReplicationControllersByLabels";
    String GET_REPLICATION_CONTROLLER_OPERATION = "getReplicationController";
    String CREATE_REPLICATION_CONTROLLER_OPERATION = "createReplicationController";
    String DELETE_REPLICATION_CONTROLLER_OPERATION = "deleteReplicationController";
    String SCALE_REPLICATION_CONTROLLER_OPERATION = "scaleReplicationController";

    // Pods
    String LIST_PODS_OPERATION = "listPods";
    String LIST_PODS_BY_LABELS_OPERATION = "listPodsByLabels";
    String GET_POD_OPERATION = "getPod";
    String CREATE_POD_OPERATION = "createPod";
    String DELETE_POD_OPERATION = "deletePod";

    // Persistent Volumes
    String LIST_PERSISTENT_VOLUMES = "listPersistentVolumes";
    String LIST_PERSISTENT_VOLUMES_BY_LABELS_OPERATION = "listPersistentVolumesByLabels";
    String GET_PERSISTENT_VOLUME_OPERATION = "getPersistentVolume";

    // Persistent Volumes Claims
    String LIST_PERSISTENT_VOLUMES_CLAIMS = "listPersistentVolumesClaims";
    String LIST_PERSISTENT_VOLUMES_CLAIMS_BY_LABELS_OPERATION = "listPersistentVolumesClaimsByLabels";
    String GET_PERSISTENT_VOLUME_CLAIM_OPERATION = "getPersistentVolumeClaim";
    String CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION = "createPersistentVolumeClaim";
    String DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION = "deletePersistentVolumeClaim";

    // Secrets
    String LIST_SECRETS = "listSecrets";
    String LIST_SECRETS_BY_LABELS_OPERATION = "listSecretsByLabels";
    String GET_SECRET_OPERATION = "getSecret";
    String CREATE_SECRET_OPERATION = "createSecret";
    String DELETE_SECRET_OPERATION = "deleteSecret";

    // Resources quota
    String LIST_RESOURCES_QUOTA = "listResourcesQuota";
    String LIST_RESOURCES_QUOTA_BY_LABELS_OPERATION = "listResourcesQuotaByLabels";
    String GET_RESOURCE_QUOTA_OPERATION = "getResourceQuota";
    String CREATE_RESOURCE_QUOTA_OPERATION = "createResourceQuota";
    String DELETE_RESOURCE_QUOTA_OPERATION = "deleteResourceQuota";

    // Service Accounts
    String LIST_SERVICE_ACCOUNTS = "listServiceAccounts";
    String LIST_SERVICE_ACCOUNTS_BY_LABELS_OPERATION = "listServiceAccountsByLabels";
    String GET_SERVICE_ACCOUNT_OPERATION = "getServiceAccount";
    String CREATE_SERVICE_ACCOUNT_OPERATION = "createServiceAccount";
    String DELETE_SERVICE_ACCOUNT_OPERATION = "deleteServiceAccount";

    // Nodes
    String LIST_NODES = "listNodes";
    String LIST_NODES_BY_LABELS_OPERATION = "listNodesByLabels";
    String GET_NODE_OPERATION = "getNode";
    String CREATE_NODE_OPERATION = "createNode";
    String DELETE_NODE_OPERATION = "deleteNode";
    
    // HPA
    String LIST_HPA = "listHPA";
    String LIST_HPA_BY_LABELS_OPERATION = "listHPAByLabels";
    String GET_HPA_OPERATION = "getHPA";
    String CREATE_HPA_OPERATION = "createHPA";
    String DELETE_HPA_OPERATION = "deleteHPA";

    // Deployments
    String LIST_DEPLOYMENTS = "listDeployments";
    String LIST_DEPLOYMENTS_BY_LABELS_OPERATION = "listDeploymentsByLabels";
    String GET_DEPLOYMENT = "getDeployment";
    String DELETE_DEPLOYMENT = "deleteDeployment";
    String CREATE_DEPLOYMENT = "createDeployment";
    String SCALE_DEPLOYMENT = "scaleDeployment";

    // Config Maps
    String LIST_CONFIGMAPS = "listConfigMaps";
    String LIST_CONFIGMAPS_BY_LABELS_OPERATION = "listConfigMapsByLabels";
    String GET_CONFIGMAP_OPERATION = "getConfigMap";
    String CREATE_CONFIGMAP_OPERATION = "createConfigMap";
    String DELETE_CONFIGMAP_OPERATION = "deleteConfigMap";

    // Builds
    String LIST_BUILD = "listBuilds";
    String LIST_BUILD_BY_LABELS_OPERATION = "listBuildsByLabels";
    String GET_BUILD_OPERATION = "getBuild";

    // Build Configs
    String LIST_BUILD_CONFIGS = "listBuildConfigs";
    String LIST_BUILD_CONFIGS_BY_LABELS_OPERATION = "listBuildConfigsByLabels";
    String GET_BUILD_CONFIG_OPERATION = "getBuildConfig";

    // Secrets
    String LIST_JOB = "listJob";
    String LIST_JOB_BY_LABELS_OPERATION = "listJobByLabels";
    String GET_JOB_OPERATION = "getJob";
    String CREATE_JOB_OPERATION = "createJob";
    String DELETE_JOB_OPERATION = "deleteJob";
}
