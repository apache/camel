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
 * Configuration for access to CyberArk Conjur Vault.
 */
public class CyberArkVaultConfiguration extends VaultConfiguration {

    @Metadata
    private String url;
    @Metadata
    private String account;
    @Metadata(secret = true)
    private String username;
    @Metadata(secret = true)
    private String password;
    @Metadata(secret = true)
    private String apiKey;
    @Metadata
    private String authToken;
    @Metadata
    private boolean verifySsl = true;
    @Metadata
    private String certificatePath;
    @Metadata
    private String secrets;

    public String getUrl() {
        return url;
    }

    /**
     * The CyberArk Conjur instance URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public String getAccount() {
        return account;
    }

    /**
     * The CyberArk Conjur account name
     */
    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username for authentication
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The API key for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * Pre-authenticated token to use
     */
    public void setAuthToken(String authToken) {
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

    public String getCertificatePath() {
        return certificatePath;
    }

    /**
     * Path to the SSL certificate for verification
     */
    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
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
