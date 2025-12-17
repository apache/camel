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

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentClient bedrockAgentClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam(enums = "anthropic.claude-instant-v1,anthropic.claude-v2,anthropic.claude-v2:1")
    @Metadata(required = true)
    private String modelId;
    @UriParam
    @Metadata
    private String knowledgeBaseId;
    @UriParam
    @Metadata
    private String dataSourceId;
    @UriParam
    @Metadata(label = "consumer")
    private String ingestionJobId;
    @UriParam
    @Metadata(required = true)
    private BedrockAgentOperations operation;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(enums = "us-east-1,us-east-2,us-west-2,us-gov-west-1,ap-northeast-1,ap-northeast-2,ap-south-1,ap-southeast-1,ap-southeast-2,ca-central-1,eu-central-1,eu-central-2,eu-west-1,eu-west-2,eu-west-3,sa-east-1")
    private String region;
    @UriParam
    private boolean pojoRequest;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(defaultValue = "false")
    private boolean useDefaultCredentialsProvider;
    @UriParam(defaultValue = "false")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private boolean useSessionCredentials;
    @UriParam(defaultValue = "false")
    private String profileCredentialsName;

    public BedrockAgentClient getBedrockAgentClient() {
        return bedrockAgentClient;
    }

    /**
     * To use an existing configured AWS Bedrock Agent client
     */
    public void setBedrockAgentClient(BedrockAgentClient bedrockRuntimeClient) {
        this.bedrockAgentClient = bedrockRuntimeClient;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Amazon AWS Session Token used when the user needs to assume an IAM role
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public BedrockAgentOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(BedrockAgentOperations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Bedrock Agent client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Bedrock Agent client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Bedrock Agent client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which Bedrock Agent client needs to work. When using this parameter, the configuration will expect
     * the lowercase name of the region (for example, ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with the
     * uriEndpointOverride option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

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
     * Set whether the Bedrock Agent client should expect to load credentials through a default credentials provider or
     * to expect static credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    /**
     * Set whether the Bedrock Agent client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Bedrock Agent client should expect to use Session Credentials. This is useful in a situation in
     * which the user needs to assume an IAM role for doing operations in Bedrock.
     */
    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider, this parameter will set the profile name
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public String getModelId() {
        return modelId;
    }

    /**
     * Define the model Id we are going to use
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    /**
     * Define the Knowledge Base Id we are going to use
     */
    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    /**
     * Define the Data source Id we are going to use
     */
    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getIngestionJobId() {
        return ingestionJobId;
    }

    /**
     * Define the Ingestion Job Id we want to track
     */
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
