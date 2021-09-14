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

package org.apache.camel.component.huaweicloud.common.models;

import org.apache.camel.spi.Metadata;
import org.apache.camel.support.HeaderFilterStrategyComponent;

public abstract class HwCloudServiceComponent extends HeaderFilterStrategyComponent {
    @Metadata(description = "Configuration object for cloud service authentication",
              label = "security")
    protected ServiceKeys serviceKeys;

    @Metadata(description = "Access key for the cloud user", label = "security")
    protected String accessKey;

    @Metadata(description = "Secret key for the cloud user", label = "security")
    protected String secretKey;

    @Metadata(description = "Proxy server ip/hostname", label = "proxy")
    protected String proxyHost;

    @Metadata(description = "Proxy server port", label = "proxy")
    protected int proxyPort;

    @Metadata(description = "Proxy authentication user", label = "proxy")
    protected String proxyUser;

    @Metadata(description = "Proxy authentication password", label = "proxy")
    protected String proxyPassword;

    @Metadata(description = "Ignore SSL verification",
              defaultValue = "false", label = "security")
    protected boolean ignoreSslVerification = false;

    @Metadata(description = "Cloud project id", label = "producer")
    protected String projectId;

    @Metadata(description = "Cloud service region. This is lower precedence than endpoint based configuration.",
              label = "producer")
    protected String region;

    @Metadata(
              description = "Fully qualified Cloud service url. Carries higher precedence than region based configuration.",
              label = "producer")
    protected String endpoint;

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

}
