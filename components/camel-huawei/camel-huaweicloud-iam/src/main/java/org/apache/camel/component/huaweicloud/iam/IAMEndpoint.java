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
package org.apache.camel.component.huaweicloud.iam;

import com.huaweicloud.sdk.core.auth.GlobalCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.region.IamRegion;
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
import org.apache.camel.util.ObjectHelper;

/**
 * To securely manage users on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.11.0", scheme = "hwcloud-iam", title = "Huawei Identity and Access Management (IAM)",
             syntax = "hwcloud-iam:operation",
             category = { Category.CLOUD }, producerOnly = true)
public class IAMEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to be performed", displayName = "Operation", label = "producer", secret = false)
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "IAM service region",
              displayName = "Service region", secret = false)
    @Metadata(required = true)
    private String region;

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

    @UriParam(description = "Ignore SSL verification", displayName = "SSL Verification Ignored", secret = false,
              defaultValue = "false")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

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

    @UriParam(description = "User ID to perform operation with", displayName = "User ID", secret = true)
    @Metadata(required = false)
    private String userId;

    @UriParam(description = "Group ID to perform operation with", displayName = "Group ID", secret = true)
    @Metadata(required = false)
    private String groupId;

    private IamClient iamClient;

    public IAMEndpoint() {
    }

    public IAMEndpoint(String uri, String operation, IAMComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IAMProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public IamClient getIamClient() {
        return iamClient;
    }

    public void setIamClient(IamClient iamClient) {
        this.iamClient = iamClient;
    }

    /**
     * Initialize and return a new IAM Client
     *
     * @return
     */
    public IamClient initClient() {
        if (iamClient != null) {
            return iamClient;
        }

        // check for mandatory AK/SK in ServiceKeys object or in endpoint
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getAccessKey())) {
            throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
        }
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getSecretKey())) {
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        }

        // setup AK/SK credential information. AK/SK provided through ServiceKeys overrides the AK/SK passed through the endpoint
        GlobalCredentials auth = new GlobalCredentials()
                .withAk(getServiceKeys() != null
                        ? getServiceKeys().getAccessKey()
                        : getAccessKey())
                .withSk(getServiceKeys() != null
                        ? getServiceKeys().getSecretKey()
                        : getSecretKey());

        // setup http information (including proxy information if provided)
        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig();
        httpConfig.withIgnoreSSLVerification(isIgnoreSslVerification());
        if (ObjectHelper.isNotEmpty(getProxyHost())
                && ObjectHelper.isNotEmpty(getProxyPort())) {
            httpConfig.withProxyHost(getProxyHost())
                    .withProxyPort(getProxyPort());

            if (ObjectHelper.isNotEmpty(getProxyUser())) {
                httpConfig.withProxyUsername(getProxyUser());
                if (ObjectHelper.isNotEmpty(getProxyPassword())) {
                    httpConfig.withProxyPassword(getProxyPassword());
                }
            }
        }

        // Build IamClient with mandatory region parameter.
        if (ObjectHelper.isNotEmpty(getRegion())) {
            return IamClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(IamRegion.valueOf(getRegion()))
                    .build();
        } else {
            throw new IllegalArgumentException("Region not found");
        }
    }
}
