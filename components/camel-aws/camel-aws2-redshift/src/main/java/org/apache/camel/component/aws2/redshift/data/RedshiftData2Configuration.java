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
package org.apache.camel.component.aws2.redshift.data;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;

@UriParams
public class RedshiftData2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A logical name of the endpoint, which you choose freely. The producer does not use it.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "The operation that the producer performs. The CamelAwsRedshiftDataOperation header replaces this"
                            + " option.")
    @Metadata(required = true)
    private RedshiftData2Operations operation;
    @UriParam(label = "security", security = "secret",
              description = "The access key ID of the AWS credentials. The client uses it with secretKey as static credentials,"
                            + " or with secretKey and sessionToken when useSessionCredentials is true.")
    private String accessKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret access key of the AWS credentials, which the client uses together with accessKey.")
    private String secretKey;
    @UriParam(label = "security", security = "secret",
              description = "The session token of temporary AWS credentials, for example from assuming an IAM role. The client"
                            + " uses it only when useSessionCredentials is true, together with accessKey and secretKey.")
    private String sessionToken;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The AWS region of the Redshift Data API, as a lowercase region ID such as eu-west-1, which is the"
                            + " value of Region.EU_WEST_1.id(). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, such as the aws.region system property or the AWS_REGION environment variable.")
    private String region;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol that the client uses to connect to the proxy. It is used only when proxyHost and"
                            + " proxyPort are set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy",
              description = "The host name of the proxy that the client sends its requests through. The proxy is used only when"
                            + " proxyPort is set as well.")
    private String proxyHost;
    @UriParam(label = "proxy",
              description = "The port of the proxy that the client sends its requests through. The proxy is used only when"
                            + " proxyHost is set as well.")
    private Integer proxyPort;
    @UriParam(label = "advanced",
              description = "To use a custom software.amazon.awssdk.services.redshiftdata.RedshiftDataClient. When this option is"
                            + " set or autowired, the endpoint does not build a client of its own, so the credentials, region,"
                            + " proxy and endpoint override options are not used. The endpoint does not close a client that is"
                            + " set or autowired.")
    @Metadata(autowired = true)
    private RedshiftDataClient awsRedshiftDataClient;
    @UriParam(defaultValue = "false",
              description = "Whether the message body is the request of the AWS SDK for the operation, such as an"
                            + " ExecuteStatementRequest for executeStatement, which the producer sends as it is. If true, the"
                            + " headers are not used and a body of another type fails the exchange.")
    private boolean pojoRequest;
    @UriParam(security = "insecure:ssl", defaultValue = "false",
              description = "Whether the HTTP client trusts all TLS certificates without validating them. It applies to every"
                            + " connection of the client, also when the endpoint is not overridden.")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Whether the client sends its requests to the URI of uriEndpointOverride instead of the endpoint of"
                            + " the AWS region. Without uriEndpointOverride this option has no effect.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI that the client sends its requests to, for example http://localhost:4566 for a local"
                            + " emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(defaultValue = "false",
              description = "Whether the client loads the credentials with the default credentials provider chain of the AWS"
                            + " SDK, which looks in system properties, environment variables, the profile files, and the"
                            + " container and instance metadata. It takes precedence over the other credentials options.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(defaultValue = "false",
              description = "Whether the client loads the credentials from a profile of the AWS config and credentials files,"
                            + " the one that profileCredentialsName names. It takes precedence over useSessionCredentials,"
                            + " accessKey and secretKey, but not over useDefaultCredentialsProvider.")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether the client uses temporary session credentials made of accessKey, secretKey and"
                            + " sessionToken, for example after assuming an IAM role. If sessionToken is not set, the client"
                            + " uses accessKey and secretKey as static credentials.")
    private boolean useSessionCredentials;
    @UriParam(description = "The name of the profile that the client loads when useProfileCredentialsProvider is true. If not"
                            + " set, the AWS SDK uses the profile that the AWS_PROFILE environment variable or the aws.profile"
                            + " system property names, or else the profile named default.")
    private String profileCredentialsName;

    public RedshiftDataClient getAwsRedshiftDataClient() {
        return awsRedshiftDataClient;
    }

    public void setAwsRedshiftDataClient(RedshiftDataClient awsRedshiftDataClient) {
        this.awsRedshiftDataClient = awsRedshiftDataClient;
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

    public RedshiftData2Operations getOperation() {
        return operation;
    }

    public void setOperation(RedshiftData2Operations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
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

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
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

    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
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

    // *************************************************
    //
    // *************************************************

    public RedshiftData2Configuration copy() {
        try {
            return (RedshiftData2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
