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
package org.apache.camel.component.aws2.ecs;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.ecs.EcsClient;

@UriParams
public class ECS2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A logical name for the endpoint, which you can choose freely. The component does not use it.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.ecs.EcsClient. When this option is set or"
                            + " autowired, the endpoint does not build a client from the credentials, proxy, region,"
                            + " overrideEndpoint, uriEndpointOverride and trustAllCertificates options, and it does not close"
                            + " the client when it stops.")
    @Metadata(label = "advanced", autowired = true)
    private EcsClient ecsClient;
    @UriParam(description = "The access key ID of the AWS credentials, used together with secretKey. It is required unless"
                            + " ecsClient is set or useDefaultCredentialsProvider, useProfileCredentialsProvider or"
                            + " useSessionCredentials is true.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret access key of the AWS credentials, used together with accessKey. It is required"
                            + " unless ecsClient is set or useDefaultCredentialsProvider, useProfileCredentialsProvider or"
                            + " useSessionCredentials is true.",
              label = "security", security = "secret")
    private String secretKey;

    @UriParam(description = "The session token of temporary AWS credentials, for example from assuming an IAM role. It is"
                            + " used only when useSessionCredentials is true, together with accessKey and secretKey.",
              label = "security", security = "secret")
    private String sessionToken;
    @UriParam(description = "The operation that the producer performs. The CamelAwsECSOperation header replaces this option.")
    private ECS2Operations operation;
    @UriParam(description = "The protocol of the proxy through which the ECS client sends its requests. It is used only when"
                            + " proxyHost and proxyPort are set.",
              label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "The host name of the proxy through which the ECS client sends its requests. The proxy is used"
                            + " only when proxyPort is set too.",
              label = "proxy")
    private String proxyHost;
    @UriParam(description = "The port of the proxy through which the ECS client sends its requests. The proxy is used only"
                            + " when proxyHost is set too.",
              label = "proxy")
    private Integer proxyPort;
    @UriParam(description = "The AWS region of the ECS client, as the lowercase region ID, for example eu-west-1 (the value"
                            + " of Region.EU_WEST_1.id()). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, such as the AWS_REGION environment variable.",
              enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global")
    private String region;
    @UriParam(description = "Whether the producer takes the request from the message body, as the AWS SDK request class of"
                            + " the operation, for example a ListClustersRequest for listClusters. A body of another type"
                            + " fails. If false, the producer builds the request from the headers.")
    private boolean pojoRequest;
    @UriParam(description = "Whether the ECS client accepts any TLS certificate without validating it, for example the"
                            + " self-signed certificate of a test endpoint set with uriEndpointOverride. It applies to every"
                            + " request of the client, not only to an overridden endpoint.",
              label = "security", security = "insecure:ssl")
    private boolean trustAllCertificates;
    @UriParam(description = "Whether the ECS client sends its requests to the URI of the uriEndpointOverride option instead"
                            + " of the AWS endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint that the ECS client uses instead of the AWS endpoint of the region, for"
                            + " example http://localhost:4566 for LocalStack. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(description = "Whether the ECS client loads its credentials with the default credentials provider chain of the"
                            + " AWS SDK: system properties, environment variables, the profile files, and the credentials of"
                            + " the container or the EC2 instance. It takes precedence over the other credentials options.",
              label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(description = "Whether the ECS client loads its credentials from a profile of the AWS config and credentials"
                            + " files, the one of profileCredentialsName. It is ignored when useDefaultCredentialsProvider is"
                            + " true.",
              label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(description = "Whether the ECS client uses the temporary credentials made of accessKey, secretKey and"
                            + " sessionToken, for example after assuming an IAM role. If sessionToken is not set, accessKey"
                            + " and secretKey are used without it. It is ignored when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.",
              label = "security")
    private boolean useSessionCredentials;
    @UriParam(description = "The name of the profile that useProfileCredentialsProvider loads. If not set, the AWS SDK uses"
                            + " its default profile: the one of the AWS_PROFILE environment variable, or else default.",
              label = "security")
    private String profileCredentialsName;

    public EcsClient getEcsClient() {
        return ecsClient;
    }

    public void setEcsClient(EcsClient ecsClient) {
        this.ecsClient = ecsClient;
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

    public ECS2Operations getOperation() {
        return operation;
    }

    public void setOperation(ECS2Operations operation) {
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    @Override
    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    @Override
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

    public ECS2Configuration copy() {
        try {
            return (ECS2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
