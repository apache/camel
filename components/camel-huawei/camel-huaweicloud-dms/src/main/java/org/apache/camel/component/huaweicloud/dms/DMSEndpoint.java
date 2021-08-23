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
package org.apache.camel.component.huaweicloud.dms;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.dms.models.DmsRegion;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * To integrate with a fully managed, high-performance message queuing service on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.12.0", scheme = "hwcloud-dms", title = "Huawei Distributed Message Service (DMS)",
             syntax = "hwcloud-dms:operation",
             category = { Category.CLOUD }, producerOnly = true)
public class DMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to be performed", displayName = "Operation", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "DMS service region", displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "DMS url. Carries higher precedence than region parameter based client initialization",
              displayName = "Service endpoint", secret = false)
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "Cloud project ID", displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "Proxy server ip/hostname", displayName = "Proxy server host")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "Proxy server port", displayName = "Proxy server port")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "Proxy authentication user", displayName = "Proxy user", secret = true)
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "Proxy authentication password", displayName = "Proxy password", secret = true)
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Ignore SSL verification", displayName = "SSL Verification Ignored", defaultValue = "false")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Configuration",
              secret = true)
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "Authentication key for the cloud user", displayName = "API authentication key (AK)", secret = true)
    @Metadata(required = true)
    private String authenticationKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "API secret key (SK)", secret = true)
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "The message engine. Either kafka or rabbitmq. If the parameter is not specified, all instances will be queried",
              displayName = "Engine type",
              enums = "kafka,rabbitmq")
    @Metadata(required = false)
    private String engine;

    @UriParam(description = "The id of the instance. This option is mandatory when deleting or querying an instance",
              displayName = "Instance id")
    @Metadata(required = false)
    private String instanceId;

    private DmsClient dmsClient;

    public DMSEndpoint() {
    }

    public DMSEndpoint(String uri, String operation, DMSComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    public Producer createProducer() throws Exception {
        return new DMSProducer(this);
    }

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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

    public String getAuthenticationKey() {
        return authenticationKey;
    }

    public void setAuthenticationKey(String authenticationKey) {
        this.authenticationKey = authenticationKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public DmsClient getDmsClient() {
        return dmsClient;
    }

    public void setDmsClient(DmsClient dmsClient) {
        this.dmsClient = dmsClient;
    }

    public DmsClient initClient() {
        if (dmsClient != null) {
            return dmsClient;
        }

        // check for mandatory AK/SK in ServiceKeys object or in endpoint
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getAuthenticationKey())) {
            throw new IllegalArgumentException("Authentication parameter 'authentication key (AK)' not found");
        }
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getSecretKey())) {
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        }

        // setup AK/SK credential information. AK/SK provided through ServiceKeys overrides the AK/SK passed through the endpoint
        BasicCredentials auth = new BasicCredentials()
                .withAk(getServiceKeys() != null
                        ? getServiceKeys().getAuthenticationKey()
                        : getAuthenticationKey())
                .withSk(getServiceKeys() != null
                        ? getServiceKeys().getSecretKey()
                        : getSecretKey())
                .withProjectId(getProjectId());

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

        // Build DmsClient with mandatory region parameter.
        if (ObjectHelper.isNotEmpty(getEndpoint())) {
            return DmsClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withEndpoint(getEndpoint())
                    .build();
        } else {
            return DmsClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(DmsRegion.valueOf(getRegion()))
                    .build();
        }
    }
}
