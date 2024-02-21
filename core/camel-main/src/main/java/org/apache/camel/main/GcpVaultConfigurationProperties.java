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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.vault.GcpVaultConfiguration;

/**
 * Configuration for access to GCP Secret.
 */
@Configurer(bootstrap = true)
public class GcpVaultConfigurationProperties extends GcpVaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public GcpVaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    /**
     * The Service Account Key location
     */
    public GcpVaultConfigurationProperties withServiceAccountKey(String serviceAccountKey) {
        setServiceAccountKey(serviceAccountKey);
        return this;
    }

    /**
     * The GCP Project ID
     */
    public GcpVaultConfigurationProperties withProjectId(String projectId) {
        setProjectId(projectId);
        return this;
    }

    /**
     * The GCP Project ID
     */
    public GcpVaultConfigurationProperties withUseDefaultInstance(boolean useDefaultInstance) {
        setUseDefaultInstance(useDefaultInstance);
        return this;
    }

    /**
     * The Pubsub subscriptionName name
     */
    public GcpVaultConfigurationProperties withSubscriptionName(String subscriptionName) {
        setSubscriptionName(subscriptionName);
        return this;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Google.
     */
    public GcpVaultConfigurationProperties withRefreshEnabled(boolean refreshEnabled) {
        setRefreshEnabled(refreshEnabled);
        return this;
    }

    /**
     * The period (millis) between checking Google for updated secrets.
     */
    public GcpVaultConfigurationProperties withRefreshPeriod(long refreshPeriod) {
        setRefreshPeriod(refreshPeriod);
        return this;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public GcpVaultConfigurationProperties withSecrets(String secrets) {
        setSecrets(secrets);
        return this;
    }

}
