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
package org.apache.camel.component.cyberark.vault;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for CyberArk Conjur Vault component
 */
@UriParams
public class CyberArkVaultConfiguration implements Cloneable {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;

    @UriParam(description = "The secret ID to retrieve from CyberArk Conjur")
    private String secretId;

    @UriParam(defaultValue = "getSecret")
    private CyberArkVaultOperations operation = CyberArkVaultOperations.getSecret;

    @UriParam
    @Metadata(required = true)
    private String url;

    @UriParam
    @Metadata(required = true)
    private String account;

    @UriParam(label = "security", secret = true)
    private String username;

    @UriParam(label = "security", secret = true)
    private String password;

    @UriParam(label = "security", secret = true)
    private String apiKey;

    @UriParam(label = "security", secret = true)
    private String authToken;

    @UriParam(defaultValue = "true")
    private boolean verifySsl = true;

    @UriParam
    private String certificatePath;

    @UriParam
    private ConjurClient conjurClient;

    public String getLabel() {
        return label;
    }

    /**
     * Logical name
     */
    public void setLabel(String label) {
        this.label = label;
    }

    public String getSecretId() {
        return secretId;
    }

    /**
     * The secret ID to retrieve from CyberArk Conjur
     */
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public CyberArkVaultOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform. It can be getSecret or createSecret
     */
    public void setOperation(CyberArkVaultOperations operation) {
        this.operation = operation;
    }

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
     * The username for authentication with CyberArk Conjur
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for authentication with CyberArk Conjur
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The API key for authentication with CyberArk Conjur
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * Pre-authenticated token to use for CyberArk Conjur
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Whether to verify SSL certificates when connecting to CyberArk Conjur
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

    public ConjurClient getConjurClient() {
        return conjurClient;
    }

    /**
     * Reference to a ConjurClient instance in the registry
     */
    public void setConjurClient(ConjurClient conjurClient) {
        this.conjurClient = conjurClient;
    }

    // *************************************************
    //
    // *************************************************

    public CyberArkVaultConfiguration copy() {
        try {
            return (CyberArkVaultConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
