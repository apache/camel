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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kubernetes.build_configs.KubernetesBuildConfigsProducer;
import org.apache.camel.component.kubernetes.builds.KubernetesBuildsProducer;
import org.apache.camel.component.kubernetes.config_maps.KubernetesConfigMapsProducer;
import org.apache.camel.component.kubernetes.namespaces.KubernetesNamespacesConsumer;
import org.apache.camel.component.kubernetes.namespaces.KubernetesNamespacesProducer;
import org.apache.camel.component.kubernetes.nodes.KubernetesNodesConsumer;
import org.apache.camel.component.kubernetes.nodes.KubernetesNodesProducer;
import org.apache.camel.component.kubernetes.persistent_volumes.KubernetesPersistentVolumesProducer;
import org.apache.camel.component.kubernetes.persistent_volumes_claims.KubernetesPersistentVolumesClaimsProducer;
import org.apache.camel.component.kubernetes.pods.KubernetesPodsConsumer;
import org.apache.camel.component.kubernetes.pods.KubernetesPodsProducer;
import org.apache.camel.component.kubernetes.replication_controllers.KubernetesReplicationControllersConsumer;
import org.apache.camel.component.kubernetes.replication_controllers.KubernetesReplicationControllersProducer;
import org.apache.camel.component.kubernetes.resources_quota.KubernetesResourcesQuotaProducer;
import org.apache.camel.component.kubernetes.secrets.KubernetesSecretsProducer;
import org.apache.camel.component.kubernetes.service_accounts.KubernetesServiceAccountsProducer;
import org.apache.camel.component.kubernetes.services.KubernetesServicesConsumer;
import org.apache.camel.component.kubernetes.services.KubernetesServicesProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use splitted kubernetes components instead of this composite component.
 * @deprecated
 */
@Deprecated
@UriEndpoint(firstVersion = "2.17.0", scheme = "kubernetes", title = "Kubernetes", syntax = "kubernetes:masterUrl", label = "container,cloud,paas")
public class KubernetesEndpoint extends AbstractKubernetesEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesEndpoint.class);

    @UriParam(enums = "namespaces,services,replicationControllers,pods,persistentVolumes,persistentVolumesClaims,secrets,resourcesQuota,serviceAccounts,nodes,configMaps,builds,buildConfigs")
    @Metadata(required = "true")
    private String category;

    public KubernetesEndpoint(String uri, KubernetesComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
        category = config.getCategory();
    }

    @Override
    public Producer createProducer() throws Exception {
        if (ObjectHelper.isEmpty(category)) {
            throw new IllegalArgumentException("A producer category must be specified");
        } else {
            switch (category) {

            case KubernetesCategory.NAMESPACES:
                return new KubernetesNamespacesProducer(this);

            case KubernetesCategory.SERVICES:
                return new KubernetesServicesProducer(this);

            case KubernetesCategory.REPLICATION_CONTROLLERS:
                return new KubernetesReplicationControllersProducer(this);

            case KubernetesCategory.PODS:
                return new KubernetesPodsProducer(this);

            case KubernetesCategory.PERSISTENT_VOLUMES:
                return new KubernetesPersistentVolumesProducer(this);

            case KubernetesCategory.PERSISTENT_VOLUMES_CLAIMS:
                return new KubernetesPersistentVolumesClaimsProducer(this);

            case KubernetesCategory.SECRETS:
                return new KubernetesSecretsProducer(this);

            case KubernetesCategory.RESOURCES_QUOTA:
                return new KubernetesResourcesQuotaProducer(this);

            case KubernetesCategory.SERVICE_ACCOUNTS:
                return new KubernetesServiceAccountsProducer(this);

            case KubernetesCategory.NODES:
                return new KubernetesNodesProducer(this);
                
            case KubernetesCategory.CONFIGMAPS:
                return new KubernetesConfigMapsProducer(this);

            case KubernetesCategory.BUILDS:
                return new KubernetesBuildsProducer(this);

            case KubernetesCategory.BUILD_CONFIGS:
                return new KubernetesBuildConfigsProducer(this);

            default:
                throw new IllegalArgumentException("The " + category + " producer category doesn't exist");
            }
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(category)) {
            throw new IllegalArgumentException("A consumer category must be specified");
        } else {
            switch (category) {

            case KubernetesCategory.PODS:
                return new KubernetesPodsConsumer(this, processor);

            case KubernetesCategory.SERVICES:
                return new KubernetesServicesConsumer(this, processor);

            case KubernetesCategory.REPLICATION_CONTROLLERS:
                return new KubernetesReplicationControllersConsumer(this, processor);
                
            case KubernetesCategory.NAMESPACES:
                return new KubernetesNamespacesConsumer(this, processor);
                
            case KubernetesCategory.NODES:
                return new KubernetesNodesConsumer(this, processor);

            default:
                throw new IllegalArgumentException("The " + category + " consumer category doesn't exist");
            }
        }
    }

    /**
     * Kubernetes Producer and Consumer category
     */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

}
