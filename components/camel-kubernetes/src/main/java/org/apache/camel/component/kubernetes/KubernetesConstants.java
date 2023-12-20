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

import org.apache.camel.spi.Metadata;

public final class KubernetesConstants {
    // Schemes
    public static final String SCHEME_CONFIG_MAPS = "kubernetes-config-maps";
    public static final String SCHEME_CUSTOM_RESOURCES = "kubernetes-custom-resources";
    public static final String SCHEME_DEPLOYMENTS = "kubernetes-deployments";
    public static final String SCHEME_EVENTS = "kubernetes-events";
    public static final String SCHEME_HPA = "kubernetes-hpa";
    public static final String SCHEME_JOB = "kubernetes-job";
    public static final String SCHEME_CRON_JOB = "kubernetes-cronjob";
    public static final String SCHEME_NAMESPACES = "kubernetes-namespaces";
    public static final String SCHEME_NODES = "kubernetes-nodes";
    public static final String SCHEME_PERSISTENT_VOLUMES = "kubernetes-persistent-volumes";
    public static final String SCHEME_PERSISTENT_VOLUMES_CLAIMS = "kubernetes-persistent-volumes-claims";
    public static final String SCHEME_PODS = "kubernetes-pods";
    public static final String SCHEME_REPLICATION_CONTROLLERS = "kubernetes-replication-controllers";
    public static final String SCHEME_RESOURCES_QUOTA = "kubernetes-resources-quota";
    public static final String SCHEME_SECRETS = "kubernetes-secrets";
    public static final String SCHEME_SERVICE_ACCOUNTS = "kubernetes-service-accounts";
    public static final String SCHEME_SERVICES = "kubernetes-services";
    public static final String SCHEME_BUILD_CONFIG = "openshift-build-configs";
    public static final String SCHEME_BUILDS = "openshift-builds";
    public static final String SCHEME_DEPLOYMENT_CONFIGS = "openshift-deploymentconfigs";
    // Producer
    @Metadata(label = "producer", description = "The Producer operation", javaType = "String")
    public static final String KUBERNETES_OPERATION = "CamelKubernetesOperation";
    @Metadata(label = "producer", description = "The namespace name", javaType = "String",
              applicableFor = {
                      SCHEME_CONFIG_MAPS, SCHEME_CUSTOM_RESOURCES, SCHEME_DEPLOYMENTS,
                      SCHEME_HPA, SCHEME_JOB, SCHEME_NAMESPACES, SCHEME_PERSISTENT_VOLUMES_CLAIMS, SCHEME_PODS,
                      SCHEME_REPLICATION_CONTROLLERS, SCHEME_RESOURCES_QUOTA, SCHEME_SECRETS, SCHEME_SERVICE_ACCOUNTS,
                      SCHEME_SERVICES, SCHEME_BUILD_CONFIG, SCHEME_BUILDS, SCHEME_DEPLOYMENT_CONFIGS, SCHEME_EVENTS })
    public static final String KUBERNETES_NAMESPACE_NAME = "CamelKubernetesNamespaceName";
    @Metadata(label = "producer", description = "The namespace labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_NAMESPACES)
    public static final String KUBERNETES_NAMESPACE_LABELS = "CamelKubernetesNamespaceLabels";
    @Metadata(label = "producer", description = "The service labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_SERVICES)
    public static final String KUBERNETES_SERVICE_LABELS = "CamelKubernetesServiceLabels";
    @Metadata(label = "producer", description = "The service name", javaType = "String", applicableFor = SCHEME_SERVICES)
    public static final String KUBERNETES_SERVICE_NAME = "CamelKubernetesServiceName";
    @Metadata(label = "producer", description = "The spec of a service",
              javaType = "io.fabric8.kubernetes.api.model.ServiceSpec", applicableFor = SCHEME_SERVICES)
    public static final String KUBERNETES_SERVICE_SPEC = "CamelKubernetesServiceSpec";
    @Metadata(label = "producer", description = "The replication controller labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_REPLICATION_CONTROLLERS)
    public static final String KUBERNETES_REPLICATION_CONTROLLERS_LABELS = "CamelKubernetesReplicationControllersLabels";
    @Metadata(label = "producer", description = "The replication controller name", javaType = "String",
              applicableFor = SCHEME_REPLICATION_CONTROLLERS)
    public static final String KUBERNETES_REPLICATION_CONTROLLER_NAME = "CamelKubernetesReplicationControllerName";
    @Metadata(label = "producer", description = "The spec for a replication controller",
              javaType = "io.fabric8.kubernetes.api.model.ReplicationControllerSpec",
              applicableFor = SCHEME_REPLICATION_CONTROLLERS)
    public static final String KUBERNETES_REPLICATION_CONTROLLER_SPEC = "CamelKubernetesReplicationControllerSpec";
    @Metadata(label = "producer",
              description = "The number of replicas for a replication controller during the Scale operation",
              javaType = "Integer", applicableFor = SCHEME_REPLICATION_CONTROLLERS)
    public static final String KUBERNETES_REPLICATION_CONTROLLER_REPLICAS = "CamelKubernetesReplicationControllerReplicas";
    @Metadata(label = "producer", description = "The event labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENTS_LABELS = "CamelKubernetesEventsLabels";
    @Metadata(label = "producer",
              description = "The event time in ISO-8601 extended offset date-time format, such as '2011-12-03T10:15:30+01:00'.",
              javaType = "String", defaultValue = "server time", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_TIME = "CamelKubernetesEventTime";
    @Metadata(label = "producer", description = "The event action", javaType = "String", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_ACTION_PRODUCER = "CamelKubernetesEventAction";
    @Metadata(label = "producer", description = "The event type", javaType = "String", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_TYPE = "CamelKubernetesEventType";
    @Metadata(label = "producer", description = "The event reason", javaType = "String", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_REASON = "CamelKubernetesEventReason";
    @Metadata(label = "producer", description = "The event note", javaType = "String", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_NOTE = "CamelKubernetesEventNote";
    @Metadata(label = "producer", description = "The event regarding",
              javaType = "io.fabric8.kubernetes.api.model.ObjectReference", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_REGARDING = "CamelKubernetesEventRegarding";
    @Metadata(label = "producer", description = "The event related",
              javaType = "io.fabric8.kubernetes.api.model.ObjectReference", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_RELATED = "CamelKubernetesEventRelated";
    @Metadata(label = "producer", description = "The event reporting controller", javaType = "String",
              applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_REPORTING_CONTROLLER = "CamelKubernetesEventReportingController";
    @Metadata(label = "producer", description = "The event reporting instance", javaType = "String",
              applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_REPORTING_INSTANCE = "CamelKubernetesEventReportingInstance";
    @Metadata(label = "producer", description = "The event name", javaType = "String", applicableFor = SCHEME_EVENTS)
    public static final String KUBERNETES_EVENT_NAME = "CamelKubernetesEventName";
    @Metadata(label = "producer", description = "The pod labels", javaType = "Map<String, String>", applicableFor = SCHEME_PODS)
    public static final String KUBERNETES_PODS_LABELS = "CamelKubernetesPodsLabels";
    @Metadata(label = "producer", description = "The pod name", javaType = "String", applicableFor = SCHEME_PODS)
    public static final String KUBERNETES_POD_NAME = "CamelKubernetesPodName";
    @Metadata(label = "producer", description = "The spec for a pod", javaType = "io.fabric8.kubernetes.api.model.PodSpec",
              applicableFor = SCHEME_PODS)
    public static final String KUBERNETES_POD_SPEC = "CamelKubernetesPodSpec";
    @Metadata(label = "producer", description = "The persistent volume labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_PERSISTENT_VOLUMES)
    public static final String KUBERNETES_PERSISTENT_VOLUMES_LABELS = "CamelKubernetesPersistentVolumesLabels";
    @Metadata(label = "producer", description = "The persistent volume name", javaType = "String",
              applicableFor = SCHEME_PERSISTENT_VOLUMES)
    public static final String KUBERNETES_PERSISTENT_VOLUME_NAME = "CamelKubernetesPersistentVolumeName";
    @Metadata(label = "producer", description = "The persistent volume claim labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_PERSISTENT_VOLUMES_CLAIMS)
    public static final String KUBERNETES_PERSISTENT_VOLUMES_CLAIMS_LABELS = "CamelKubernetesPersistentVolumesClaimsLabels";
    @Metadata(label = "producer", description = "The persistent volume claim name", javaType = "String",
              applicableFor = SCHEME_PERSISTENT_VOLUMES_CLAIMS)
    public static final String KUBERNETES_PERSISTENT_VOLUME_CLAIM_NAME = "CamelKubernetesPersistentVolumeClaimName";
    @Metadata(label = "producer", description = "The spec for a persistent volume claim",
              javaType = "io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec",
              applicableFor = SCHEME_PERSISTENT_VOLUMES_CLAIMS)
    public static final String KUBERNETES_PERSISTENT_VOLUME_CLAIM_SPEC = "CamelKubernetesPersistentVolumeClaimSpec";
    @Metadata(label = "producer", description = "The secret labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_SECRETS)
    public static final String KUBERNETES_SECRETS_LABELS = "CamelKubernetesSecretsLabels";
    @Metadata(label = "producer", description = "The secret name", javaType = "String", applicableFor = SCHEME_SECRETS)
    public static final String KUBERNETES_SECRET_NAME = "CamelKubernetesSecretName";
    @Metadata(label = "producer", description = "A secret object", javaType = "io.fabric8.kubernetes.api.model.Secret",
              applicableFor = SCHEME_SECRETS)
    public static final String KUBERNETES_SECRET = "CamelKubernetesSecret";
    @Metadata(label = "producer", description = "The resource quota labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_RESOURCES_QUOTA)
    public static final String KUBERNETES_RESOURCES_QUOTA_LABELS = "CamelKubernetesResourcesQuotaLabels";
    @Metadata(label = "producer", description = "The resource quota name", javaType = "String",
              applicableFor = SCHEME_RESOURCES_QUOTA)
    public static final String KUBERNETES_RESOURCES_QUOTA_NAME = "CamelKubernetesResourcesQuotaName";
    @Metadata(label = "producer", description = "The spec for a resource quota",
              javaType = "io.fabric8.kubernetes.api.model.ResourceQuotaSpec", applicableFor = SCHEME_RESOURCES_QUOTA)
    public static final String KUBERNETES_RESOURCE_QUOTA_SPEC = "CamelKubernetesResourceQuotaSpec";
    @Metadata(label = "producer", description = "The service account labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_SERVICE_ACCOUNTS)
    public static final String KUBERNETES_SERVICE_ACCOUNTS_LABELS = "CamelKubernetesServiceAccountsLabels";
    @Metadata(label = "producer", description = "The service account name", javaType = "String",
              applicableFor = SCHEME_SERVICE_ACCOUNTS)
    public static final String KUBERNETES_SERVICE_ACCOUNT_NAME = "CamelKubernetesServiceAccountName";
    @Metadata(label = "producer", description = "A service account object",
              javaType = "io.fabric8.kubernetes.api.model.ServiceAccount", applicableFor = SCHEME_SERVICE_ACCOUNTS)
    public static final String KUBERNETES_SERVICE_ACCOUNT = "CamelKubernetesServiceAccount";
    @Metadata(label = "producer", description = "The node labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_NODES)
    public static final String KUBERNETES_NODES_LABELS = "CamelKubernetesNodesLabels";
    @Metadata(label = "producer", description = "The node name", javaType = "String", applicableFor = SCHEME_NODES)
    public static final String KUBERNETES_NODE_NAME = "CamelKubernetesNodeName";
    @Metadata(label = "producer", description = "The spec for a node", javaType = "io.fabric8.kubernetes.api.model.NodeSpec",
              applicableFor = SCHEME_NODES)
    public static final String KUBERNETES_NODE_SPEC = "CamelKubernetesNodeSpec";
    @Metadata(label = "producer", description = "The deployment labels", javaType = "Map<String, String>",
              applicableFor = { SCHEME_DEPLOYMENTS, SCHEME_DEPLOYMENT_CONFIGS })
    public static final String KUBERNETES_DEPLOYMENTS_LABELS = "CamelKubernetesDeploymentsLabels";
    @Metadata(label = "producer", description = "The deployment name", javaType = "String",
              applicableFor = { SCHEME_DEPLOYMENTS, SCHEME_DEPLOYMENT_CONFIGS })
    public static final String KUBERNETES_DEPLOYMENT_NAME = "CamelKubernetesDeploymentName";
    @Metadata(label = "producer", description = "The spec for a deployment",
              javaType = "io.fabric8.kubernetes.api.model.apps.DeploymentSpec", applicableFor = SCHEME_DEPLOYMENTS)
    public static final String KUBERNETES_DEPLOYMENT_SPEC = "CamelKubernetesDeploymentSpec";
    @Metadata(label = "producer", description = "The ConfigMap labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_CONFIG_MAPS)
    public static final String KUBERNETES_CONFIGMAPS_LABELS = "CamelKubernetesConfigMapsLabels";
    @Metadata(label = "producer", description = "The ConfigMap name", javaType = "String", applicableFor = SCHEME_CONFIG_MAPS)
    public static final String KUBERNETES_CONFIGMAP_NAME = "CamelKubernetesConfigMapName";
    @Metadata(label = "producer", description = "The ConfigMap Data", javaType = "Map<String, String>",
              applicableFor = SCHEME_CONFIG_MAPS)
    public static final String KUBERNETES_CONFIGMAP_DATA = "CamelKubernetesConfigData";
    @Metadata(label = "producer", description = "The Openshift build labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_BUILDS)
    public static final String KUBERNETES_BUILDS_LABELS = "CamelKubernetesBuildsLabels";
    @Metadata(label = "producer", description = "The Openshift build name", javaType = "String", applicableFor = SCHEME_BUILDS)
    public static final String KUBERNETES_BUILD_NAME = "CamelKubernetesBuildName";
    @Metadata(label = "producer", description = "The Openshift Config Build labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_BUILD_CONFIG)
    public static final String KUBERNETES_BUILD_CONFIGS_LABELS = "CamelKubernetesBuildConfigsLabels";
    @Metadata(label = "producer", description = "The Openshift Config Build name", javaType = "String",
              applicableFor = SCHEME_BUILD_CONFIG)
    public static final String KUBERNETES_BUILD_CONFIG_NAME = "CamelKubernetesBuildConfigName";
    @Metadata(label = "producer", description = "The desired instance count", javaType = "Integer",
              applicableFor = { SCHEME_DEPLOYMENTS, SCHEME_DEPLOYMENT_CONFIGS })
    public static final String KUBERNETES_DEPLOYMENT_REPLICAS = "CamelKubernetesDeploymentReplicas";
    @Metadata(label = "producer", description = "The HPA name.", javaType = "String", applicableFor = SCHEME_HPA)
    public static final String KUBERNETES_HPA_NAME = "CamelKubernetesHPAName";
    @Metadata(label = "producer", description = "The spec for a HPA.",
              javaType = "io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerSpec",
              applicableFor = SCHEME_HPA)
    public static final String KUBERNETES_HPA_SPEC = "CamelKubernetesHPASpec";
    @Metadata(label = "producer", description = "The HPA labels.", javaType = "Map<String, String>", applicableFor = SCHEME_HPA)
    public static final String KUBERNETES_HPA_LABELS = "CamelKubernetesHPALabels";
    @Metadata(label = "producer", description = "The Job name.", javaType = "String", applicableFor = SCHEME_JOB)
    public static final String KUBERNETES_JOB_NAME = "CamelKubernetesJobName";
    @Metadata(label = "producer", description = "The spec for a Job.",
              javaType = "io.fabric8.kubernetes.api.model.batch.v1.JobSpec", applicableFor = SCHEME_JOB)
    public static final String KUBERNETES_JOB_SPEC = "CamelKubernetesJobSpec";
    @Metadata(label = "producer", description = "The Job labels.", javaType = "Map<String, String>", applicableFor = SCHEME_JOB)
    public static final String KUBERNETES_JOB_LABELS = "CamelKubernetesJobLabels";
    @Metadata(label = "producer", description = "The deployment name", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_INSTANCE_NAME = "CamelKubernetesCRDInstanceName";
    @Metadata(label = "consumer", description = "Timestamp of the action watched by the consumer", javaType = "long",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_EVENT_TIMESTAMP = "CamelKubernetesCRDEventTimestamp";
    @Metadata(label = "consumer", description = "Action watched by the consumer",
              javaType = "io.fabric8.kubernetes.client.Watcher.Action", applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_EVENT_ACTION = "CamelKubernetesCRDEventAction";
    @Metadata(label = "producer", description = "The Consumer CRD Resource name we would like to watch", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_NAME = "CamelKubernetesCRDName";
    @Metadata(label = "producer", description = "The Consumer CRD Resource Group we would like to watch", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_GROUP = "CamelKubernetesCRDGroup";
    @Metadata(label = "producer", description = "The Consumer CRD Resource Scope we would like to watch", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_SCOPE = "CamelKubernetesCRDScope";
    @Metadata(label = "producer", description = "The Consumer CRD Resource Version we would like to watch", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_VERSION = "CamelKubernetesCRDVersion";
    @Metadata(label = "producer", description = "The Consumer CRD Resource Plural we would like to watch", javaType = "String",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_PLURAL = "CamelKubernetesCRDPlural";
    @Metadata(label = "producer", description = "The CRD resource labels", javaType = "Map<String, String>",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_LABELS = "CamelKubernetesCRDLabels";
    @Metadata(label = "producer", description = "The manifest of the CRD resource to create as JSON string",
              javaType = "String", applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_CRD_INSTANCE = "CamelKubernetesCRDInstance";
    @Metadata(label = "producer", description = "The result of the delete operation", javaType = "boolean",
              applicableFor = SCHEME_CUSTOM_RESOURCES)
    public static final String KUBERNETES_DELETE_RESULT = "CamelKubernetesDeleteResult";
    @Metadata(label = "producer", description = "The spec for a deployment config",
              javaType = "io.fabric8.openshift.api.model.DeploymentConfigSpec", applicableFor = SCHEME_DEPLOYMENT_CONFIGS)
    public static final String KUBERNETES_DEPLOYMENT_CONFIG_SPEC = "CamelKubernetesDeploymentConfigSpec";

    // Consumer
    @Metadata(label = "consumer", description = "Action watched by the consumer",
              javaType = "io.fabric8.kubernetes.client.Watcher.Action",
              applicableFor = {
                      SCHEME_CONFIG_MAPS, SCHEME_DEPLOYMENTS, SCHEME_HPA, SCHEME_NAMESPACES, SCHEME_NODES, SCHEME_PODS,
                      SCHEME_REPLICATION_CONTROLLERS, SCHEME_SERVICES, SCHEME_DEPLOYMENT_CONFIGS, SCHEME_EVENTS })
    public static final String KUBERNETES_EVENT_ACTION = "CamelKubernetesEventAction";
    @Metadata(label = "consumer", description = "Timestamp of the action watched by the consumer", javaType = "long",
              applicableFor = {
                      SCHEME_CONFIG_MAPS, SCHEME_DEPLOYMENTS, SCHEME_HPA, SCHEME_NAMESPACES, SCHEME_NODES, SCHEME_PODS,
                      SCHEME_REPLICATION_CONTROLLERS, SCHEME_SERVICES, SCHEME_DEPLOYMENT_CONFIGS, SCHEME_EVENTS })
    public static final String KUBERNETES_EVENT_TIMESTAMP = "CamelKubernetesEventTimestamp";

    @Metadata(label = "producer", description = "The Cronjob labels.", javaType = "Map<String, String>",
              applicableFor = SCHEME_CRON_JOB)
    public static final String KUBERNETES_CRON_JOB_LABELS = "CamelKubernetesCronJobLabels";

    @Metadata(label = "producer", description = "The Cronjob name.", javaType = "String", applicableFor = SCHEME_CRON_JOB)
    public static final String KUBERNETES_CRON_JOB_NAME = "CamelKubernetesCronJobName";
    @Metadata(label = "producer", description = "The spec for a Job.",
              javaType = "io.fabric8.kubernetes.api.model.batch.v1.CronJobSpec", applicableFor = SCHEME_CRON_JOB)
    public static final String KUBERNETES_CRON_JOB_SPEC = "CamelKubernetesCronJobSpec";

    private KubernetesConstants() {

    }
}
