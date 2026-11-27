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
package org.apache.camel.component.aws2.bedrock.runtime;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@UriParams
public class BedrockConfiguration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "A logical name of the endpoint, which you choose freely. The producer does not use it: the model"
                           + " and the operation are given by options and headers.")
    @Metadata(required = true)
    private String label;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient for the"
                            + " operations that do not stream: invokeTextModel, invokeImageModel, invokeEmbeddingsModel,"
                            + " converse and applyGuardrail. When this option is set or autowired, the credentials, region,"
                            + " proxy, endpoint override and trustAllCertificates options are not used for this client. The"
                            + " Camel endpoint does not close a client that is set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private BedrockRuntimeClient bedrockRuntimeClient;
    @UriParam(description = "To use a custom software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient for the"
                            + " streaming operations: invokeTextModelStreaming, its deprecated variants and converseStream."
                            + " When it is not set, the endpoint builds one from the credentials, region, proxy and endpoint"
                            + " override options, also when bedrockRuntimeClient is set. The Camel endpoint does not close a"
                            + " client that is set or autowired.")
    @Metadata(label = "advanced", autowired = true)
    private BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;
    @UriParam(description = "The access key ID of the AWS credentials, used together with the secretKey option. It is"
                            + " required, unless bedrockRuntimeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret access key of the AWS credentials, used together with the accessKey option. It is"
                            + " required, unless bedrockRuntimeClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.",
              label = "security", security = "secret")
    private String secretKey;
    @UriParam(description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.",
              label = "security", security = "secret")
    private String sessionToken;
    @UriParam(description = "The ID of the model to invoke, for example anthropic.claude-3-5-sonnet-20241022-v2:0, or of a"
                            + " cross-region inference profile, such as us.anthropic.claude-3-5-sonnet-20241022-v2:0, or its"
                            + " ARN. invokeTextModel and the streaming operations read the response in the format of the model"
                            + " family that the ID names. applyGuardrail does not use it, and with pojoRequest the model of the"
                            + " request is used instead.",
              enums = "amazon.titan-text-express-v1,amazon.titan-text-lite-v1,amazon.titan-image-generator-v1,amazon.titan-embed-text-v1,amazon.titan-embed-image-v1,amazon.titan-text-premier-v1:0,amazon.titan-embed-text-v2:0,amazon.titan-image-generator-v2:0,amazon.nova-canvas-v1:0,amazon.nova-lite-v1:0,amazon.nova-micro-v1:0,amazon.nova-premier-v1:0,amazon.nova-pro-v1:0,amazon.nova-reel-v1:0,amazon.nova-reel-v1:1,amazon.nova-sonic-v1:0,amazon.rerank-v1:0,ai21.jamba-1-5-large-v1:0,ai21.jamba-1-5-mini-v1:0,anthropic.claude-3-sonnet-20240229-v1:0,anthropic.claude-3-5-sonnet-20240620-v1:0,anthropic.claude-3-5-sonnet-20241022-v2:0,anthropic.claude-3-haiku-20240307-v1:0,anthropic.claude-3-5-haiku-20241022-v1:0,anthropic.claude-3-opus-20240229-v1:0,anthropic.claude-3-7-sonnet-20250219-v1:0,anthropic.claude-opus-4-20250514-v1:0,anthropic.claude-sonnet-4-20250514-v1:0,cohere.command-r-plus-v1:0,cohere.command-r-v1:0,cohere.embed-english-v3,cohere.embed-multilingual-v3,cohere.rerank-v3-5:0,meta.llama3-8b-instruct-v1:0,meta.llama3-70b-instruct-v1:0,meta.llama3-1-8b-instruct-v1:0,meta.llama3-1-70b-instruct-v1:0,meta.llama3-1-405b-instruct-v1:0,meta.llama3-2-1b-instruct-v1:0,meta.llama3-2-3b-instruct-v1:0,meta.llama3-2-11b-instruct-v1:0,meta.llama3-2-90b-instruct-v1:0,meta.llama3-3-70b-instruct-v1:0,meta.llama4-maverick-17b-instruct-v1:0,meta.llama4-scout-17b-instruct-v1:0,mistral.mistral-7b-instruct-v0:2,mistral.mixtral-8x7b-instruct-v0:1,mistral.mistral-large-2402-v1:0,mistral.mistral-large-2407-v1:0,mistral.mistral-small-2402-v1:0,mistral.pixtral-large-2502-v1:0,stability.sd3-5-large-v1:0,stability.stable-image-control-sketch-v1:0,stability.stable-image-control-structure-v1:0,stability.stable-image-core-v1:1")
    @Metadata(required = true)
    private String modelId;
    @UriParam(description = "The operation that the producer performs. The CamelAwsBedrockOperation header replaces this"
                            + " option. The invokeImageModelStreaming and invokeEmbeddingsModelStreaming operations are"
                            + " deprecated, as the image generation and embedding models do not stream: use invokeImageModel,"
                            + " invokeEmbeddingsModel or invokeTextModelStreaming instead.")
    @Metadata(required = true)
    private BedrockOperations operation;
    @UriParam(description = "The protocol that the HTTP clients of Bedrock Runtime use to connect to the proxy. It is used"
                            + " only when the proxyHost and proxyPort options are set.",
              label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "The host name of the proxy that the HTTP clients of Bedrock Runtime connect through. The proxy"
                            + " is used only when the proxyPort option is set too.",
              label = "proxy")
    private String proxyHost;
    @UriParam(description = "The port of the proxy that the HTTP clients of Bedrock Runtime connect through. The proxy is"
                            + " used only when the proxyHost option is set too.",
              label = "proxy")
    private Integer proxyPort;
    @UriParam(description = "The AWS region of Amazon Bedrock, as a region ID such as us-east-1, which is the value of"
                            + " Region.US_EAST_1.id(). If not set, the AWS SDK takes the region from its default region"
                            + " provider chain, for example the AWS_REGION environment variable.",
              enums = "us-east-1,us-east-2,us-west-2,us-gov-west-1,ap-northeast-1,ap-northeast-2,ap-south-1,ap-southeast-1,ap-southeast-2,ca-central-1,eu-central-1,eu-central-2,eu-west-1,eu-west-2,eu-west-3,sa-east-1")
    private String region;
    @UriParam(description = "Whether the body is the request object of the AWS SDK for the operation, which the producer"
                            + " sends as it is: an InvokeModelRequest for the invoke operations, an"
                            + " InvokeModelWithResponseStreamRequest for the streaming ones, or a ConverseRequest,"
                            + " ConverseStreamRequest or ApplyGuardrailRequest. The options and headers that build the request"
                            + " are then not used, and a body of another type fails the exchange.")
    private boolean pojoRequest;
    @UriParam(description = "Whether the HTTP clients of Bedrock Runtime trust every TLS certificate without checking it. It"
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
    @UriParam(description = "How the streaming operations and converseStream give the response: complete gives the whole text"
                            + " as a String, and chunks gives a List of the text of each chunk. In both modes the producer"
                            + " waits until the model has finished. The CamelAwsBedrockStreamOutputMode header replaces this"
                            + " option.",
              defaultValue = "complete", enums = "complete,chunks")
    private String streamOutputMode = "complete";
    @UriParam(description = "Whether the streaming operations and converseStream set headers about the response: the chunk"
                            + " count, the completion reason and the token count for the invoke operations, and the chunk"
                            + " count, stop reason, usage and guardrail trace for converseStream, each when the model sends it.",
              defaultValue = "true")
    private boolean includeStreamingMetadata = true;
    @UriParam(description = "The ID or ARN of the guardrail to apply. converse and converseStream apply it when the"
                            + " CamelAwsBedrockGuardrailConfig header is not set, and applyGuardrail uses it when the"
                            + " CamelAwsBedrockGuardrailIdentifier header is not set. Not used with pojoRequest.")
    private String guardrailIdentifier;
    @UriParam(description = "The version of the guardrail, a number such as 1, or DRAFT for its working draft. converse and"
                            + " converseStream use it together with guardrailIdentifier, and applyGuardrail also with the"
                            + " guardrail of the CamelAwsBedrockGuardrailIdentifier header.",
              defaultValue = "DRAFT")
    private String guardrailVersion = "DRAFT";
    @UriParam(description = "Whether converse and converseStream ask for the trace of the guardrail of the"
                            + " guardrailIdentifier option, which the producer gives in the CamelAwsBedrockGuardrailTrace"
                            + " header. Not used when the CamelAwsBedrockGuardrailConfig header is set, and not used by"
                            + " applyGuardrail.",
              defaultValue = "false")
    private boolean guardrailTrace;
    @UriParam(description = "How converseStream applies the guardrail of the guardrailIdentifier option to the streamed"
                            + " response. With sync, Bedrock buffers the chunks and checks them with the guardrail before it"
                            + " sends them. With async, Bedrock sends the chunks at once and checks them in the background, so"
                            + " chunks that the guardrail would block can be sent before it blocks the rest, and masking of"
                            + " sensitive information is not supported. Not used when the CamelAwsBedrockGuardrailConfig"
                            + " header is set.",
              defaultValue = "sync", enums = "sync,async")
    private String guardrailStreamProcessingMode = "sync";

    public BedrockRuntimeClient getBedrockRuntimeClient() {
        return bedrockRuntimeClient;
    }

    public void setBedrockRuntimeClient(BedrockRuntimeClient bedrockRuntimeClient) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
    }

    public BedrockRuntimeAsyncClient getBedrockRuntimeAsyncClient() {
        return bedrockRuntimeAsyncClient;
    }

    public void setBedrockRuntimeAsyncClient(
            BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient) {
        this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
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

    public BedrockOperations getOperation() {
        return operation;
    }

    public void setOperation(BedrockOperations operation) {
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

    public String getStreamOutputMode() {
        return streamOutputMode;
    }

    public void setStreamOutputMode(String streamOutputMode) {
        this.streamOutputMode = streamOutputMode;
    }

    public boolean isIncludeStreamingMetadata() {
        return includeStreamingMetadata;
    }

    public void setIncludeStreamingMetadata(boolean includeStreamingMetadata) {
        this.includeStreamingMetadata = includeStreamingMetadata;
    }

    public String getGuardrailIdentifier() {
        return guardrailIdentifier;
    }

    public void setGuardrailIdentifier(String guardrailIdentifier) {
        this.guardrailIdentifier = guardrailIdentifier;
    }

    public String getGuardrailVersion() {
        return guardrailVersion;
    }

    public void setGuardrailVersion(String guardrailVersion) {
        this.guardrailVersion = guardrailVersion;
    }

    public boolean isGuardrailTrace() {
        return guardrailTrace;
    }

    public void setGuardrailTrace(boolean guardrailTrace) {
        this.guardrailTrace = guardrailTrace;
    }

    public String getGuardrailStreamProcessingMode() {
        return guardrailStreamProcessingMode;
    }

    public void setGuardrailStreamProcessingMode(String guardrailStreamProcessingMode) {
        this.guardrailStreamProcessingMode = guardrailStreamProcessingMode;
    }

    // *************************************************
    //
    // *************************************************

    public BedrockConfiguration copy() {
        try {
            return (BedrockConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
