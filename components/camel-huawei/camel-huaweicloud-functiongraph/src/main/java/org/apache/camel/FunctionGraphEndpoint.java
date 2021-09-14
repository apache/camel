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
package org.apache.camel;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.region.FunctionGraphRegion;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * To call serverless functions on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.11.0", scheme = "hwcloud-functiongraph", title = "Huawei FunctionGraph",
             syntax = "hwcloud-functiongraph:operation",
             category = { Category.CLOUD, Category.SERVERLESS }, producerOnly = true)
public class FunctionGraphEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to be performed", displayName = "Operation", label = "producer", secret = false)
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "FunctionGraph service region. This is lower precedence than endpoint based configuration",
              displayName = "Service region", secret = false)
    @Metadata(required = true)
    private String region;

    @UriParam(description = "Cloud project ID", displayName = "Project ID", secret = false)
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "Functions that can be logically grouped together",
              displayName = "Function package", secret = false, defaultValue = FunctionGraphConstants.DEFAULT_FUNCTION_PACKAGE)
    @Metadata(required = false)
    private String functionPackage;

    @UriParam(description = "Name of the function to invoke",
              displayName = "Function name", secret = false)
    @Metadata(required = false)
    private String functionName;

    @UriParam(description = "Proxy server ip/hostname", displayName = "Proxy server host", secret = false, label = "proxy")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "Proxy server port", displayName = "Proxy server port", secret = false, label = "proxy")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "Proxy authentication user", displayName = "Proxy user", secret = true, label = "proxy")
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "Proxy authentication password", displayName = "Proxy password", secret = true, label = "proxy")
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Ignore SSL verification", displayName = "SSL Verification Ignored", secret = false,
              defaultValue = "false", label = "security")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(description = "FunctionGraph url. Carries higher precedence than region parameter based client initialization",
              displayName = "Service endpoint", secret = false)
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Configuration",
              secret = true)
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "Access key for the cloud user", displayName = "API access key (AK)", secret = true,
              label = "security")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "API secret key (SK)", secret = true,
              label = "security")
    @Metadata(required = true)
    private String secretKey;

    private FunctionGraphClient functionGraphClient;

    public FunctionGraphEndpoint() {
    }

    public FunctionGraphEndpoint(String uri, String operation, FunctionGraphComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    public Producer createProducer() throws Exception {
        return new FunctionGraphProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
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

    public FunctionGraphClient getFunctionGraphClient() {
        return functionGraphClient;
    }

    public void setFunctionGraphClient(FunctionGraphClient functionGraphClient) {
        this.functionGraphClient = functionGraphClient;
    }

    /**
     * Initialize and return a new FunctionGraph Client
     *
     * @return
     */
    public FunctionGraphClient initClient() {
        if (functionGraphClient != null) {
            return functionGraphClient;
        }

        // setup AK/SK credential information. User can input AK/SK through the ServiceKeys class, which, if provided, overrides the AK/SK passed through the endpoint
        BasicCredentials auth = new BasicCredentials()
                .withAk(getServiceKeys() != null
                        ? getServiceKeys().getAccessKey()
                        : getAccessKey())
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

        // if an endpoint url is provided, the FunctionGraphClient will be built with an endpoint. Otherwise, it will be built with the provided region
        if (ObjectHelper.isNotEmpty(getEndpoint())) {
            return FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withEndpoint(getEndpoint())
                    .build();
        } else {
            return FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(FunctionGraphRegion.valueOf(getRegion()))
                    .build();
        }
    }

}
