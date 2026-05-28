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
 * Configuration for access to CyberArk Conjur Vault.
 *
 * @since 4.17
 */
public class CyberArkVaultConfiguration extends VaultConfiguration {

    @Metadata
    private @Nullable String url;
    @Metadata
    private @Nullable String account;
    @Metadata(security = "secret")
    private @Nullable String username;
    @Metadata(security = "secret")
    private @Nullable String password;
    @Metadata(security = "secret")
    private @Nullable String apiKey;
    @Metadata(security = "secret")
    private @Nullable String authToken;
    @Metadata
    private boolean verifySsl = true;
    @Metadata
    private @Nullable String certificatePath;
    @Metadata
    private @Nullable String secrets;

    public @Nullable String getUrl() {
        return url;
    }

    /**
     * The CyberArk Conjur instance URL
     */
    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    public @Nullable String getAccount() {
        return account;
    }

    /**
     * The CyberArk Conjur account name
     */
    public void setAccount(@Nullable String account) {
        this.account = account;
    }

    public @Nullable String getUsername() {
        return username;
    }

    /**
     * The username for authentication
     */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    /**
     * The password for authentication
     */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public @Nullable String getApiKey() {
        return apiKey;
    }

    /**
     * The API key for authentication
     */
    public void setApiKey(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    public @Nullable String getAuthToken() {
        return authToken;
    }

    /**
     * Pre-authenticated token to use
     */
    public void setAuthToken(@Nullable String authToken) {
        this.authToken = authToken;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Whether to verify SSL certificates
     */
    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public @Nullable String getCertificatePath() {
        return certificatePath;
    }

    /**
     * Path to the SSL certificate for verification
     */
    public void setCertificatePath(@Nullable String certificatePath) {
        this.certificatePath = certificatePath;
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
