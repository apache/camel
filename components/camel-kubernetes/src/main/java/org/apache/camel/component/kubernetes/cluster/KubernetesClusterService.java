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
package org.apache.camel.component.kubernetes.cluster;

import java.net.InetAddress;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLockConfiguration;
import org.apache.camel.impl.cluster.AbstractCamelClusterService;
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
        KubernetesLockConfiguration lockConfig = lockConfigWithGroupNameAndDefaults(namespace);
        KubernetesConfiguration config = setConfigDefaults(this.configuration.copy(), lockConfig);
        return new KubernetesClusterView(getCamelContext(), this, config, lockConfig);
    }

    protected KubernetesConfiguration setConfigDefaults(KubernetesConfiguration configuration, KubernetesLockConfiguration lockConfiguration) {
        if (configuration.getConnectionTimeout() == null) {
            // Set the connection timeout to be much lower than the renewal deadline,
            // to avoid losing the leadership in case of stale connections
            int timeout = (int) (lockConfiguration.getRenewDeadlineMillis() / 3);
            timeout = Math.max(timeout, 3000);
            timeout = Math.min(timeout, 30000);
            configuration.setConnectionTimeout(timeout);
        }
        return configuration;
    }

    protected KubernetesLockConfiguration lockConfigWithGroupNameAndDefaults(String groupName) {
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
        if (config.getRetryPeriodMillis() <= 0) {
            throw new IllegalStateException("retryPeriodMillis must be > 0 (found: " + config.getRetryPeriodMillis() + ")");
        }
        if (config.getRenewDeadlineMillis() <= 0) {
            throw new IllegalStateException("renewDeadlineMillis must be > 0 (found: " + config.getRenewDeadlineMillis() + ")");
        }
        if (config.getLeaseDurationMillis() <= 0) {
            throw new IllegalStateException("leaseDurationMillis must be > 0 (found: " + config.getLeaseDurationMillis() + ")");
        }
        if (config.getLeaseDurationMillis() <= config.getRenewDeadlineMillis()) {
            throw new IllegalStateException("leaseDurationMillis must be greater than renewDeadlineMillis "
                    + "(" + config.getLeaseDurationMillis() + " is not greater than " + config.getRenewDeadlineMillis() + ")");
        }
        if (config.getRenewDeadlineMillis() <= config.getJitterFactor() * config.getRetryPeriodMillis()) {
            throw new IllegalStateException("renewDeadlineMillis must be greater than jitterFactor*retryPeriodMillis "
                    + "(" + config.getRenewDeadlineMillis() + " is not greater than " + config.getJitterFactor() + "*" + config.getRetryPeriodMillis() + ")");
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

    public Integer getConnectionTimeoutMillis() {
        return configuration.getConnectionTimeout();
    }

    /**
     * Connection timeout in milliseconds to use when making requests to the Kubernetes API server.
     */
    public void setConnectionTimeoutMillis(Integer connectionTimeout) {
        configuration.setConnectionTimeout(connectionTimeout);
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

    public double getJitterFactor() {
        return lockConfiguration.getJitterFactor();
    }

    /**
     * A jitter factor to apply in order to prevent all pods to call Kubernetes APIs in the same instant.
     */
    public void setJitterFactor(double jitterFactor) {
        lockConfiguration.setJitterFactor(jitterFactor);
    }

    public long getLeaseDurationMillis() {
        return lockConfiguration.getLeaseDurationMillis();
    }

    /**
     * The default duration of the lease for the current leader.
     */
    public void setLeaseDurationMillis(long leaseDurationMillis) {
        lockConfiguration.setLeaseDurationMillis(leaseDurationMillis);
    }

    public long getRenewDeadlineMillis() {
        return lockConfiguration.getRenewDeadlineMillis();
    }

    /**
     * The deadline after which the leader must stop its services because it may have lost the leadership.
     */
    public void setRenewDeadlineMillis(long renewDeadlineMillis) {
        lockConfiguration.setRenewDeadlineMillis(renewDeadlineMillis);
    }

    public long getRetryPeriodMillis() {
        return lockConfiguration.getRetryPeriodMillis();
    }

    /**
     * The time between two subsequent attempts to check and acquire the leadership.
     * It is randomized using the jitter factor.
     */
    public void setRetryPeriodMillis(long retryPeriodMillis) {
        lockConfiguration.setRetryPeriodMillis(retryPeriodMillis);
    }
}
