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
package org.apache.camel.component.aws2.transcribe;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

@UriParams
public class Transcribe2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    private TranscribeClient transcribeClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam
    private String region;
    @UriParam(defaultValue = "false")
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(defaultValue = "true")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false")
    private boolean useDefaultCredentialsProvider;
    @UriParam(defaultValue = "false")
    private boolean useProfileCredentialsProvider;
    @UriParam(defaultValue = "false")
    private boolean useSessionCredentials;
    @UriParam
    private String profileCredentialsName;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol protocol = Protocol.HTTPS;
    @UriParam(label = "producer")
    private Transcribe2Operations operation;
    @UriParam(defaultValue = "false")
    private boolean pojoRequest;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "security", secret = true)
    private String proxyUsername;
    @UriParam(label = "security", secret = true)
    private String proxyPassword;

    /**
     * Logical name
     */
    public String getLabel() {
        return label;
    }

    /**
     * Logical name
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * To use a existing configured AWS Transcribe as client
     */
    public TranscribeClient getTranscribeClient() {
        return transcribeClient;
    }

    /**
     * To use a existing configured AWS Transcribe as client
     */
    public void setTranscribeClient(TranscribeClient transcribeClient) {
        this.transcribeClient = transcribeClient;
    }

    /**
     * Amazon AWS Access Key
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Amazon AWS Session Token used when the user needs to assume a IAM role
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Amazon AWS Session Token used when the user needs to assume a IAM role
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    /**
     * The region in which Transcribe client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public String getRegion() {
        return region;
    }

    /**
     * The region in which Transcribe client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with uriEndpointOverride
     * option
     */
    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with uriEndpointOverride
     * option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    /**
     * Set whether the Transcribe client should expect to load credentials through a default credentials provider or to
     * expect static credentials to be passed in.
     */
    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    /**
     * Set whether the Transcribe client should expect to load credentials through a default credentials provider or to
     * expect static credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    /**
     * Set whether the Transcribe client should expect to load credentials through a profile credentials provider.
     */
    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    /**
     * Set whether the Transcribe client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    /**
     * Set whether the Transcribe client should expect to use Session Credentials. This is useful in a situation in
     * which the user needs to assume a IAM role for doing the operations.
     */
    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Transcribe client should expect to use Session Credentials. This is useful in a situation in
     * which the user needs to assume a IAM role for doing the operations.
     */
    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    /**
     * If using a profile credentials provider this parameter will set the profile name.
     */
    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider this parameter will set the profile name.
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    /**
     * To define a proxy protocol when instantiating the Transcribe client
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * To define a proxy protocol when instantiating the Transcribe client
     */
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    /**
     * The operation to perform
     */
    public Transcribe2Operations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(Transcribe2Operations operation) {
        this.operation = operation;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    /**
     * To define a proxy host when instantiating the Transcribe client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Transcribe client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the Transcribe client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Transcribe client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * To define a proxy protocol when instantiating the Transcribe client
     */
    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Transcribe client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    /**
     * To define a proxy username when instantiating the Transcribe client
     */
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * To define a proxy username when instantiating the Transcribe client
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * To define a proxy password when instantiating the Transcribe client
     */
    public String getProxyPassword() {
        return proxyPassword;
    }

    /**
     * To define a proxy password when instantiating the Transcribe client
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    @Override
    public Transcribe2Configuration clone() {
        try {
            return (Transcribe2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
