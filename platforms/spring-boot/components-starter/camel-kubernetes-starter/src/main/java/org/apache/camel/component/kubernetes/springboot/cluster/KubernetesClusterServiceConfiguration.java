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
package org.apache.camel.component.kubernetes.springboot.cluster;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.component.kubernetes.cluster.service")
public class KubernetesClusterServiceConfiguration {

    /**
     * Sets if the Kubernetes cluster service should be enabled or not, default is false.
     */
    private boolean enabled;

    /**
     * Cluster Service ID
     */
    private String id;

    /**
     * Set the URL of the Kubernetes master (read from Kubernetes client properties by default).
     */
    private String masterUrl;

    /**
     * Connection timeout in milliseconds to use when making requests to the Kubernetes API server.
     */
    private Integer connectionTimeoutMillis;

    /**
     * Set the name of the Kubernetes namespace containing the pods and the configmap (autodetected by default)
     */
    private String kubernetesNamespace;

    /**
     * Set the name of the ConfigMap used to do optimistic locking (defaults to 'leaders').
     */
    private String configMapName;

    /**
     * Set the name of the current pod (autodetected from container host name by default).
     */
    private String podName;

    /**
     * Set the labels used to identify the pods composing the cluster.
     */
    private Map<String, String> clusterLabels;

    /**
     * A jitter factor to apply in order to prevent all pods to call Kubernetes APIs in the same instant.
     */
    private Double jitterFactor;

    /**
     * The default duration of the lease for the current leader.
     */
    private Long leaseDurationMillis;

    /**
     * The deadline after which the leader must stop its services because it may have lost the leadership.
     */
    private Long renewDeadlineMillis;

    /**
     * The time between two subsequent attempts to check and acquire the leadership.
     * It is randomized using the jitter factor.
     */
    private Long retryPeriodMillis;

    /**
     * Custom service attributes.
     */
    private Map<String, Object> attributes;
    
    /**
     * Service lookup order/priority.
     */
    private Integer order;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public Integer getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public void setConnectionTimeoutMillis(Integer connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public String getKubernetesNamespace() {
        return kubernetesNamespace;
    }

    public void setKubernetesNamespace(String kubernetesNamespace) {
        this.kubernetesNamespace = kubernetesNamespace;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public void setConfigMapName(String configMapName) {
        this.configMapName = configMapName;
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

    public Double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(Double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public Long getLeaseDurationMillis() {
        return leaseDurationMillis;
    }

    public void setLeaseDurationMillis(Long leaseDurationMillis) {
        this.leaseDurationMillis = leaseDurationMillis;
    }

    public Long getRenewDeadlineMillis() {
        return renewDeadlineMillis;
    }

    public void setRenewDeadlineMillis(Long renewDeadlineMillis) {
        this.renewDeadlineMillis = renewDeadlineMillis;
    }

    public Long getRetryPeriodMillis() {
        return retryPeriodMillis;
    }

    public void setRetryPeriodMillis(Long retryPeriodMillis) {
        this.retryPeriodMillis = retryPeriodMillis;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
