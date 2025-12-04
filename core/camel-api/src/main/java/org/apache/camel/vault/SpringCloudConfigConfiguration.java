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
 * Configuration for Spring Cloud Config
 */
public class SpringCloudConfigConfiguration extends VaultConfiguration {

    @Metadata(secret = true)
    private String password;

    @Metadata(secret = true, defaultValue = "user")
    private String username = "user";

    @Metadata(secret = true)
    private String token;

    @Metadata(defaultValue = "http://localhost:8888", description = "Comma separated list of Spring Config Server URIs")
    private String uris = "http://localhost:8888";

    @Metadata
    private String label;

    @Metadata
    private String profile;

    @Metadata
    private boolean refreshEnabled;

    @Metadata(defaultValue = "30000")
    private long refreshPeriod = 30000;

    public String getPassword() {
        return password;
    }

    /**
     * Password for Spring Config Server
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for Spring Config Server
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getUris() {
        return uris;
    }

    /**
     * Comma separated list of Spring Config Server URIs
     */
    public void setUris(String uris) {
        this.uris = uris;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Spring Config Server.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking Spring Config Server for updated secrets.
     */
    public void setRefreshPeriod(long refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public String getToken() {
        return token;
    }

    /**
     * Authentication token for the Config Server
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Config Server label to use (e.g., git branch)
     */
    public void setLabel(String label) {
        this.label = label;
    }

    public String getProfile() {
        return profile;
    }

    /**
     * Configuration profile to use
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }
}
