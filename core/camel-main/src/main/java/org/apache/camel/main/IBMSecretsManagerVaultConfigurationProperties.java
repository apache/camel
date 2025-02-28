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
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;

/**
 * Configuration for access to IBM Secrets Manager Vault Secret.
 */
@Configurer(extended = true)
public class IBMSecretsManagerVaultConfigurationProperties extends IBMSecretsManagerVaultConfiguration
        implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public IBMSecretsManagerVaultConfigurationProperties(MainConfigurationProperties parent) {
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
     * The IBM Secrets Manager Vault token
     */
    public IBMSecretsManagerVaultConfigurationProperties withToken(String token) {
        setToken(token);
        return this;
    }

    /**
     * The IBM Secrets Manager service url
     */
    public IBMSecretsManagerVaultConfigurationProperties withServiceUrl(String serviceUrl) {
        setServiceUrl(serviceUrl);
        return this;
    }

}
