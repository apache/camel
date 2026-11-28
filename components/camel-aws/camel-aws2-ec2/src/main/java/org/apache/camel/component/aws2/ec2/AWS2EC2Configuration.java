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
package org.apache.camel.component.aws2.ec2;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.ec2.Ec2Client;

@UriParams
public class AWS2EC2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A logical name for the endpoint, of your choice. The component does not use it.")
    @Metadata(required = true)
    private String label;
    @UriParam(label = "producer",
              description = "To use a custom software.amazon.awssdk.services.ec2.Ec2Client. When this option is set or"
                            + " autowired, the region, credentials, proxy, endpoint override and trustAllCertificates"
                            + " options are not used. The endpoint does not close a client that is set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private Ec2Client amazonEc2Client;
    @UriParam(label = "producer,security", security = "secret",
              description = "The access key ID of the AWS credentials, used together with the secretKey option. It is not"
                            + " used when useDefaultCredentialsProvider or useProfileCredentialsProvider is true.")
    private String accessKey;
    @UriParam(label = "producer,security", security = "secret",
              description = "The secret access key of the AWS credentials, used together with the accessKey option. It is"
                            + " not used when useDefaultCredentialsProvider or useProfileCredentialsProvider is true.")
    private String secretKey;
    @UriParam(label = "producer,security", security = "secret",
              description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is"
                            + " used only when useSessionCredentials is true, together with the accessKey and secretKey"
                            + " options.")
    private String sessionToken;
    @UriParam(label = "producer",
              description = "The operation that the producer performs. The CamelAwsEC2Operation header replaces this"
                            + " option.")
    @Metadata(required = true)
    private AWS2EC2Operations operation;
    @UriParam(label = "producer,proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol of the proxy that the EC2 client uses. It is used only when the proxyHost and"
                            + " proxyPort options are both set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "producer,proxy",
              description = "The host name of the proxy that the EC2 client uses. The client uses a proxy only when the"
                            + " proxyPort option is set too.")
    private String proxyHost;
    @UriParam(label = "producer,proxy",
              description = "The port of the proxy that the EC2 client uses. The client uses a proxy only when the"
                            + " proxyHost option is set too.")
    private Integer proxyPort;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The ID of the AWS region of the EC2 service, in lower case, for example eu-west-1 (the value"
                            + " of Region.EU_WEST_1.id()). If not set, the AWS SDK resolves the region itself, for example"
                            + " from the AWS_REGION environment variable.")
    private String region;
    @UriParam(description = "Whether the body is the request of the AWS SDK for the operation, such as a"
                            + " DescribeInstancesRequest for describeInstances. If true, the producer sends that request as"
                            + " it is, and a body of another type fails. If false, the producer builds the request from the"
                            + " headers.")
    private boolean pojoRequest;
    @UriParam(label = "security", security = "insecure:ssl",
              description = "Whether the EC2 client accepts any TLS certificate of the server without checking it. It"
                            + " applies whether or not the endpoint is overridden. Use it only for tests, for example with a"
                            + " local emulator.")
    private boolean trustAllCertificates;
    @UriParam(description = "Whether the EC2 client sends the requests to the URI of the uriEndpointOverride option"
                            + " instead of the EC2 endpoint of the region. It has no effect when uriEndpointOverride is not"
                            + " set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI that the EC2 client sends the requests to, for example http://localhost:4566 for a"
                            + " local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(label = "security",
              description = "Whether the EC2 client loads the credentials with the default credentials provider chain of"
                            + " the AWS SDK: system properties, environment variables, web identity token, the shared"
                            + " credentials and config files, and container or instance profile credentials. It takes"
                            + " precedence over the other credentials options.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether the EC2 client loads the credentials of a profile of the shared AWS credentials and"
                            + " config files. The profileCredentialsName option names the profile. It takes precedence over"
                            + " useSessionCredentials and the accessKey and secretKey options.")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether the EC2 client uses temporary credentials made of the accessKey, secretKey and"
                            + " sessionToken options, for example to assume an IAM role. If one of the three is not set, the"
                            + " session credentials are not used.")
    private boolean useSessionCredentials;
    @UriParam(label = "security",
              description = "The name of the profile to load when useProfileCredentialsProvider is true. If not set, the"
                            + " AWS SDK uses its default profile, which the AWS_PROFILE environment variable can set.")
    private String profileCredentialsName;

    public Ec2Client getAmazonEc2Client() {
        return amazonEc2Client;
    }

    public void setAmazonEc2Client(Ec2Client amazonEc2Client) {
        this.amazonEc2Client = amazonEc2Client;
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

    public AWS2EC2Operations getOperation() {
        return operation;
    }

    public void setOperation(AWS2EC2Operations operation) {
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

    public AWS2EC2Configuration copy() {
        try {
            return (AWS2EC2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
