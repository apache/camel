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

import io.fabric8.kubernetes.client.KubernetesClient;

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

        // Determine the pod name if not provided
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

        ObjectHelper.notNull(config.getConfigMapName(), "configMapName");
        ObjectHelper.notNull(config.getClusterLabels(), "clusterLabels");

        if (config.getJitterFactor() < 1) {
            throw new IllegalStateException("jitterFactor must be >= 1 (found: " + config.getJitterFactor() + ")");
        }
        if (config.getRetryOnErrorIntervalSeconds() <= 0) {
            throw new IllegalStateException("retryOnErrorIntervalSeconds must be > 0 (found: " + config.getRetryOnErrorIntervalSeconds() + ")");
        }
        if (config.getRetryPeriodSeconds() <= 0) {
            throw new IllegalStateException("retryPeriodSeconds must be > 0 (found: " + config.getRetryPeriodSeconds() + ")");
        }
        if (config.getRenewDeadlineSeconds() <= 0) {
            throw new IllegalStateException("renewDeadlineSeconds must be > 0 (found: " + config.getRenewDeadlineSeconds() + ")");
        }
        if (config.getLeaseDurationSeconds() <= 0) {
            throw new IllegalStateException("leaseDurationSeconds must be > 0 (found: " + config.getLeaseDurationSeconds() + ")");
        }
        if (config.getLeaseDurationSeconds() <= config.getRenewDeadlineSeconds()) {
            throw new IllegalStateException("leaseDurationSeconds must be greater than renewDeadlineSeconds "
                    + "(" + config.getLeaseDurationSeconds() + " is not greater than " + config.getRenewDeadlineSeconds() + ")");
        }
        if (config.getRenewDeadlineSeconds() <= config.getJitterFactor() * config.getRetryPeriodSeconds()) {
            throw new IllegalStateException("renewDeadlineSeconds must be greater than jitterFactor*retryPeriodSeconds "
                    + "(" + config.getRenewDeadlineSeconds() + " is not greater than " + config.getJitterFactor() + "*" + config.getRetryPeriodSeconds() + ")");
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

    public void addToClusterLabels(String key, String value) {
        lockConfiguration.addToClusterLabels(key, value);
    }

    public String getKubernetesResourcesNamespace() {
        return lockConfiguration.getKubernetesResourcesNamespace();
    }

    /**
     * Kubernetes namespace containing the pods and the ConfigMap used for locking.
     */
    public void setKubernetesResourcesNamespace(String kubernetesResourcesNamespace) {
        lockConfiguration.setKubernetesResourcesNamespace(kubernetesResourcesNamespace);
    }

    public long getRetryOnErrorIntervalSeconds() {
        return lockConfiguration.getRetryOnErrorIntervalSeconds();
    }

    /**
     * Indicates the maximum amount of time a Kubernetes watch should be kept active, before being recreated.
     * Watch recreation can be disabled by putting value <= 0.
     */
    public void setRetryOnErrorIntervalSeconds(long retryOnErrorIntervalSeconds) {
        lockConfiguration.setRetryOnErrorIntervalSeconds(retryOnErrorIntervalSeconds);
    }

    public double getJitterFactor() {
        return lockConfiguration.getJitterFactor();
    }

    /**
     * A jitter factor to apply in order to prevent all pods to try to become leaders in the same instant.
     */
    public void setJitterFactor(double jitterFactor) {
        lockConfiguration.setJitterFactor(jitterFactor);
    }

    public long getLeaseDurationSeconds() {
        return lockConfiguration.getLeaseDurationSeconds();
    }

    /**
     * The default duration of the lease for the current leader.
     */
    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        lockConfiguration.setLeaseDurationSeconds(leaseDurationSeconds);
    }

    public long getRenewDeadlineSeconds() {
        return lockConfiguration.getRenewDeadlineSeconds();
    }

    /**
     * The deadline after which the leader must stop trying to renew its leadership (and yield it).
     */
    public void setRenewDeadlineSeconds(long renewDeadlineSeconds) {
        lockConfiguration.setRenewDeadlineSeconds(renewDeadlineSeconds);
    }

    public long getRetryPeriodSeconds() {
        return lockConfiguration.getRetryPeriodSeconds();
    }

    /**
     * The time between two subsequent attempts to acquire/renew the leadership (or after the lease expiration).
     * It is randomized using the jitter factor in case of new leader election (not renewal).
     */
    public void setRetryPeriodSeconds(long retryPeriodSeconds) {
        lockConfiguration.setRetryPeriodSeconds(retryPeriodSeconds);
    }

    public long getWatchRefreshIntervalSeconds() {
        return lockConfiguration.getWatchRefreshIntervalSeconds();
    }

    /**
     * Set this to a positive value in order to recreate watchers after a certain amount of time,
     * to avoid having stale watchers.
     */
    public void setWatchRefreshIntervalSeconds(long watchRefreshIntervalSeconds) {
        lockConfiguration.setWatchRefreshIntervalSeconds(watchRefreshIntervalSeconds);
    }

}
