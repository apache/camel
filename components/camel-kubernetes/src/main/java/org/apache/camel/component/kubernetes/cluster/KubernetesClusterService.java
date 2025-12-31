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
package org.apache.camel.component.kubernetes.cluster;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cluster.CamelPreemptiveClusterService;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLockConfiguration;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;

/**
 * A Kubernetes based cluster service leveraging Kubernetes optimistic locks on resources (specifically ConfigMaps).
 */
@Metadata(label = "bean",
          description = "Kubernetes based cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
@DeferredContextBinding
public class KubernetesClusterService extends AbstractCamelClusterService<KubernetesClusterView>
        implements CamelPreemptiveClusterService {

    @Metadata(description = "URL to a remote Kubernetes API server."
                            + " This should only be used when your Camel application is connecting from outside Kubernetes. If you run your Camel"
                            + " application inside Kubernetes, then you can use local or client as the URL to tell Camel to run in local mode."
                            + " If you connect remotely to Kubernetes, then you may also need some of the many other configuration options for"
                            + " secured connection with certificates, etc.")
    private String masterUrl;
    @Metadata(description = "Kubernetes namespace")
    private String namespace;
    @Metadata(label = "advanced",
              description = "Connection timeout in milliseconds to use when making requests to the Kubernetes API server.")
    private Integer connectionTimeout;
    @Metadata(description = "Kubernetes resource type used to hold the leases.", defaultValue = "Lease")
    private LeaseResourceType leaseResourceType = LeaseResourceType.Lease;
    @Metadata(description = "Kubernetes namespace containing the pods and the ConfigMap used for locking.")
    private String kubernetesResourcesNamespace;
    @Metadata(description = "Name of the resource used for locking (or prefix, in case multiple ones are used).",
              defaultValue = "leaders")
    private String kubernetesResourceName = "leaders";
    @Metadata(description = "Name of the lock group (or namespace according to the Camel cluster convention) within the chosen ConfigMap.")
    private String groupName;
    @Metadata(description = "Name of the current pod (defaults to host name).")
    private String podName;
    @Metadata(description = "Labels used to identify the members of the cluster.")
    private Map<String, String> clusterLabels;
    @Metadata(description = "A jitter factor to apply in order to prevent all pods to call Kubernetes APIs in the same instant.",
              defaultValue = "1.2")
    private double jitterFactor = 1.2d;
    @Metadata(description = "The default duration of the lease for the current leader.", defaultValue = "15000")
    private long leaseDurationMillis = 15000;
    @Metadata(description = "The deadline after which the leader must stop its services because it may have lost the leadership.",
              defaultValue = "10000")
    private long renewDeadlineMillis = 10000;
    @Metadata(description = "The time between two subsequent attempts to check and acquire the leadership. It is randomized using the jitter factor.",
              defaultValue = "2000")
    private long retryPeriodMillis = 2000;
    @Metadata(label = "advanced", description = "To use an existing configuration for Kubernetes")
    private KubernetesConfiguration configuration;

    private KubernetesLockConfiguration lockConfiguration;

    public KubernetesClusterService() {
    }

    public KubernetesClusterService(KubernetesConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public KubernetesClusterService(CamelContext camelContext, KubernetesConfiguration configuration) {
        super(null, camelContext);
        this.configuration = configuration.copy();
    }

    @Override
    protected KubernetesClusterView createView(String namespace) throws Exception {
        KubernetesLockConfiguration lockConfig = lockConfigWithGroupNameAndDefaults(namespace);
        KubernetesConfiguration config = setConfigDefaults(this.configuration.copy(), lockConfig);
        return new KubernetesClusterView(getCamelContext(), this, config, lockConfig);
    }

    @Override
    public KubernetesClusterView getView(String namespace) throws Exception {
        return (KubernetesClusterView) super.getView(namespace);
    }

    protected KubernetesConfiguration setConfigDefaults(
            KubernetesConfiguration configuration, KubernetesLockConfiguration lockConfiguration) {
        if (configuration.getConnectionTimeout() == null) {
            // Set the connection timeout to be much lower than the renewal
            // deadline,
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

        ObjectHelper.notNull(config.getKubernetesResourceName(), "configMapName");
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
            throw new IllegalStateException(
                    "leaseDurationMillis must be greater than renewDeadlineMillis ("
                                            + config.getLeaseDurationMillis() + " is not greater than "
                                            + config.getRenewDeadlineMillis() + ")");
        }
        if (config.getRenewDeadlineMillis() <= config.getJitterFactor() * config.getRetryPeriodMillis()) {
            throw new IllegalStateException(
                    "renewDeadlineMillis must be greater than jitterFactor*retryPeriodMillis " + "("
                                            + config.getRenewDeadlineMillis()
                                            + " is not greater than " + config.getJitterFactor() + "*"
                                            + config.getRetryPeriodMillis() + ")");
        }

        return config;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (configuration == null) {
            configuration = new KubernetesConfiguration();
        } else {
            configuration = configuration.copy();
            if (masterUrl != null) {
                configuration.setMasterUrl(masterUrl);
            }
            if (namespace != null) {
                configuration.setNamespace(namespace);
            }
            if (connectionTimeout != null) {
                configuration.setConnectionTimeout(connectionTimeout);
            }
        }
        if (lockConfiguration == null) {
            lockConfiguration = new KubernetesLockConfiguration();
            if (clusterLabels != null) {
                lockConfiguration.setClusterLabels(clusterLabels);
            } else {
                lockConfiguration.setClusterLabels(Collections.EMPTY_MAP);
            }
            lockConfiguration.setGroupName(groupName);
            lockConfiguration.setJitterFactor(jitterFactor);
            lockConfiguration.setKubernetesResourceName(kubernetesResourceName);
            lockConfiguration.setKubernetesResourcesNamespace(kubernetesResourcesNamespace);
            lockConfiguration.setLeaseDurationMillis(leaseDurationMillis);
            lockConfiguration.setLeaseResourceType(leaseResourceType);
            lockConfiguration.setPodName(podName);
            lockConfiguration.setRenewDeadlineMillis(renewDeadlineMillis);
            lockConfiguration.setRetryPeriodMillis(retryPeriodMillis);
        }
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * @deprecated use setNamespace
     */
    @Deprecated
    public String getKubernetesNamespace() {
        return getNamespace();
    }

    /**
     * @deprecated use setNamespace
     */
    @Deprecated
    public void setKubernetesNamespace(String namespace) {
        setNamespace(namespace);
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public LeaseResourceType getLeaseResourceType() {
        return leaseResourceType;
    }

    public void setLeaseResourceType(LeaseResourceType leaseResourceType) {
        this.leaseResourceType = leaseResourceType;
    }

    public String getKubernetesResourcesNamespace() {
        return kubernetesResourcesNamespace;
    }

    public void setKubernetesResourcesNamespace(String kubernetesResourcesNamespace) {
        this.kubernetesResourcesNamespace = kubernetesResourcesNamespace;
    }

    public String getKubernetesResourceName() {
        return kubernetesResourceName;
    }

    public void setKubernetesResourceName(String kubernetesResourceName) {
        this.kubernetesResourceName = kubernetesResourceName;
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

    public void setClusterLabels(Map<String, String> clusterLabels) {
        this.clusterLabels = clusterLabels;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public long getLeaseDurationMillis() {
        return leaseDurationMillis;
    }

    public void setLeaseDurationMillis(long leaseDurationMillis) {
        this.leaseDurationMillis = leaseDurationMillis;
    }

    public long getRenewDeadlineMillis() {
        return renewDeadlineMillis;
    }

    public void setRenewDeadlineMillis(long renewDeadlineMillis) {
        this.renewDeadlineMillis = renewDeadlineMillis;
    }

    public long getRetryPeriodMillis() {
        return retryPeriodMillis;
    }

    public void setRetryPeriodMillis(long retryPeriodMillis) {
        this.retryPeriodMillis = retryPeriodMillis;
    }

}
