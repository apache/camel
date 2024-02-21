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
package org.apache.camel.component.hashicorp.vault;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.springframework.vault.core.VaultTemplate;

@UriParams
public class HashicorpVaultConfiguration implements Cloneable {

    @UriPath
    private String secretsEngine;
    @UriParam
    @Metadata(autowired = true)
    private VaultTemplate vaultTemplate;
    @UriParam
    private String host;
    @UriParam(defaultValue = "8200")
    private String port = "8200";
    @UriParam(defaultValue = "https")
    private String scheme = "https";
    @UriParam
    private String secretPath;
    @UriParam(label = "security", secret = true)
    private String token;
    @UriParam(label = "producer")
    private HashicorpVaultOperation operation = HashicorpVaultOperation.createSecret;

    /**
     * Instance of Vault template
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }

    public void setVaultTemplate(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    /**
     * Vault Name to be used
     */
    public String getSecretsEngine() {
        return secretsEngine;
    }

    public void setSecretsEngine(String secretsEngine) {
        this.secretsEngine = secretsEngine;
    }

    /**
     * Token to be used
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Hashicorp Vault instance host to be used
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Hashicorp Vault instance port to be used
     */
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Hashicorp Vault instance scheme to be used
     */
    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Hashicorp Vault instance secret Path to be used
     */
    public String getSecretPath() {
        return secretPath;
    }

    public void setSecretPath(String secretPath) {
        this.secretPath = secretPath;
    }

    /**
     * Operation to be performed
     */
    public HashicorpVaultOperation getOperation() {
        return operation;
    }

    public void setOperation(HashicorpVaultOperation operation) {
        this.operation = operation;
    }

    // *************************************************
    //
    // *************************************************

    public HashicorpVaultConfiguration copy() {
        try {
            return (HashicorpVaultConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
