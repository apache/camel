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

    @UriPath(description = "A logical name of the endpoint, which you choose freely. The producer does not use it: the"
                           + " knowledge base, agent or flow and the operation are given by options and headers.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient"
                            + " for retrieve and retrieveAndGenerate. When this option is set or autowired, the credentials,"
                            + " region, proxy, endpoint override and trustAllCertificates options are not used for this client."
                            + " The Camel endpoint does not close a client that is set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    @UriParam(description = "To use a custom"
                            + " software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient for"
                            + " invokeFlow, invokeAgent and invokeInlineAgent, which receive their response as an event stream."
                            + " When it is not set, the endpoint builds one from the credentials, region, proxy and endpoint"
                            + " override options: at start when the operation option is one of these, or else on first use. The"
                            + " Camel endpoint does not close a client that is set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient;
    @UriParam(description = "The access key ID of the AWS credentials, used together with the secretKey option. It is"
                            + " required, unless bedrockAgentRuntimeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret access key of the AWS credentials, used together with the accessKey option. It is"
                            + " required, unless bedrockAgentRuntimeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String secretKey;
    @UriParam(description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.",
              label = "security", security = "secret")
    private String sessionToken;
    @UriParam(description = "The ID or ARN of the model that retrieveAndGenerate generates the answer with, for example"
                            + " anthropic.claude-3-5-sonnet-20241022-v2:0. The other operations do not use it, and neither does"
                            + " retrieveAndGenerate with pojoRequest.",
              enums = "amazon.titan-text-express-v1,amazon.titan-text-lite-v1,amazon.titan-image-generator-v1,amazon.titan-embed-text-v1,amazon.titan-embed-image-v1,amazon.titan-text-premier-v1:0,amazon.titan-embed-text-v2:0,amazon.titan-image-generator-v2:0,amazon.nova-canvas-v1:0,amazon.nova-lite-v1:0,amazon.nova-micro-v1:0,amazon.nova-premier-v1:0,amazon.nova-pro-v1:0,amazon.nova-reel-v1:0,amazon.nova-reel-v1:1,amazon.nova-sonic-v1:0,amazon.rerank-v1:0,ai21.jamba-1-5-large-v1:0,ai21.jamba-1-5-mini-v1:0,anthropic.claude-3-sonnet-20240229-v1:0,anthropic.claude-3-5-sonnet-20240620-v1:0,anthropic.claude-3-5-sonnet-20241022-v2:0,anthropic.claude-3-haiku-20240307-v1:0,anthropic.claude-3-5-haiku-20241022-v1:0,anthropic.claude-3-opus-20240229-v1:0,anthropic.claude-3-7-sonnet-20250219-v1:0,anthropic.claude-opus-4-20250514-v1:0,anthropic.claude-sonnet-4-20250514-v1:0,cohere.command-r-plus-v1:0,cohere.command-r-v1:0,cohere.embed-english-v3,cohere.embed-multilingual-v3,cohere.rerank-v3-5:0,meta.llama3-8b-instruct-v1:0,meta.llama3-70b-instruct-v1:0,meta.llama3-1-8b-instruct-v1:0,meta.llama3-1-70b-instruct-v1:0,meta.llama3-1-405b-instruct-v1:0,meta.llama3-2-1b-instruct-v1:0,meta.llama3-2-3b-instruct-v1:0,meta.llama3-2-11b-instruct-v1:0,meta.llama3-2-90b-instruct-v1:0,meta.llama3-3-70b-instruct-v1:0,meta.llama4-maverick-17b-instruct-v1:0,meta.llama4-scout-17b-instruct-v1:0,mistral.mistral-7b-instruct-v0:2,mistral.mixtral-8x7b-instruct-v0:1,mistral.mistral-large-2402-v1:0,mistral.mistral-large-2407-v1:0,mistral.mistral-small-2402-v1:0,mistral.pixtral-large-2502-v1:0,stability.sd3-5-large-v1:0,stability.stable-image-control-sketch-v1:0,stability.stable-image-control-structure-v1:0,stability.stable-image-core-v1:1")
    private String modelId;
    @UriParam(description = "The ID of the knowledge base that retrieve and retrieveAndGenerate query. The other operations"
                            + " do not use it, and neither do these with pojoRequest.")
    private String knowledgeBaseId;
    @UriParam(description = "The operation that the producer performs. The CamelAwsBedrockAgentRuntimeOperation header"
                            + " replaces this option.")
    @Metadata(required = true)
    private BedrockAgentRuntimeOperations operation;
    @UriParam(description = "The ID of the flow that invokeFlow invokes. The CamelAwsBedrockAgentRuntimeFlowIdentifier header"
                            + " replaces this option.",
              label = "flow")
    private String flowIdentifier;
    @UriParam(description = "The ID of the alias of the flow that invokeFlow invokes. The"
                            + " CamelAwsBedrockAgentRuntimeFlowAliasIdentifier header replaces this option.",
              label = "flow")
    private String flowAliasIdentifier;
    @UriParam(description = "Whether invokeFlow, invokeAgent and invokeInlineAgent ask for the trace events of the"
                            + " invocation, which the producer gives in the CamelAwsBedrockAgentRuntimeFlowTraces or"
                            + " CamelAwsBedrockAgentRuntimeAgentTraces header. The CamelAwsBedrockAgentRuntimeFlowEnableTrace"
                            + " header replaces it for invokeFlow, and the CamelAwsBedrockAgentRuntimeAgentEnableTrace header"
                            + " for the agent operations.",
              label = "flow,agent", defaultValue = "false")
    private boolean enableTrace;
    @UriParam(description = "The ID of the agent that invokeAgent invokes. The CamelAwsBedrockAgentRuntimeAgentId header"
                            + " replaces this option.",
              label = "agent")
    private String agentId;
    @UriParam(description = "The ID of the alias of the agent that invokeAgent invokes. The"
                            + " CamelAwsBedrockAgentRuntimeAgentAliasId header replaces this option.",
              label = "agent")
    private String agentAliasId;
    @UriParam(description = "The ID of the session of invokeAgent and invokeInlineAgent: reuse the same value to continue the"
                            + " conversation. The CamelAwsBedrockAgentRuntimeSessionId header replaces this option. If neither"
                            + " is set, the producer uses a random ID for each message, and gives it in that header.",
              label = "agent")
    private String sessionId;
    @UriParam(description = "How invokeAgent and invokeInlineAgent give the answer: complete gives the whole text as a"
                            + " String, and chunks gives a List of the text of each chunk. In both modes the producer waits"
                            + " until the agent has finished. The CamelAwsBedrockAgentRuntimeAgentStreamOutputMode header"
                            + " replaces this option.",
              label = "agent", enums = "complete,chunks", defaultValue = "complete")
    private String streamOutputMode;
    @UriParam(description = "The ID of the foundation model that the inline agent of invokeInlineAgent uses, for example"
                            + " anthropic.claude-3-5-sonnet-20241022-v2:0. The CamelAwsBedrockAgentRuntimeAgentFoundationModel"
                            + " header replaces this option.",
              label = "agent")
    private String foundationModel;
    @UriParam(description = "The instructions that tell the inline agent of invokeInlineAgent what to do and how to interact"
                            + " with users. The CamelAwsBedrockAgentRuntimeAgentInstruction header replaces this option.",
              label = "agent")
    private String instruction;
    @UriParam(description = "The protocol that the HTTP clients of Bedrock Agent Runtime use to connect to the proxy. It is"
                            + " used only when the proxyHost and proxyPort options are set.",
              label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "The host name of the proxy that the HTTP clients of Bedrock Agent Runtime connect through. The"
                            + " proxy is used only when the proxyPort option is set too.",
              label = "proxy")
    private String proxyHost;
    @UriParam(description = "The port of the proxy that the HTTP clients of Bedrock Agent Runtime connect through. The proxy"
                            + " is used only when the proxyHost option is set too.",
              label = "proxy")
    private Integer proxyPort;
    @UriParam(description = "The AWS region of Amazon Bedrock, as a region ID such as us-east-1, which is the value of"
                            + " Region.US_EAST_1.id(). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, for example the AWS_REGION environment variable.",
              enums = "us-east-1,us-east-2,us-west-2,us-gov-west-1,ap-northeast-1,ap-northeast-2,ap-south-1,ap-southeast-1,ap-southeast-2,ca-central-1,eu-central-1,eu-central-2,eu-west-1,eu-west-2,eu-west-3,sa-east-1")
    private String region;
    @UriParam(description = "Whether the body is the request object of the AWS SDK for the operation, which the producer"
                            + " sends as it is: a RetrieveAndGenerateRequest, RetrieveRequest, InvokeFlowRequest,"
                            + " InvokeAgentRequest or InvokeInlineAgentRequest. The options and headers that build the request"
                            + " are then not used, and a body of another type fails the exchange.")
    private boolean pojoRequest;
    @UriParam(description = "Whether the HTTP clients of Bedrock Agent Runtime trust every TLS certificate without checking"
                            + " it. It applies to any endpoint, not only to an overridden one, so use it only for testing, for"
                            + " example against a local emulator.",
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

    public BedrockAgentRuntimeClient getBedrockAgentRuntimeClient() {
        return bedrockAgentRuntimeClient;
    }

    public void setBedrockAgentRuntimeClient(BedrockAgentRuntimeClient bedrockRuntimeClient) {
        this.bedrockAgentRuntimeClient = bedrockRuntimeClient;
    }

    public BedrockAgentRuntimeAsyncClient getBedrockAgentRuntimeAsyncClient() {
        return bedrockAgentRuntimeAsyncClient;
    }

    public void setBedrockAgentRuntimeAsyncClient(BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeAsyncClient) {
        this.bedrockAgentRuntimeAsyncClient = bedrockAgentRuntimeAsyncClient;
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

    public BedrockAgentRuntimeOperations getOperation() {
        return operation;
    }

    public void setOperation(BedrockAgentRuntimeOperations operation) {
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

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getFlowIdentifier() {
        return flowIdentifier;
    }

    public void setFlowIdentifier(String flowIdentifier) {
        this.flowIdentifier = flowIdentifier;
    }

    public String getFlowAliasIdentifier() {
        return flowAliasIdentifier;
    }

    public void setFlowAliasIdentifier(String flowAliasIdentifier) {
        this.flowAliasIdentifier = flowAliasIdentifier;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentAliasId() {
        return agentAliasId;
    }

    public void setAgentAliasId(String agentAliasId) {
        this.agentAliasId = agentAliasId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStreamOutputMode() {
        return streamOutputMode;
    }

    public void setStreamOutputMode(String streamOutputMode) {
        this.streamOutputMode = streamOutputMode;
    }

    public String getFoundationModel() {
        return foundationModel;
    }

    public void setFoundationModel(String foundationModel) {
        this.foundationModel = foundationModel;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public boolean isEnableTrace() {
        return enableTrace;
    }

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
