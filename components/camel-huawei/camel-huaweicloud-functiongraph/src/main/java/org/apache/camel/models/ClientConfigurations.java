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
package org.apache.camel.models;

import org.apache.camel.FunctionGraphEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientConfigurations {
    private static final Logger LOG = LoggerFactory.getLogger(ClientConfigurations.class.getName());
    private String operation;
    private String region;
    private String projectId;
    private String functionPackage;
    private String functionName;
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPassword;
    private boolean ignoreSslVerification;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String xcffLogType;

    public ClientConfigurations(FunctionGraphEndpoint endpoint) {

        // checking for required region
        if (ObjectHelper.isNotEmpty(endpoint.getEndpoint())) {
            this.setEndpoint(endpoint.getEndpoint());
        } else if (ObjectHelper.isNotEmpty(endpoint.getRegion())) {
            this.setRegion(endpoint.getRegion());
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("No region/endpoint given. Cannot proceed with FunctionGraph operations.");
            }
            throw new IllegalArgumentException("Region/endpoint not found");
        }

        // checking for optional proxy authentication
        if (ObjectHelper.isNotEmpty(endpoint.getProxyHost()) && ObjectHelper.isNotEmpty(endpoint.getProxyPort())) {
            this.setProxyHost(endpoint.getProxyHost());
            this.setProxyPort(endpoint.getProxyPort());
        }
        if (ObjectHelper.isNotEmpty(endpoint.getProxyUser())) {
            this.setProxyUser(endpoint.getProxyUser());
            if (ObjectHelper.isNotEmpty(endpoint.getProxyPassword())) {
                this.setProxyPassword(endpoint.getProxyPassword());
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Proxy password not provided. Continuing without it.");
                }
            }
        }

        // checking for required project id
        if (ObjectHelper.isEmpty(endpoint.getProjectId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No project ID given. Cannot proceed with FunctionGraph operations.");
            }
            throw new IllegalArgumentException("Project ID not found");
        } else {
            this.setProjectId(endpoint.getProjectId());
        }

        // checking for ignore ssl verification
        if (endpoint.isIgnoreSslVerification()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("SSL verification is ignored. This is unsafe in production environment");
            }
        }
        this.setIgnoreSslVerification(endpoint.isIgnoreSslVerification());

        // checking for required cloud AK (access key)
        if (ObjectHelper.isEmpty(endpoint.getAccessKey())
                && ObjectHelper.isEmpty(endpoint.getServiceKeys())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No access key (AK) given. Cannot proceed with FunctionGraph operations");
            }
            throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
        } else {
            this.setAccessKey(ObjectHelper.isNotEmpty(endpoint.getServiceKeys())
                    ? endpoint.getServiceKeys().getAccessKey()
                    : endpoint.getAccessKey());
        }

        // checking for required cloud SK (secret key)
        if (ObjectHelper.isEmpty(endpoint.getSecretKey())
                && ObjectHelper.isEmpty(endpoint.getServiceKeys())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No secret key (SK) given. Cannot proceed with FunctionGraph operations");
            }
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        } else {
            this.setSecretKey(ObjectHelper.isNotEmpty(endpoint.getServiceKeys())
                    ? endpoint.getServiceKeys().getSecretKey()
                    : endpoint.getSecretKey());
        }
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getFunctionPackage() {
        return functionPackage;
    }

    public void setFunctionPackage(String functionPackage) {
        this.functionPackage = functionPackage;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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

    public String getXCffLogType() {
        return xcffLogType;
    }

    public void setXCffLogType(String xcffLogType) {
        this.xcffLogType = xcffLogType;
    }
}
