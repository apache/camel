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
package org.apache.camel.component.huaweicloud.obs;

import com.obs.services.HttpProxyConfiguration;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.obs.models.OBSRegion;
import org.apache.camel.component.huaweicloud.obs.models.ServiceKeys;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * To provide stable, secure, efficient, and easy-to-use cloud storage service on Huawei Cloud
 */
@UriEndpoint(firstVersion = "3.12.0", scheme = "hwcloud-obs", title = "Huawei Object Storage Service (OBS)",
             syntax = "hwcloud-obs:operation",
             category = { Category.CLOUD })
public class OBSEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to be performed", displayName = "Operation", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "OBS service region. This is lower precedence than endpoint based configuration",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "OBS url. Carries higher precedence than region parameter based client initialization",
              displayName = "Endpoint url")
    @Metadata(required = false)
    private String endpoint;

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

    @UriParam(description = "Name of bucket to perform operation on", displayName = "Bucket Name")
    @Metadata(required = false)
    private String bucketName;

    @UriParam(description = "Location of bucket when creating a new bucket", displayName = "Bucket Location")
    @Metadata(required = false)
    private String bucketLocation;

    private ObsClient obsClient;

    public OBSEndpoint() {
    }

    public OBSEndpoint(String uri, String operation, OBSComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    public Producer createProducer() throws Exception {
        return new OBSProducer(this);
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

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getBucketLocation() {
        return bucketLocation;
    }

    public void setBucketLocation(String bucketLocation) {
        this.bucketLocation = bucketLocation;
    }

    public ObsClient getObsClient() {
        return obsClient;
    }

    public void setObsClient(ObsClient obsClient) {
        this.obsClient = obsClient;
    }

    /**
     * Initialize and return a new OBS Client
     *
     * @return
     */
    public ObsClient initClient() {
        if (obsClient != null) {
            return obsClient;
        }

        // check for mandatory AK/SK in ServiceKeys object or in endpoint
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getAuthenticationKey())) {
            throw new IllegalArgumentException("Authentication parameter 'authentication key (AK)' not found");
        }
        if (ObjectHelper.isEmpty(getServiceKeys()) && ObjectHelper.isEmpty(getSecretKey())) {
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        }

        // build client with mandatory region/endpoint parameter. Endpoint takes higher precedence than region
        ObsConfiguration obsConfiguration = new ObsConfiguration();
        if (ObjectHelper.isNotEmpty(getEndpoint())) {
            obsConfiguration.setEndPoint(getEndpoint());
        } else if (ObjectHelper.isNotEmpty(getRegion())) {
            obsConfiguration.setEndPoint(OBSRegion.valueOf(getRegion()));
        } else {
            throw new IllegalArgumentException("Region/endpoint not found");
        }

        // setup proxy information (if needed)
        if (ObjectHelper.isNotEmpty(getProxyHost())
                && ObjectHelper.isNotEmpty(getProxyPort())) {
            HttpProxyConfiguration httpConfig = new HttpProxyConfiguration();
            httpConfig.setProxyAddr(getProxyHost());
            httpConfig.setProxyPort(getProxyPort());

            if (ObjectHelper.isNotEmpty(getProxyUser())) {
                httpConfig.setProxyUName(getProxyUser());
                if (ObjectHelper.isNotEmpty(getProxyPassword())) {
                    httpConfig.setUserPaaswd(getProxyPassword());
                }
            }
            obsConfiguration.setHttpProxy(httpConfig);
        }

        // setup ignore ssl verification
        obsConfiguration.setValidateCertificate(!isIgnoreSslVerification());

        // setup AK/SK credential information. AK/SK provided through ServiceKeys overrides the AK/SK passed through the endpoint
        String auth = getServiceKeys() != null
                ? getServiceKeys().getAuthenticationKey()
                : getAuthenticationKey();
        String secret = getServiceKeys() != null
                ? getServiceKeys().getSecretKey()
                : getSecretKey();

        return new ObsClient(auth, secret, obsConfiguration);
    }
}
