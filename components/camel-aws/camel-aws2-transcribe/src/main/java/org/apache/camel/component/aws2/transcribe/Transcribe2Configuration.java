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

    @UriPath(description = "A logical name of the endpoint, which you choose freely. The producer does not use it: the"
                           + " job, vocabulary, filter or model to act on is given in headers.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.transcribe.TranscribeClient. When this option"
                            + " is set, the credentials, region, proxy, endpoint override and trustAllCertificates options are"
                            + " not used. The endpoint does not close this client, but the component closes a client that is"
                            + " set on the component when the component stops.")
    private TranscribeClient transcribeClient;
    @UriParam(label = "security", security = "secret",
              description = "The access key ID of the AWS credentials, used together with the secretKey option. If not set,"
                            + " the aws.accessKeyId system property or the AWS_ACCESS_KEY_ID environment variable is used. It"
                            + " is required, unless transcribeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String accessKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret access key of the AWS credentials, used together with the accessKey option. If not"
                            + " set, the aws.secretKey system property or the AWS_SECRET_ACCESS_KEY environment variable is"
                            + " used. It is required, unless transcribeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String secretKey;
    @UriParam(label = "security", security = "secret",
              description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.")
    private String sessionToken;
    @UriParam(description = "The AWS region of Amazon Transcribe, as a region ID such as eu-west-1, which is the value of"
                            + " Region.EU_WEST_1.id(). If not set, the aws.region system property or the AWS_REGION environment"
                            + " variable is used, and else the default region provider chain of the AWS SDK.")
    private String region;
    @UriParam(defaultValue = "false",
              description = "Whether to send the requests to the URI of the uriEndpointOverride option instead of the"
                            + " endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint to use instead of the endpoint of the region, for example"
                            + " http://localhost:4566 for a local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(security = "insecure:ssl", defaultValue = "false",
              description = "Whether the HTTP client of Amazon Transcribe trusts every TLS certificate without checking it."
                            + " It applies to any endpoint, not only to an overridden one, so use it only for testing, for"
                            + " example against a local emulator.")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Whether to load the credentials with the default credentials provider chain of the AWS SDK,"
                            + " which looks in system properties, environment variables, the shared credentials and config"
                            + " files, and the container or instance role. It takes precedence over the other credentials"
                            + " options.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(defaultValue = "false",
              description = "Whether to load the credentials from a profile of the AWS shared credentials and config files,"
                            + " the one of the profileCredentialsName option. It is not used when useDefaultCredentialsProvider"
                            + " is true, and it takes precedence over the session and static credentials.")
    private boolean useProfileCredentialsProvider;
    @UriParam(defaultValue = "false",
              description = "Whether to use temporary credentials made of the accessKey, secretKey and sessionToken options,"
                            + " for example those of an assumed IAM role. It is not used when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private boolean useSessionCredentials;
    @UriParam(description = "The name of the profile in the AWS shared credentials and config files, used when"
                            + " useProfileCredentialsProvider is true. If not set, the AWS SDK uses its default profile, which"
                            + " the AWS_PROFILE environment variable can change.")
    private String profileCredentialsName;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "This option has no effect: the Transcribe client uses HTTPS, or the scheme of"
                            + " uriEndpointOverride when overrideEndpoint is true. For the protocol of the proxy, use"
                            + " proxyProtocol.")
    @Metadata(deprecationNote = "Not used: nothing reads this option when the Transcribe client is built")
    @Deprecated
    private Protocol protocol = Protocol.HTTPS;
    @UriParam(label = "producer",
              description = "The operation that the producer performs. It is required, as no header replaces it: a"
                            + " producer without an operation fails the exchange.")
    private Transcribe2Operations operation;
    @UriParam(defaultValue = "false",
              description = "Whether the body is the request object of the AWS SDK for the operation, such as a"
                            + " StartTranscriptionJobRequest, which the producer sends as it is. The headers that give the"
                            + " input of the operation are then not used, and a body of another type fails the exchange.")
    private boolean pojoRequest;
    @UriParam(description = "The host name of the proxy that the HTTP client of Amazon Transcribe connects through. The"
                            + " proxy is used only when the proxyPort option is set too.")
    private String proxyHost;
    @UriParam(description = "The port of the proxy that the HTTP client of Amazon Transcribe connects through. The proxy is"
                            + " used only when the proxyHost option is set too.")
    private Integer proxyPort;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol that the HTTP client of Amazon Transcribe uses to connect to the proxy. It is"
                            + " used only when the proxyHost and proxyPort options are set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "security", security = "secret",
              description = "The user name for the proxy. It is not used: the Transcribe client that the component builds"
                            + " does not authenticate to the proxy.")
    private String proxyUsername;
    @UriParam(label = "security", security = "secret",
              description = "The password for the proxy. It is not used: the Transcribe client that the component builds"
                            + " does not authenticate to the proxy.")
    private String proxyPassword;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public TranscribeClient getTranscribeClient() {
        return transcribeClient;
    }

    public void setTranscribeClient(TranscribeClient transcribeClient) {
        this.transcribeClient = transcribeClient;
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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    @Deprecated
    public Protocol getProtocol() {
        return protocol;
    }

    @Deprecated
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public Transcribe2Operations getOperation() {
        return operation;
    }

    public void setOperation(Transcribe2Operations operation) {
        this.operation = operation;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

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
