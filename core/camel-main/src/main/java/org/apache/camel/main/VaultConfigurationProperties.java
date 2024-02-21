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
import org.apache.camel.vault.VaultConfiguration;

public class VaultConfigurationProperties extends VaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;
    private AwsVaultConfigurationProperties aws;
    private GcpVaultConfigurationProperties gcp;
    private AzureVaultConfigurationProperties azure;
    private HashicorpVaultConfigurationProperties hashicorp;

    public VaultConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
        if (aws != null) {
            aws.close();
        }
        if (gcp != null) {
            gcp.close();
        }
        if (azure != null) {
            azure.close();
        }
        if (hashicorp != null) {
            hashicorp.close();
        }
    }

    // getter and setters
    // --------------------------------------------------------------

    // these are inherited from the parent class

    // fluent builders
    // --------------------------------------------------------------

    @Override
    public AwsVaultConfigurationProperties aws() {
        if (aws == null) {
            aws = new AwsVaultConfigurationProperties(parent);
        }
        return aws;
    }

    @Override
    public GcpVaultConfigurationProperties gcp() {
        if (gcp == null) {
            gcp = new GcpVaultConfigurationProperties(parent);
        }
        return gcp;
    }

    @Override
    public AzureVaultConfigurationProperties azure() {
        if (azure == null) {
            azure = new AzureVaultConfigurationProperties(parent);
        }
        return azure;
    }

    @Override
    public HashicorpVaultConfigurationProperties hashicorp() {
        if (hashicorp == null) {
            hashicorp = new HashicorpVaultConfigurationProperties(parent);
        }
        return hashicorp;
    }
}
