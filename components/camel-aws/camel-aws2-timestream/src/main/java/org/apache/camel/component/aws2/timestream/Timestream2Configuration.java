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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;

@UriParams
public class Timestream2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "The type of the client: write for the databases, tables, batch load tasks and records of the"
                           + " Write API of Amazon Timestream for LiveAnalytics, or query for the queries and scheduled"
                           + " queries of its Query API. describeEndpoints is an operation of both.")
    @Metadata(required = true)
    private Timestream2ClientType clientType;
    @UriPath(description = "A logical name of the endpoint, which you choose freely. The producer does not use it: the"
                           + " database, table or query to act on is given in headers.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "The operation that the producer performs, which must be one of the clientType of the endpoint:"
                            + " the query and scheduled query operations for query, the others for write, and"
                            + " describeEndpoints for both. The CamelAwsTimestreamOperation header replaces this option.")
    @Metadata(required = true)
    private Timestream2Operations operation;
    @UriParam(label = "security", security = "secret",
              description = "The access key ID of the AWS credentials, used together with the secretKey option. It is"
                            + " required, unless the client option of the clientType (awsTimestreamWriteClient or"
                            + " awsTimestreamQueryClient) is set or useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private String accessKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret access key of the AWS credentials, used together with the accessKey option. It is"
                            + " required, unless the client option of the clientType (awsTimestreamWriteClient or"
                            + " awsTimestreamQueryClient) is set or useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private String secretKey;
    @UriParam(label = "security", security = "secret",
              description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.")
    private String sessionToken;
    @UriParam(label = "security",
              description = "Whether to use temporary credentials made of the accessKey, secretKey and sessionToken options,"
                            + " for example those of an assumed IAM role. It is not used when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private boolean useSessionCredentials;
    @UriParam(description = "The AWS region of Timestream, as a region ID such as eu-west-1, which is the value of"
                            + " Region.EU_WEST_1.id(). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, for example the AWS_REGION environment variable.",
              enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global")
    private String region;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol that the HTTP client of Timestream uses to connect to the proxy. It is used only"
                            + " when the proxyHost and proxyPort options are set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy",
              description = "The host name of the proxy that the HTTP client of Timestream connects through. The proxy is"
                            + " used only when the proxyPort option is set too.")
    private String proxyHost;
    @UriParam(label = "proxy",
              description = "The port of the proxy that the HTTP client of Timestream connects through. The proxy is used"
                            + " only when the proxyHost option is set too.")
    private Integer proxyPort;
    @UriParam(label = "advanced",
              description = "To use a custom software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient for an"
                            + " endpoint of the write clientType; a query endpoint does not use it. When this option is set or"
                            + " autowired, the credentials, region, proxy, endpoint override and trustAllCertificates options"
                            + " are not used. The Camel endpoint does not close a client that is set or autowired.")
    @Metadata(autowired = true)
    private TimestreamWriteClient awsTimestreamWriteClient;
    @UriParam(label = "advanced",
              description = "To use a custom software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient for an"
                            + " endpoint of the query clientType; a write endpoint does not use it. When this option is set or"
                            + " autowired, the credentials, region, proxy, endpoint override and trustAllCertificates options"
                            + " are not used. The Camel endpoint does not close a client that is set or autowired.")
    @Metadata(autowired = true)
    private TimestreamQueryClient awsTimestreamQueryClient;
    @UriParam(defaultValue = "false",
              description = "Whether the body is the request object of the AWS SDK for the operation, such as a"
                            + " WriteRecordsRequest or a QueryRequest, which the producer sends as it is. The headers that give"
                            + " the input of the operation are then not used, and a body of another type fails the exchange.")
    private boolean pojoRequest;
    @UriParam(label = "producer", defaultValue = "false",
              description = "For the query operation: read all the pages of the result, and give all the rows in one"
                            + " QueryResponse. Without it, the body is one page, and the CamelAwsTimestreamQueryNextToken"
                            + " header gives the token of the next page. Not used with pojoRequest.")
    private boolean queryAllPages;
    @UriParam(security = "insecure:ssl", defaultValue = "false",
              description = "Whether the HTTP client of Timestream trusts every TLS certificate without checking it. It"
                            + " applies to any endpoint, not only to an overridden one, so use it only for testing, for"
                            + " example against a local emulator.")
    private boolean trustAllCertificates;
    @UriParam(defaultValue = "false",
              description = "Whether to send the requests to the URI of the uriEndpointOverride option instead of the"
                            + " endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint to use instead of the endpoint of the region, for example"
                            + " http://localhost:4566 for a local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
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
    @UriParam(description = "The name of the profile in the AWS shared credentials and config files, used when"
                            + " useProfileCredentialsProvider is true. If not set, the AWS SDK uses its default profile, which"
                            + " the AWS_PROFILE environment variable can change.")
    private String profileCredentialsName;

    public Timestream2ClientType getClientType() {
        return clientType;
    }

    public TimestreamWriteClient getAwsTimestreamWriteClient() {
        return awsTimestreamWriteClient;
    }

    public void setAwsTimestreamWriteClient(TimestreamWriteClient awsTimestreamWriteClient) {
        this.awsTimestreamWriteClient = awsTimestreamWriteClient;
    }

    public TimestreamQueryClient getAwsTimestreamQueryClient() {
        return awsTimestreamQueryClient;
    }

    public void setAwsTimestreamQueryClient(TimestreamQueryClient awsTimestreamQueryClient) {
        this.awsTimestreamQueryClient = awsTimestreamQueryClient;
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

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Timestream2Operations getOperation() {
        return operation;
    }

    public void setOperation(Timestream2Operations operation) {
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

    public boolean isQueryAllPages() {
        return queryAllPages;
    }

    public void setQueryAllPages(boolean queryAllPages) {
        this.queryAllPages = queryAllPages;
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

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    // *************************************************
    //
    // *************************************************

    public Timestream2Configuration copy() {
        try {
            return (Timestream2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
