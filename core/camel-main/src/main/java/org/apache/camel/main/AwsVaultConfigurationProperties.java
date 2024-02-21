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
import org.apache.camel.vault.AwsVaultConfiguration;

/**
 * Configuration for access to AWS Secret.
 */
@Configurer(bootstrap = true)
public class AwsVaultConfigurationProperties extends AwsVaultConfiguration implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    public AwsVaultConfigurationProperties(MainConfigurationProperties parent) {
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
     * The AWS access key
     */
    public AwsVaultConfigurationProperties withAccessKey(String accessKey) {
        setAccessKey(accessKey);
        return this;
    }

    /**
     * The AWS secret key
     */
    public AwsVaultConfigurationProperties withSecretKey(String secretKey) {
        setSecretKey(secretKey);
        return this;
    }

    /**
     * The AWS region
     */
    public AwsVaultConfigurationProperties withRegion(String region) {
        setRegion(region);
        return this;
    }

    /**
     * Define if we want to use the AWS Default Credentials Provider or not
     */
    public AwsVaultConfigurationProperties withDefaultCredentialsProvider(boolean defaultCredentialsProvider) {
        setDefaultCredentialsProvider(defaultCredentialsProvider);
        return this;
    }

    /**
     * Define if we want to use the AWS Profile Credentials Provider or not
     */
    public AwsVaultConfigurationProperties withProfileCredentialsProvider(boolean profileCredentialsProvider) {
        setProfileCredentialsProvider(profileCredentialsProvider);
        return this;
    }

    /**
     * Define the profile name if we are using the Profile Credentials Provider
     */
    public AwsVaultConfigurationProperties withProfileName(String profileName) {
        setProfileName(profileName);
        return this;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in AWS.
     */
    public AwsVaultConfigurationProperties withRefreshEnabled(boolean refreshEnabled) {
        setRefreshEnabled(refreshEnabled);
        return this;
    }

    /**
     * The period (millis) between checking AWS for updated secrets.
     */
    public AwsVaultConfigurationProperties withRefreshPeriod(long refreshPeriod) {
        setRefreshPeriod(refreshPeriod);
        return this;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public AwsVaultConfigurationProperties withSecrets(String secrets) {
        setSecrets(secrets);
        return this;
    }

}
