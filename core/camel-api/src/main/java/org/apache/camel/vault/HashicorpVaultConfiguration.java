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
 * Configuration for access to Hashicorp Vault Secrets.
 */
public class HashicorpVaultConfiguration extends VaultConfiguration {

    @Metadata(secret = true)
    private String token;
    @Metadata
    private String host;
    @Metadata
    private String port;
    @Metadata
    private String scheme;
    @Metadata
    private boolean cloud;
    @Metadata
    private String namespace;

    public String getToken() {
        return token;
    }

    /**
     * Token to access hashicorp vault
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getHost() {
        return host;
    }

    /**
     * Host to access hashicorp vault
     */
    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    /**
     * Port to access hashicorp vault
     */
    public void setPort(String port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    /**
     * Scheme to access hashicorp vault
     */
    public void setScheme(String scheme) {
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

    public String getNamespace() {
        return namespace;
    }

    /**
     * If the Hashicorp Vault instance is deployed on Hashicorp Cloud, this field will determine the namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
