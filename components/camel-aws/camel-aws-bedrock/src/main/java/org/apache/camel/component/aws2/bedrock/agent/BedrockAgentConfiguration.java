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
package org.apache.camel.component.aws2.bedrock.agent;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;

@UriParams
public class BedrockAgentConfiguration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A logical name of the endpoint, which you choose freely. The consumer and the producer do not"
                           + " use it.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.bedrockagent.BedrockAgentClient. When this"
                            + " option is set or autowired, the credentials, region, proxy, endpoint override and"
                            + " trustAllCertificates options are not used. The Camel endpoint does not close a client that is"
                            + " set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentClient bedrockAgentClient;
    @UriParam(description = "The access key ID of the AWS credentials, used together with the secretKey option. It is"
                            + " required, unless bedrockAgentClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret access key of the AWS credentials, used together with the accessKey option. It is"
                            + " required, unless bedrockAgentClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String secretKey;
    @UriParam(description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.",
              label = "security", security = "secret")
    private String sessionToken;
    @UriParam(description = "The ID of the knowledge base of the ingestion jobs. The consumer and all the operations need it;"
                            + " the CamelAwsBedrockAgentKnowledgeBaseId header replaces it for the producer. Not used with"
                            + " pojoRequest.")
    @Metadata
    private String knowledgeBaseId;
    @UriParam(description = "The ID of the data source of the knowledge base, which the ingestion jobs sync. The consumer and"
                            + " all the operations need it; the CamelAwsBedrockAgentDataSourceId header replaces it for the"
                            + " producer. Not used with pojoRequest.")
    @Metadata
    private String dataSourceId;
    @UriParam(description = "The ID of the ingestion job that the consumer polls and that getIngestionJob gets. The"
                            + " CamelAwsBedrockAgentIngestionJobId header replaces it for getIngestionJob. startIngestionJob"
                            + " and listIngestionJobs do not use it.")
    private String ingestionJobId;
    @UriParam(description = "The operation that the producer performs. The CamelAwsBedrockAgentOperation header replaces this"
                            + " option. The consumer does not use it: it gets the ingestion job of the ingestionJobId option in"
                            + " each poll.")
    @Metadata(required = true)
    private BedrockAgentOperations operation;
    @UriParam(description = "The protocol that the HTTP client of Bedrock Agent uses to connect to the proxy. It is used only"
                            + " when the proxyHost and proxyPort options are set.",
              label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "The host name of the proxy that the HTTP client of Bedrock Agent connects through. The proxy is"
                            + " used only when the proxyPort option is set too.",
              label = "proxy")
    private String proxyHost;
    @UriParam(description = "The port of the proxy that the HTTP client of Bedrock Agent connects through. The proxy is used"
                            + " only when the proxyHost option is set too.",
              label = "proxy")
    private Integer proxyPort;
    @UriParam(description = "The AWS region of Amazon Bedrock, as a region ID such as us-east-1, which is the value of"
                            + " Region.US_EAST_1.id(). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, for example the AWS_REGION environment variable.",
              enums = "us-east-1,us-east-2,us-west-2,us-gov-west-1,ap-northeast-1,ap-northeast-2,ap-south-1,ap-southeast-1,ap-southeast-2,ca-central-1,eu-central-1,eu-central-2,eu-west-1,eu-west-2,eu-west-3,sa-east-1")
    private String region;
    @UriParam(description = "Whether the body is the request object of the AWS SDK for the operation, a"
                            + " StartIngestionJobRequest, ListIngestionJobsRequest or GetIngestionJobRequest, which the"
                            + " producer sends as it is. The ID options and headers are then not used, and a body of another"
                            + " type fails the exchange. The consumer does not use it.")
    private boolean pojoRequest;
    @UriParam(description = "Whether the HTTP client of Bedrock Agent trusts every TLS certificate without checking it. It"
                            + " applies to any endpoint, not only to an overridden one, so use it only for testing, for example"
                            + " against a local emulator.",
              label = "security", security = "insecure:ssl")
    private boolean trustAllCertificates;
    @UriParam(description = "Whether to send the requests to the URI of the uriEndpointOverride option instead of the"
                            + " endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint to use instead of the endpoint of the region, for example"
                            + " http://localhost:4566 for a local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;
    @UriParam(description = "Whether to load the credentials with the default credentials provider chain of the AWS SDK,"
                            + " which looks in system properties, environment variables, the shared credentials and config"
                            + " files, and the container or instance role. It takes precedence over the other credentials"
                            + " options.",
              defaultValue = "false")
    private boolean useDefaultCredentialsProvider;
    @UriParam(description = "Whether to load the credentials from a profile of the AWS shared credentials and config files,"
                            + " the one of the profileCredentialsName option. It is not used when useDefaultCredentialsProvider"
                            + " is true, and it takes precedence over the session and static credentials.",
              defaultValue = "false")
    private boolean useProfileCredentialsProvider;
    @UriParam(description = "Whether to use temporary credentials made of the accessKey, secretKey and sessionToken options,"
                            + " for example those of an assumed IAM role. It is not used when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.",
              label = "security")
    private boolean useSessionCredentials;
    @UriParam(description = "The name of the profile in the AWS shared credentials and config files, used when"
                            + " useProfileCredentialsProvider is true. If not set, the AWS SDK uses its default profile, which"
                            + " the AWS_PROFILE environment variable can change.",
              defaultValue = "false")
    private String profileCredentialsName;

    public BedrockAgentClient getBedrockAgentClient() {
        return bedrockAgentClient;
    }

    public void setBedrockAgentClient(BedrockAgentClient bedrockAgentClient) {
        this.bedrockAgentClient = bedrockAgentClient;
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

    public BedrockAgentOperations getOperation() {
        return operation;
    }

    public void setOperation(BedrockAgentOperations operation) {
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

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getIngestionJobId() {
        return ingestionJobId;
    }

    public void setIngestionJobId(String ingestionJobId) {
        this.ingestionJobId = ingestionJobId;
    }

    // *************************************************
    //
    // *************************************************

    public BedrockAgentConfiguration copy() {
        try {
            return (BedrockAgentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
