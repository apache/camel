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
package org.apache.camel.component.kubernetes.ha.lock;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Configuration for Kubernetes Lock.
 */
public class KubernetesLockConfiguration implements Cloneable {

    private static final long DEFAULT_WATCHER_REFRESH_INTERVAL_SECONDS = 1800;

    /**
     * Kubernetes namespace containing the pods and the ConfigMap used for locking.
     */
    private String kubernetesResourcesNamespace;

    /**
     * Name of the ConfigMap used for locking.
     */
    private String configMapName;

    /**
     * Name of the lock group (or namespace according to the Camel cluster convention) within the chosen ConfgMap.
     */
    private String groupName;

    /**
     * Name of the current pod (defaults to host name).
     */
    private String podName;

    /**
     * Labels used to identify the members of the cluster.
     */
    private Map<String, String> clusterLabels = new HashMap<>();

    /**
     * Indicates the maximum amount of time a Kubernetes watch should be kept active, before being recreated.
     * Watch recreation can be disabled by putting a negative value (the default will be used in case of null).
     */
    private Long watchRefreshIntervalSeconds;

    public KubernetesLockConfiguration() {
    }

    public String getKubernetesResourcesNamespaceOrDefault(KubernetesClient kubernetesClient) {
        if (kubernetesResourcesNamespace != null) {
            return kubernetesResourcesNamespace;
        }
        return kubernetesClient.getNamespace();
    }

    public String getKubernetesResourcesNamespace() {
        return kubernetesResourcesNamespace;
    }

    public void setKubernetesResourcesNamespace(String kubernetesResourcesNamespace) {
        this.kubernetesResourcesNamespace = kubernetesResourcesNamespace;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public void setConfigMapName(String configMapName) {
        this.configMapName = configMapName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public Map<String, String> getClusterLabels() {
        return clusterLabels;
    }

    public void addToClusterLabels(String key, String value) {
        this.clusterLabels.put(key, value);
    }

    public void setClusterLabels(Map<String, String> clusterLabels) {
        this.clusterLabels = clusterLabels;
    }

    public Long getWatchRefreshIntervalSeconds() {
        return watchRefreshIntervalSeconds;
    }

    public long getWatchRefreshIntervalSecondsOrDefault() {
        Long interval = watchRefreshIntervalSeconds;
        if (interval == null) {
            interval = DEFAULT_WATCHER_REFRESH_INTERVAL_SECONDS;
        }
        return interval;
    }

    public void setWatchRefreshIntervalSeconds(Long watchRefreshIntervalSeconds) {
        this.watchRefreshIntervalSeconds = watchRefreshIntervalSeconds;
    }

    public KubernetesLockConfiguration copy() {
        try {
            KubernetesLockConfiguration copy = (KubernetesLockConfiguration) this.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cannot clone", e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("KubernetesLockConfiguration{");
        sb.append("kubernetesResourcesNamespace='").append(kubernetesResourcesNamespace).append('\'');
        sb.append(", configMapName='").append(configMapName).append('\'');
        sb.append(", groupName='").append(groupName).append('\'');
        sb.append(", podName='").append(podName).append('\'');
        sb.append(", clusterLabels=").append(clusterLabels);
        sb.append(", watchRefreshIntervalSeconds=").append(watchRefreshIntervalSeconds);
        sb.append('}');
        return sb.toString();
    }
}
