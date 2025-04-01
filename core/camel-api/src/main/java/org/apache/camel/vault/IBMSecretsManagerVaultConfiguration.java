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
 * Configuration for access to IBM Secrets Manager Vault Secrets.
 */
public class IBMSecretsManagerVaultConfiguration extends VaultConfiguration {

    @Metadata(secret = true)
    private String token;
    @Metadata
    private String serviceUrl;
    @Metadata
    private boolean refreshEnabled;
    @Metadata
    private String secrets;
    @Metadata
    private String eventStreamTopic;
    @Metadata
    private String eventStreamBootstrapServers;
    @Metadata
    private String eventStreamUsername;
    @Metadata
    private String eventStreamPassword;
    @Metadata
    private String eventStreamGroupId;
    @Metadata(defaultValue = "3000")
    private long eventStreamConsumerPollTimeout = 3000;

    public String getToken() {
        return token;
    }

    /**
     * Token to access IBM Secrets Manager vault
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * Service URL to access IBM Secrets Manager vault
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in IBM.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
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

    public String getEventStreamTopic() {
        return eventStreamTopic;
    }

    /**
     * Specify the topic name for consuming notification on IBM Event Stream
     */
    public void setEventStreamTopic(String eventStreamTopic) {
        this.eventStreamTopic = eventStreamTopic;
    }

    public String getEventStreamBootstrapServers() {
        return eventStreamBootstrapServers;
    }

    /**
     * Specify the Bootstrap servers for consuming notification on IBM Event Stream. Multiple servers can be separated
     * by comma.
     */
    public void setEventStreamBootstrapServers(String eventStreamBootstrapServers) {
        this.eventStreamBootstrapServers = eventStreamBootstrapServers;
    }

    public String getEventStreamUsername() {
        return eventStreamUsername;
    }

    /**
     * Specify the username to access IBM Event Stream
     */
    public void setEventStreamUsername(String eventStreamUsername) {
        this.eventStreamUsername = eventStreamUsername;
    }

    public String getEventStreamPassword() {
        return eventStreamPassword;
    }

    /**
     * Specify the password to access IBM Event Stream
     */
    public void setEventStreamPassword(String eventStreamPassword) {
        this.eventStreamPassword = eventStreamPassword;
    }

    public String getEventStreamGroupId() {
        return eventStreamGroupId;
    }

    /**
     * Specify the Consumer Group ID to access IBM Event Stream
     */
    public void setEventStreamGroupId(String eventStreamGroupId) {
        this.eventStreamGroupId = eventStreamGroupId;
    }

    public long getEventStreamConsumerPollTimeout() {
        return eventStreamConsumerPollTimeout;
    }

    /**
     * Specify the Consumer Poll Timeout while consuming from IBM Event Stream Topic
     */
    public void setEventStreamConsumerPollTimeout(long eventStreamConsumerPollTimeout) {
        this.eventStreamConsumerPollTimeout = eventStreamConsumerPollTimeout;
    }
}
