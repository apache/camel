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
package org.apache.camel.component.huaweicloud.smn;

import java.util.concurrent.ExecutorService;

import com.huaweicloud.sdk.smn.v2.SmnClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * To broadcast messages and connect cloud services through notifications on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "hwcloud-smn", title = "Huawei Simple Message Notification (SMN)",
             syntax = "hwcloud-smn:smnService",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
public class SimpleNotificationEndpoint extends DefaultEndpoint {

    @UriPath(description = "Name of SMN service to invoke", displayName = "Service name", label = "producer")
    @Metadata(required = true)
    private String smnService;

    @UriParam(description = "Name of operation to perform", displayName = "Operation name", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Configuration",
              secret = true)
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "Access key for the cloud user", displayName = "API access key (AK)", secret = true)
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "API secret key (SK)", secret = true)
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "Cloud project ID", displayName = "Project ID", secret = false)
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "Proxy server ip/hostname", displayName = "Proxy server host", secret = false)
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "Proxy server port", displayName = "Proxy server port", secret = false)
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "Proxy authentication user", displayName = "Proxy user", secret = true)
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "Proxy authentication password", displayName = "Proxy password", secret = true)
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "SMN service region. This is lower precedence than endpoint based configuration",
              displayName = "Service region", secret = false)
    @Metadata(required = true)
    private String region;

    @UriParam(description = "Fully qualified smn service url. Carries higher precedence than region parameter based client initialization",
              displayName = "Service endpoint", secret = false)
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "TTL for published message", displayName = "Message TTL", secret = false, defaultValue = "3600")
    @Metadata(required = false)
    private int messageTtl = 3600;

    @UriParam(description = "Ignore SSL verification", displayName = "SSL Verification Ignored", secret = false,
              defaultValue = "false")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    private SmnClient smnClient;

    public SimpleNotificationEndpoint() {
    }

    public SimpleNotificationEndpoint(String uri, String smnService, SimpleNotificationComponent component) {
        super(uri, component);
        this.smnService = smnService;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SimpleNotificationProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public String getSmnService() {
        return smnService;
    }

    public void setSmnService(String smnService) {
        this.smnService = smnService;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public ServiceKeys getCredentials() {
        return serviceKeys;
    }

    public void setCredentials(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

    public int getMessageTtl() {
        return messageTtl;
    }

    public void setMessageTtl(int messageTtl) {
        this.messageTtl = messageTtl;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public SmnClient getSmnClient() {
        return smnClient;
    }

    public void setSmnClient(SmnClient smnClient) {
        this.smnClient = smnClient;
    }

    public ExecutorService createExecutor() {
        // TODO: Delete me when you implemented your custom component
        return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "SimpleNotificationConsumer");
    }
}
