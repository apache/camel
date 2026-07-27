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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

@UriParams
public class BedrockAgentRuntimeConfiguration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    @UriParam(label = "security", security = "secret")
    private String accessKey;
    @UriParam(label = "security", security = "secret")
    private String secretKey;
    @UriParam(label = "security", security = "secret")
    private String sessionToken;
    @UriParam(enums = "amazon.titan-text-express-v1,amazon.titan-text-lite-v1,amazon.titan-image-generator-v1,amazon.titan-embed-text-v1,amazon.titan-embed-image-v1,amazon.titan-text-premier-v1:0,amazon.titan-embed-text-v2:0,amazon.titan-image-generator-v2:0,amazon.nova-canvas-v1:0,amazon.nova-lite-v1:0,amazon.nova-micro-v1:0,amazon.nova-premier-v1:0,amazon.nova-pro-v1:0,amazon.nova-reel-v1:0,amazon.nova-reel-v1:1,amazon.nova-sonic-v1:0,amazon.rerank-v1:0,ai21.jamba-1-5-large-v1:0,ai21.jamba-1-5-mini-v1:0,anthropic.claude-3-sonnet-20240229-v1:0,anthropic.claude-3-5-sonnet-20240620-v1:0,anthropic.claude-3-5-sonnet-20241022-v2:0,anthropic.claude-3-haiku-20240307-v1:0,anthropic.claude-3-5-haiku-20241022-v1:0,anthropic.claude-3-opus-20240229-v1:0,anthropic.claude-3-7-sonnet-20250219-v1:0,anthropic.claude-opus-4-20250514-v1:0,anthropic.claude-sonnet-4-20250514-v1:0,cohere.command-r-plus-v1:0,cohere.command-r-v1:0,cohere.embed-english-v3,cohere.embed-multilingual-v3,cohere.rerank-v3-5:0,meta.llama3-8b-instruct-v1:0,meta.llama3-70b-instruct-v1:0,meta.llama3-1-8b-instruct-v1:0,meta.llama3-1-70b-instruct-v1:0,meta.llama3-1-405b-instruct-v1:0,meta.llama3-2-1b-instruct-v1:0,meta.llama3-2-3b-instruct-v1:0,meta.llama3-2-11b-instruct-v1:0,meta.llama3-2-90b-instruct-v1:0,meta.llama3-3-70b-instruct-v1:0,meta.llama4-maverick-17b-instruct-v1:0,meta.llama4-scout-17b-instruct-v1:0,mistral.mistral-7b-instruct-v0:2,mistral.mixtral-8x7b-instruct-v0:1,mistral.mistral-large-2402-v1:0,mistral.mistral-large-2407-v1:0,mistral.mistral-small-2402-v1:0,mistral.pixtral-large-2502-v1:0,stability.sd3-5-large-v1:0,stability.stable-image-control-sketch-v1:0,stability.stable-image-control-structure-v1:0,stability.stable-image-core-v1:1")
    @Metadata(required = true)
    private String modelId;
    @UriParam
    @Metadata(required = true)
    private String knowledgeBaseId;
    @UriParam
    @Metadata(required = true)
    private BedrockAgentRuntimeOperations operation;
    @UriParam(label = "flow")
    private String flowIdentifier;
    @UriParam(label = "flow")
    private String flowAliasIdentifier;
    @UriParam(label = "flow,agent", defaultValue = "false")
    private boolean enableTrace;
    @UriParam(label = "agent")
    private String agentId;
    @UriParam(label = "agent")
    private String agentAliasId;
    @UriParam(label = "agent")
    private String sessionId;
    @UriParam(label = "agent", enums = "complete,chunks", defaultValue = "complete")
    private String streamOutputMode;
    @UriParam(label = "agent")
    private String foundationModel;
    @UriParam(label = "agent")
    private String instruction;
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
    @UriParam(label = "security", security = "insecure:ssl")
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

    public BedrockAgentRuntimeClient getBedrockAgentRuntimeClient() {
        return bedrockAgentRuntimeClient;
    }

    /**
     * To use an existing configured AWS Bedrock Agent Runtime client
     */
    public void setBedrockAgentRuntimeClient(BedrockAgentRuntimeClient bedrockRuntimeClient) {
        this.bedrockAgentRuntimeClient = bedrockRuntimeClient;
    }

    public BedrockAgentRuntimeAsyncClient getBedrockAgentRuntimeAsyncClient() {
        return bedrockAgentRuntimeAsyncClient;
    }

    /**
     * To use an existing configured AWS Bedrock Agent Runtime async client (required for invokeFlow which streams
     * events back).
     */
    public void setBedrockAgentRuntimeAsyncClient(BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient) {
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
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

    public BedrockAgentRuntimeOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(BedrockAgentRuntimeOperations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Bedrock Agent Runtime client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Bedrock Agent Runtime client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Bedrock Agent Runtime client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which Bedrock Agent Runtime client needs to work. When using this parameter, the configuration will
     * expect the lowercase name of the region (for example, ap-east-1) You'll need to use the name
     * Region.EU_WEST_1.id()
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
     * Set whether the Bedrock Agent Runtime client should expect to load credentials through a default credentials
     * provider or to expect static credentials to be passed in.
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
     * Set whether the Bedrock Agent Runtime client should expect to load credentials through a profile credentials
     * provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Bedrock Agent Runtime client should expect to use Session Credentials. This is useful in a
     * situation in which the user needs to assume an IAM role for doing operations in Bedrock.
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

    public String getFlowIdentifier() {
        return flowIdentifier;
    }

    /**
     * The unique identifier of the Bedrock flow to invoke (used by the invokeFlow operation). Can be overridden per
     * exchange via the CamelAwsBedrockAgentRuntimeFlowIdentifier header.
     */
    public void setFlowIdentifier(String flowIdentifier) {
        this.flowIdentifier = flowIdentifier;
    }

    public String getFlowAliasIdentifier() {
        return flowAliasIdentifier;
    }

    /**
     * The unique identifier of the Bedrock flow alias to invoke (used by the invokeFlow operation). Can be overridden
     * per exchange via the CamelAwsBedrockAgentRuntimeFlowAliasIdentifier header.
     */
    public void setFlowAliasIdentifier(String flowAliasIdentifier) {
        this.flowAliasIdentifier = flowAliasIdentifier;
    }

    public String getAgentId() {
        return agentId;
    }

    /**
     * The unique identifier of the agent to invoke, used by the invokeAgent operation.
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentAliasId() {
        return agentAliasId;
    }

    /**
     * The unique identifier of the agent alias to invoke, used by the invokeAgent operation.
     */
    public void setAgentAliasId(String agentAliasId) {
        this.agentAliasId = agentAliasId;
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * The unique identifier of the agent session. Reuse the same value across invocations to continue the same
     * conversation. When not set, a random session id is generated for each invocation.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStreamOutputMode() {
        return streamOutputMode;
    }

    /**
     * The streaming output mode (complete or chunks) used by the agent operations. In complete mode the response chunks
     * are accumulated and the body is the full text. In chunks mode the body is the list of chunks.
     */
    public void setStreamOutputMode(String streamOutputMode) {
        this.streamOutputMode = streamOutputMode;
    }

    public String getFoundationModel() {
        return foundationModel;
    }

    /**
     * The foundation model used by the invokeInlineAgent operation.
     */
    public void setFoundationModel(String foundationModel) {
        this.foundationModel = foundationModel;
    }

    public String getInstruction() {
        return instruction;
    }

    /**
     * The instruction given to the agent defined by the invokeInlineAgent operation.
     */
    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public boolean isEnableTrace() {
        return enableTrace;
    }

    /**
     * Enables tracing for the invokeFlow and agent operations. When enabled, the producer collects the trace events and
     * publishes them in the CamelAwsBedrockAgentRuntimeFlowTraces or CamelAwsBedrockAgentRuntimeAgentTraces header.
     */
    public void setEnableTrace(boolean enableTrace) {
        this.enableTrace = enableTrace;
    }

    // *************************************************
    //
    // *************************************************

    public BedrockAgentRuntimeConfiguration copy() {
        try {
            return (BedrockAgentRuntimeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
