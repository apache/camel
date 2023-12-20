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

public final class KubernetesOperations {

    // Namespaces
    public static final String LIST_NAMESPACE_OPERATION = "listNamespaces";
    public static final String LIST_NAMESPACE_BY_LABELS_OPERATION = "listNamespacesByLabels";
    public static final String GET_NAMESPACE_OPERATION = "getNamespace";
    public static final String CREATE_NAMESPACE_OPERATION = "createNamespace";
    public static final String UPDATE_NAMESPACE_OPERATION = "updateNamespace";
    public static final String DELETE_NAMESPACE_OPERATION = "deleteNamespace";

    // Services
    public static final String LIST_SERVICES_OPERATION = "listServices";
    public static final String LIST_SERVICES_BY_LABELS_OPERATION = "listServicesByLabels";
    public static final String GET_SERVICE_OPERATION = "getService";
    public static final String CREATE_SERVICE_OPERATION = "createService";
    public static final String UPDATE_SERVICE_OPERATION = "updateService";
    public static final String DELETE_SERVICE_OPERATION = "deleteService";

    // Replication Controllers
    public static final String LIST_REPLICATION_CONTROLLERS_OPERATION = "listReplicationControllers";
    public static final String LIST_REPLICATION_CONTROLLERS_BY_LABELS_OPERATION = "listReplicationControllersByLabels";
    public static final String GET_REPLICATION_CONTROLLER_OPERATION = "getReplicationController";
    public static final String CREATE_REPLICATION_CONTROLLER_OPERATION = "createReplicationController";
    public static final String UPDATE_REPLICATION_CONTROLLER_OPERATION = "updateReplicationController";
    public static final String DELETE_REPLICATION_CONTROLLER_OPERATION = "deleteReplicationController";
    public static final String SCALE_REPLICATION_CONTROLLER_OPERATION = "scaleReplicationController";

    // Pods
    public static final String LIST_PODS_OPERATION = "listPods";
    public static final String LIST_PODS_BY_LABELS_OPERATION = "listPodsByLabels";
    public static final String GET_POD_OPERATION = "getPod";
    public static final String CREATE_POD_OPERATION = "createPod";
    public static final String UPDATE_POD_OPERATION = "updatePod";
    public static final String DELETE_POD_OPERATION = "deletePod";

    // Persistent Volumes
    public static final String LIST_PERSISTENT_VOLUMES = "listPersistentVolumes";
    public static final String LIST_PERSISTENT_VOLUMES_BY_LABELS_OPERATION = "listPersistentVolumesByLabels";
    public static final String GET_PERSISTENT_VOLUME_OPERATION = "getPersistentVolume";

    // Persistent Volumes Claims
    public static final String LIST_PERSISTENT_VOLUMES_CLAIMS = "listPersistentVolumesClaims";
    public static final String LIST_PERSISTENT_VOLUMES_CLAIMS_BY_LABELS_OPERATION = "listPersistentVolumesClaimsByLabels";
    public static final String GET_PERSISTENT_VOLUME_CLAIM_OPERATION = "getPersistentVolumeClaim";
    public static final String CREATE_PERSISTENT_VOLUME_CLAIM_OPERATION = "createPersistentVolumeClaim";
    public static final String UPDATE_PERSISTENT_VOLUME_CLAIM_OPERATION = "updatePersistentVolumeClaim";
    public static final String DELETE_PERSISTENT_VOLUME_CLAIM_OPERATION = "deletePersistentVolumeClaim";

    // Secrets
    public static final String LIST_SECRETS = "listSecrets";
    public static final String LIST_SECRETS_BY_LABELS_OPERATION = "listSecretsByLabels";
    public static final String GET_SECRET_OPERATION = "getSecret";
    public static final String CREATE_SECRET_OPERATION = "createSecret";
    public static final String UPDATE_SECRET_OPERATION = "updateSecret";
    public static final String DELETE_SECRET_OPERATION = "deleteSecret";

    // Resources quota
    public static final String LIST_RESOURCES_QUOTA = "listResourcesQuota";
    public static final String LIST_RESOURCES_QUOTA_BY_LABELS_OPERATION = "listResourcesQuotaByLabels";
    public static final String GET_RESOURCE_QUOTA_OPERATION = "getResourceQuota";
    public static final String CREATE_RESOURCE_QUOTA_OPERATION = "createResourceQuota";
    public static final String UPDATE_RESOURCE_QUOTA_OPERATION = "updateResourceQuota";
    public static final String DELETE_RESOURCE_QUOTA_OPERATION = "deleteResourceQuota";

    // Service Accounts
    public static final String LIST_SERVICE_ACCOUNTS = "listServiceAccounts";
    public static final String LIST_SERVICE_ACCOUNTS_BY_LABELS_OPERATION = "listServiceAccountsByLabels";
    public static final String GET_SERVICE_ACCOUNT_OPERATION = "getServiceAccount";
    public static final String CREATE_SERVICE_ACCOUNT_OPERATION = "createServiceAccount";
    public static final String UPDATE_SERVICE_ACCOUNT_OPERATION = "updateServiceAccount";
    public static final String DELETE_SERVICE_ACCOUNT_OPERATION = "deleteServiceAccount";

