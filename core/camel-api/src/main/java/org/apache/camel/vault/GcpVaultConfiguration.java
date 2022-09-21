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
package org.apache.camel.vault;

import org.apache.camel.spi.Metadata;

/**
 * Configuration for access to GCP Secret Manager.
 */
public class GcpVaultConfiguration extends VaultConfiguration {

    @Metadata(secret = true)
    private String serviceAccountKey;
    @Metadata
    private String projectId;
    @Metadata
    private boolean useDefaultInstance;
    @Metadata
    private String subscriptionName;
    @Metadata
    private boolean refreshEnabled;
    @Metadata(defaultValue = "30000")
    private long refreshPeriod = 30000;
    @Metadata
    private String secrets;

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * The Service Account Key location
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * The GCP Project ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public boolean isUseDefaultInstance() {
        return useDefaultInstance;
    }

    /**
     * Define if we want to use the GCP Client Default Instance or not
     */
    public void setUseDefaultInstance(boolean useDefaultInstance) {
        this.useDefaultInstance = useDefaultInstance;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    /**
     * Define the Google Pubsub subscription Name to be used when checking for updates
     */
    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in AWS.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking Google for updated secrets.
     */
    public void setRefreshPeriod(long refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public String getSecrets() {
        return secrets;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public void setSecrets(String secrets) {
        this.secrets = secrets;
    }
}
