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
package org.apache.camel.component.kubernetes.ha;

import java.net.InetAddress;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.ha.lock.KubernetesLockConfiguration;
import org.apache.camel.impl.ha.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;

/**
 * A Kubernetes based cluster service leveraging Kubernetes optimistic locks on resources (specifically ConfigMaps).
 */
public class KubernetesClusterService extends AbstractCamelClusterService<KubernetesClusterView> {

    public static final String DEFAULT_CONFIGMAP_NAME = "leaders";

    private KubernetesConfiguration configuration;

    private KubernetesLockConfiguration lockConfiguration;

    public KubernetesClusterService() {
        this.configuration = new KubernetesConfiguration();
        this.lockConfiguration = new KubernetesLockConfiguration();
    }

    public KubernetesClusterService(KubernetesConfiguration configuration) {
        this.configuration = configuration.copy();
        this.lockConfiguration = new KubernetesLockConfiguration();
    }

    public KubernetesClusterService(CamelContext camelContext, KubernetesConfiguration configuration) {
        super(null, camelContext);
        this.configuration = configuration.copy();
        this.lockConfiguration = new KubernetesLockConfiguration();
    }

    @Override
    protected KubernetesClusterView createView(String namespace) throws Exception {
        KubernetesLockConfiguration lockConfig = configWithGroupNameAndDefaults(namespace);
        return new KubernetesClusterView(this, configuration, lockConfig);
    }

    protected KubernetesLockConfiguration configWithGroupNameAndDefaults(String groupName) {
        KubernetesLockConfiguration config = this.lockConfiguration.copy();

        config.setGroupName(ObjectHelper.notNull(groupName, "groupName"));

        // Check defaults (Namespace and podName can be null)
        if (config.getConfigMapName() == null) {
            config.setConfigMapName(DEFAULT_CONFIGMAP_NAME);
        }
        if (config.getPodName() == null) {
            config.setPodName(System.getenv("HOSTNAME"));
            if (config.getPodName() == null) {
                try {
                    config.setPodName(InetAddress.getLocalHost().getHostName());
                } catch (Exception e) {
                    throw new RuntimeCamelException("Unable to determine pod name", e);
                }
            }
        }

        return config;
    }

    public String getMasterUrl() {
        return configuration.getMasterUrl();
    }

    /**
     * Set the URL of the Kubernetes master (read from Kubernetes client properties by default).
     */
    public void setMasterUrl(String masterUrl) {
        configuration.setMasterUrl(masterUrl);
    }

    public String getKubernetesNamespace() {
        return this.lockConfiguration.getKubernetesResourcesNamespace();
    }

    /**
     * Set the name of the Kubernetes namespace containing the pods and the configmap (autodetected by default)
     */
    public void setKubernetesNamespace(String kubernetesNamespace) {
        this.lockConfiguration.setKubernetesResourcesNamespace(kubernetesNamespace);
    }

    public String getConfigMapName() {
        return this.lockConfiguration.getConfigMapName();
    }

    /**
     * Set the name of the ConfigMap used to do optimistic locking (defaults to 'leaders').
     */
    public void setConfigMapName(String configMapName) {
        this.lockConfiguration.setConfigMapName(configMapName);
    }

    public String getPodName() {
        return this.lockConfiguration.getPodName();
    }

    /**
     * Set the name of the current pod (autodetected from container host name by default).
     */
    public void setPodName(String podName) {
        this.lockConfiguration.setPodName(podName);
    }

    public Map<String, String> getClusterLabels() {
        return lockConfiguration.getClusterLabels();
    }

    /**
     * Set the labels used to identify the pods composing the cluster.
     */
    public void setClusterLabels(Map<String, String> clusterLabels) {
        lockConfiguration.setClusterLabels(clusterLabels);
    }

    public Long getWatchRefreshIntervalSeconds() {
        return lockConfiguration.getWatchRefreshIntervalSeconds();
    }

    /**
     * Indicates the maximum amount of time a Kubernetes watch should be kept active, before being recreated.
     * Watch recreation can be disabled by putting a negative value (the default will be used in case of null).
     */
    public void setWatchRefreshIntervalSeconds(Long watchRefreshIntervalSeconds) {
        lockConfiguration.setWatchRefreshIntervalSeconds(watchRefreshIntervalSeconds);
    }
}
