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
import org.apache.camel.vault.CyberArkVaultConfiguration;

/**
 * Configuration for access to Hashicorp Vault Secret.
 */
@Configurer(extended = true)
public class CyberArkVaultConfigurationProperties extends CyberArkVaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public CyberArkVaultConfigurationProperties(MainConfigurationProperties parent) {
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
     * The Cyberark Conjur Vault Auth token
     */
    public CyberArkVaultConfigurationProperties withAuthToken(String authToken) {
        setAuthToken(authToken);
        return this;
    }

    /**
     * The Cyberark Conjur Vault instance url
     */
    public CyberArkVaultConfigurationProperties withUrl(String url) {
        setUrl(url);
        return this;
    }

    /**
     * The Cyberark Conjur Vault account
     */
    public CyberArkVaultConfigurationProperties withAccount(String account) {
        setAccount(account);
        return this;
    }

    /**
     * The Cyberark Conjur Vault username
     */
    public CyberArkVaultConfigurationProperties withUsername(String username) {
        setUsername(username);
        return this;
    }

    /**
     * The Cyberark Conjur Vault password
     */
    public CyberArkVaultConfigurationProperties withPassword(String password) {
        setPassword(password);
        return this;
    }

    /**
     * The Cyberark Conjur Vault Api Key
     */
    public CyberArkVaultConfigurationProperties withApiKey(String apiKey) {
        withApiKey(apiKey);
        return this;
    }

    /**
     * The Cyberark Conjur Vault Verify SSL setting
     */
    public CyberArkVaultConfigurationProperties withVerifySSL(boolean verifySSL) {
        withVerifySSL(verifySSL);
        return this;
    }

    /**
     * The Cyberark Conjur Vault Certificate Path
     */
    public CyberArkVaultConfigurationProperties withCertificatePath(String certificatePath) {
        withCertificatePath(certificatePath);
        return this;
    }

}
