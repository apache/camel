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
import org.jspecify.annotations.Nullable;

/**
 * Configuration for access to Hashicorp Vault Secrets.
 */
public class HashicorpVaultConfiguration extends VaultConfiguration {

    @Metadata(security = "secret")
    private @Nullable String token;
    @Metadata
    private @Nullable String host;
    @Metadata
    private @Nullable String port;
    @Metadata
    private @Nullable String scheme;
    @Metadata
    private boolean cloud;
    @Metadata
    private @Nullable String namespace;
    @Metadata
    private boolean refreshEnabled;
    @Metadata(defaultValue = "60000")
    private long refreshPeriod = 60000;
    @Metadata
    private @Nullable String secrets;

    public @Nullable String getToken() {
        return token;
    }

    /**
     * Token to access hashicorp vault
     */
    public void setToken(@Nullable String token) {
        this.token = token;
    }

    public @Nullable String getHost() {
        return host;
    }

    /**
     * Host to access hashicorp vault
     */
    public void setHost(@Nullable String host) {
        this.host = host;
    }

    public @Nullable String getPort() {
        return port;
    }

    /**
     * Port to access hashicorp vault
     */
    public void setPort(@Nullable String port) {
        this.port = port;
    }

    public @Nullable String getScheme() {
        return scheme;
    }

    /**
     * Scheme to access hashicorp vault
     */
    public void setScheme(@Nullable String scheme) {
        this.scheme = scheme;
    }

    public boolean isCloud() {
        return cloud;
    }

    /**
     * Determine if the Hashicorp Vault is deployed on Hashicorp Cloud or not
     */
    public void setCloud(boolean cloud) {
        this.cloud = cloud;
    }

    public @Nullable String getNamespace() {
        return namespace;
    }

    /**
     * If the Hashicorp Vault instance is deployed on Hashicorp Cloud, this field will determine the namespace
     */
    public void setNamespace(@Nullable String namespace) {
        this.namespace = namespace;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Hashicorp Vault.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking Hashicorp Vault for updated secrets.
     */
    public void setRefreshPeriod(long refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public @Nullable String getSecrets() {
        return secrets;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public void setSecrets(@Nullable String secrets) {
        this.secrets = secrets;
    }
}
