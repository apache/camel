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

    public static final String DEFAULT_CONFIGMAP_NAME = "leaders";


    public static final double DEFAULT_JITTER_FACTOR = 1.2;
    public static final long DEFAULT_LEASE_DURATION_SECONDS = 20;
    public static final long DEFAULT_RENEW_DEADLINE_SECONDS = 15;
    public static final long DEFAULT_RETRY_PERIOD_SECONDS = 6;

    public static final long DEFAULT_RETRY_ON_ERROR_INTERVAL_SECONDS = 5;
    public static final long DEFAULT_WATCH_REFRESH_INTERVAL_SECONDS = 1800;

    /**
     * Kubernetes namespace containing the pods and the ConfigMap used for locking.
     */
    private String kubernetesResourcesNamespace;

    /**
     * Name of the ConfigMap used for locking.
     */
    private String configMapName = DEFAULT_CONFIGMAP_NAME;

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
     * Watch recreation can be disabled by putting value <= 0.
     */
    private long retryOnErrorIntervalSeconds = DEFAULT_RETRY_ON_ERROR_INTERVAL_SECONDS;

    /**
     * A jitter factor to apply in order to prevent all pods to try to become leaders in the same instant.
     */
    private double jitterFactor = DEFAULT_JITTER_FACTOR;

    /**
     * The default duration of the lease for the current leader.
     */
    private long leaseDurationSeconds = DEFAULT_LEASE_DURATION_SECONDS;

    /**
     * The deadline after which the leader must stop trying to renew its leadership (and yield it).
     */
    private long renewDeadlineSeconds = DEFAULT_RENEW_DEADLINE_SECONDS;

    /**
     * The time between two subsequent attempts to acquire/renew the leadership (or after the lease expiration).
     * It is randomized using the jitter factor in case of new leader election (not renewal).
     */
    private long retryPeriodSeconds = DEFAULT_RETRY_PERIOD_SECONDS;

    /**
     * Set this to a positive value in order to recreate watchers after a certain amount of time
     * (to prevent them becoming stale).
     */
    private long watchRefreshIntervalSeconds = DEFAULT_WATCH_REFRESH_INTERVAL_SECONDS;

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

    public long getRetryOnErrorIntervalSeconds() {
        return retryOnErrorIntervalSeconds;
    }

    public void setRetryOnErrorIntervalSeconds(long retryOnErrorIntervalSeconds) {
        this.retryOnErrorIntervalSeconds = retryOnErrorIntervalSeconds;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public long getRenewDeadlineSeconds() {
        return renewDeadlineSeconds;
    }

    public void setRenewDeadlineSeconds(long renewDeadlineSeconds) {
        this.renewDeadlineSeconds = renewDeadlineSeconds;
    }

    public long getRetryPeriodSeconds() {
        return retryPeriodSeconds;
    }

    public void setRetryPeriodSeconds(long retryPeriodSeconds) {
        this.retryPeriodSeconds = retryPeriodSeconds;
    }

    public long getWatchRefreshIntervalSeconds() {
        return watchRefreshIntervalSeconds;
    }

    public void setWatchRefreshIntervalSeconds(long watchRefreshIntervalSeconds) {
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
        sb.append(", retryOnErrorIntervalSeconds=").append(retryOnErrorIntervalSeconds);
        sb.append(", jitterFactor=").append(jitterFactor);
        sb.append(", leaseDurationSeconds=").append(leaseDurationSeconds);
        sb.append(", renewDeadlineSeconds=").append(renewDeadlineSeconds);
        sb.append(", retryPeriodSeconds=").append(retryPeriodSeconds);
        sb.append(", watchRefreshIntervalSeconds=").append(watchRefreshIntervalSeconds);
        sb.append('}');
        return sb.toString();
    }
}
