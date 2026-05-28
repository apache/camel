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
 * Configuration for access to AWS Secret.
 *
 * @since 3.16
 */
public class AwsVaultConfiguration extends VaultConfiguration {

    @Metadata(security = "secret")
    private @Nullable String accessKey;
    @Metadata(security = "secret")
    private @Nullable String secretKey;
    @Metadata
    private @Nullable String region;
    @Metadata
    private boolean defaultCredentialsProvider;
    @Metadata
    private boolean profileCredentialsProvider;
    @Metadata
    private @Nullable String profileName;
    @Metadata
    private boolean refreshEnabled;
    @Metadata(defaultValue = "30000")
    private long refreshPeriod = 30000;
    @Metadata
    private @Nullable String secrets;
    @Metadata
    private boolean useSqsNotification;
    @Metadata
    private @Nullable String sqsQueueUrl;
    @Metadata
    private boolean overrideEndpoint;
    @Metadata
    private @Nullable String uriEndpointOverride;

    public @Nullable String getAccessKey() {
        return accessKey;
    }

    /**
     * The AWS access key
     */
    public void setAccessKey(@Nullable String accessKey) {
        this.accessKey = accessKey;
    }

    public @Nullable String getSecretKey() {
        return secretKey;
    }

    /**
     * The AWS secret key
     */
    public void setSecretKey(@Nullable String secretKey) {
        this.secretKey = secretKey;
    }

    public @Nullable String getRegion() {
        return region;
    }

    /**
     * The AWS region
     */
    public void setRegion(@Nullable String region) {
        this.region = region;
    }

    public boolean isDefaultCredentialsProvider() {
        return defaultCredentialsProvider;
    }

    /**
     * Define if we want to use the AWS Default Credentials Provider or not
     */
    public void setDefaultCredentialsProvider(boolean defaultCredentialsProvider) {
        this.defaultCredentialsProvider = defaultCredentialsProvider;
    }

    public boolean isProfileCredentialsProvider() {
        return profileCredentialsProvider;
    }

    /**
     * Define if we want to use the AWS Profile Credentials Provider or not
     */
    public void setProfileCredentialsProvider(boolean profileCredentialsProvider) {
        this.profileCredentialsProvider = profileCredentialsProvider;
    }

    public @Nullable String getProfileName() {
        return profileName;
    }

    /**
     * Define the profile name to use if Profile Credentials Provider is selected
     */
    public void setProfileName(@Nullable String profileName) {
        this.profileName = profileName;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in AWS.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking AWS for updated secrets.
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

    public boolean isUseSqsNotification() {
        return useSqsNotification;
    }

    /**
     * Whether to use AWS SQS for secrets updates notification, this will require setting up Eventbridge/Cloudtrail/SQS
     * communication
     */
    public void setUseSqsNotification(boolean useSqsNotification) {
        this.useSqsNotification = useSqsNotification;
    }

    public @Nullable String getSqsQueueUrl() {
        return sqsQueueUrl;
    }

    /**
     * In case of usage of SQS notification this field will specified the Queue URL to use
     */
    public void setSqsQueueUrl(@Nullable String sqsQueueUrl) {
        this.sqsQueueUrl = sqsQueueUrl;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with the
     * uriEndpointOverride option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public @Nullable String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public void setUriEndpointOverride(@Nullable String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }
}