    // Nodes
    public static final String LIST_NODES = "listNodes";
    public static final String LIST_NODES_BY_LABELS_OPERATION = "listNodesByLabels";
    public static final String GET_NODE_OPERATION = "getNode";
    public static final String CREATE_NODE_OPERATION = "createNode";
    public static final String UPDATE_NODE_OPERATION = "updateNode";
    public static final String DELETE_NODE_OPERATION = "deleteNode";

    // HPA
    public static final String LIST_HPA = "listHPA";
    public static final String LIST_HPA_BY_LABELS_OPERATION = "listHPAByLabels";
    public static final String GET_HPA_OPERATION = "getHPA";
    public static final String CREATE_HPA_OPERATION = "createHPA";
    public static final String UPDATE_HPA_OPERATION = "updateHPA";
    public static final String DELETE_HPA_OPERATION = "deleteHPA";

    // Deployments
    public static final String LIST_DEPLOYMENTS = "listDeployments";
    public static final String LIST_DEPLOYMENTS_BY_LABELS_OPERATION = "listDeploymentsByLabels";
    public static final String GET_DEPLOYMENT = "getDeployment";
    public static final String DELETE_DEPLOYMENT = "deleteDeployment";
    public static final String CREATE_DEPLOYMENT = "createDeployment";
    public static final String UPDATE_DEPLOYMENT = "updateDeployment";
    public static final String SCALE_DEPLOYMENT = "scaleDeployment";

    // Config Maps
    public static final String LIST_CONFIGMAPS = "listConfigMaps";
    public static final String LIST_CONFIGMAPS_BY_LABELS_OPERATION = "listConfigMapsByLabels";
    public static final String GET_CONFIGMAP_OPERATION = "getConfigMap";
    public static final String CREATE_CONFIGMAP_OPERATION = "createConfigMap";
    public static final String DELETE_CONFIGMAP_OPERATION = "deleteConfigMap";
    public static final String UPDATE_CONFIGMAP_OPERATION = "updateConfigMap";

    // Events
    public static final String LIST_EVENTS_OPERATION = "listEvents";
    public static final String LIST_EVENTS_BY_LABELS_OPERATION = "listEventsByLabels";
    public static final String GET_EVENT_OPERATION = "getEvent";
    public static final String CREATE_EVENT_OPERATION = "createEvent";
    public static final String DELETE_EVENT_OPERATION = "deleteEvent";
    public static final String UPDATE_EVENT_OPERATION = "updateEvent";

    // Builds
    public static final String LIST_BUILD = "listBuilds";
    public static final String LIST_BUILD_BY_LABELS_OPERATION = "listBuildsByLabels";
    public static final String GET_BUILD_OPERATION = "getBuild";

    // Build Configs
    public static final String LIST_BUILD_CONFIGS = "listBuildConfigs";
    public static final String LIST_BUILD_CONFIGS_BY_LABELS_OPERATION = "listBuildConfigsByLabels";
    public static final String GET_BUILD_CONFIG_OPERATION = "getBuildConfig";

    // Cronjob
    public static final String LIST_CRON_JOB_OPERATION = "listCronJob";
    public static final String LIST_CRON_JOB_BY_LABELS_OPERATION = "listCronJobByLabels";
    public static final String GET_CRON_JOB_OPERATION = "getCronJob";
    public static final String CREATE_CRON_JOB_OPERATION = "createCronJob";
    public static final String UPDATE_CRON_JOB_OPERATION = "updateCronJob";
    public static final String DELETE_CRON_JOB_OPERATION = "deleteCronJob";

    // Job
    public static final String LIST_JOB = "listJob";
    public static final String LIST_JOB_BY_LABELS_OPERATION = "listJobByLabels";
    public static final String GET_JOB_OPERATION = "getJob";
    public static final String CREATE_JOB_OPERATION = "createJob";
    public static final String UPDATE_JOB_OPERATION = "updateJob";
    public static final String DELETE_JOB_OPERATION = "deleteJob";

    // Custom Resources
    public static final String LIST_CUSTOMRESOURCES = "listCustomResources";
    public static final String LIST_CUSTOMRESOURCES_BY_LABELS_OPERATION = "listCustomResourcesByLabels";
    public static final String GET_CUSTOMRESOURCE = "getCustomResource";
    public static final String DELETE_CUSTOMRESOURCE = "deleteCustomResource";
    public static final String CREATE_CUSTOMRESOURCE = "createCustomResource";
    public static final String UPDATE_CUSTOMRESOURCE = "updateCustomResource";

    // Deployment Configs
    public static final String LIST_DEPLOYMENT_CONFIGS = "listDeploymentConfigs";
    public static final String LIST_DEPLOYMENT_CONFIGS_BY_LABELS_OPERATION = "listDeploymentConfigsByLabels";
    public static final String GET_DEPLOYMENT_CONFIG = "getDeploymentConfig";
    public static final String DELETE_DEPLOYMENT_CONFIG = "deleteDeploymentConfig";
    public static final String CREATE_DEPLOYMENT_CONFIG = "createDeploymentConfig";
    public static final String UPDATE_DEPLOYMENT_CONFIG = "updateDeploymentConfig";
    public static final String SCALE_DEPLOYMENT_CONFIG = "scaleDeploymentConfig";

    private KubernetesOperations() {

    }
}
