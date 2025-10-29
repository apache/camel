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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.aws2.bedrock.runtime.stream.BedrockStreamHandler;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * A Producer which sends messages to the Amazon Bedrock Service <a href="http://aws.amazon.com/bedrock/">AWS
 * Bedrock</a>
 */
public class BedrockProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockProducer.class);
    private transient String bedrockProducerToString;

    public BedrockProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case invokeTextModel:
                invokeTextModel(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case invokeImageModel:
                invokeImageModel(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case invokeEmbeddingsModel:
                invokeEmbeddingsModel(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case invokeTextModelStreaming:
                invokeTextModelStreaming(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case invokeImageModelStreaming:
                invokeImageModelStreaming(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case invokeEmbeddingsModelStreaming:
                invokeEmbeddingsModelStreaming(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case converse:
                converse(getEndpoint().getBedrockRuntimeClient(), exchange);
                break;
            case converseStream:
                converseStream(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private BedrockOperations determineOperation(Exchange exchange) {
        BedrockOperations operation = exchange.getMessage().getHeader(BedrockConstants.OPERATION, BedrockOperations.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected BedrockConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (bedrockProducerToString == null) {
            bedrockProducerToString = "BedrockProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return bedrockProducerToString;
    }

    @Override
    public BedrockEndpoint getEndpoint() {
        return (BedrockEndpoint) super.getEndpoint();
    }

    private void invokeTextModel(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof InvokeModelRequest) {
                InvokeModelResponse result;
                try {
                    result = bedrockRuntimeClient.invokeModel((InvokeModelRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Invoke Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                setResponseText(result, message);
            }
        } else {
            InvokeModelRequest.Builder builder = InvokeModelRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_CONTENT_TYPE))) {
                String contentType = exchange.getIn().getHeader(BedrockConstants.MODEL_CONTENT_TYPE, String.class);
                builder.contentType(contentType);
            } else {
                throw new IllegalArgumentException("Model Content Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE))) {
                String acceptContentType = exchange.getIn().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, String.class);
                builder.accept(acceptContentType);
            } else {
                throw new IllegalArgumentException("Model Accept Content Type must be specified");
            }
            InvokeModelRequest request = builder
                    .body(SdkBytes.fromUtf8String(String.valueOf(exchange.getMessage().getBody())))
                    .modelId(getConfiguration().getModelId())
                    .build();
            InvokeModelResponse result;
            try {
                result = bedrockRuntimeClient.invokeModel(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Invoke Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            setResponseText(result, message);
        }
    }

    private void invokeImageModel(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof InvokeModelRequest) {
                InvokeModelResponse result;
                try {
                    result = bedrockRuntimeClient.invokeModel((InvokeModelRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Invoke Image Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            InvokeModelRequest.Builder builder = InvokeModelRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_CONTENT_TYPE))) {
                String contentType = exchange.getIn().getHeader(BedrockConstants.MODEL_CONTENT_TYPE, String.class);
                builder.contentType(contentType);
            } else {
                throw new IllegalArgumentException("Model Content Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE))) {
                String acceptContentType = exchange.getIn().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, String.class);
                builder.accept(acceptContentType);
            } else {
                throw new IllegalArgumentException("Model Accept Content Type must be specified");
            }
            InvokeModelRequest request = builder
                    .body(SdkBytes.fromUtf8String(String.valueOf(exchange.getMessage().getBody())))
                    .modelId(getConfiguration().getModelId())
                    .build();
            InvokeModelResponse result;
            try {
                result = bedrockRuntimeClient.invokeModel(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Invoke Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            try {
                setBase64Image(result, message);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void invokeEmbeddingsModel(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof InvokeModelRequest) {
                InvokeModelResponse result;
                try {
                    result = bedrockRuntimeClient.invokeModel((InvokeModelRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Invoke Image Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            InvokeModelRequest.Builder builder = InvokeModelRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_CONTENT_TYPE))) {
                String contentType = exchange.getIn().getHeader(BedrockConstants.MODEL_CONTENT_TYPE, String.class);
                builder.contentType(contentType);
            } else {
                throw new IllegalArgumentException("Model Content Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE))) {
                String acceptContentType = exchange.getIn().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, String.class);
                builder.accept(acceptContentType);
            } else {
                throw new IllegalArgumentException("Model Accept Content Type must be specified");
            }
            InvokeModelRequest request = builder
                    .body(SdkBytes.fromUtf8String(String.valueOf(exchange.getMessage().getBody())))
                    .modelId(getConfiguration().getModelId())
                    .build();
            InvokeModelResponse result;
            try {
                result = bedrockRuntimeClient.invokeModel(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Invoke Model command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private static void setBase64Image(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString.get("images"));
    }

    protected void setResponseText(InvokeModelResponse result, Message message) {
        String modelId = getConfiguration().getModelId();
        switch (modelId) {
            // Amazon Titan Models
            case "amazon.titan-text-express-v1":
            case "amazon.titan-text-lite-v1":
            case "amazon.titan-text-premier-v1:0":
            case "amazon.titan-embed-text-v2:0":
                setTitanText(result, message);
                break;

            // AI21 Labs Models
            case "ai21.j2-ultra-v1":
            case "ai21.j2-mid-v1":
            case "ai21.jamba-1-5-large-v1:0":
            case "ai21.jamba-1-5-mini-v1:0":
                try {
                    setAi21Text(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Anthropic Claude Models (legacy format - deprecated)
            case "anthropic.claude-instant-v1":
            case "anthropic.claude-v2":
            case "anthropic.claude-v2:1":
                try {
                    setAnthropicText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Anthropic Claude Models (v3+ format)
            case "anthropic.claude-3-sonnet-20240229-v1:0":
            case "anthropic.claude-3-5-sonnet-20240620-v1:0":
            case "anthropic.claude-3-5-sonnet-20241022-v2:0":
            case "anthropic.claude-3-haiku-20240307-v1:0":
            case "anthropic.claude-3-5-haiku-20241022-v1:0":
            case "anthropic.claude-3-opus-20240229-v1:0":
            case "anthropic.claude-3-7-sonnet-20250219-v1:0":
            case "anthropic.claude-opus-4-20250514-v1:0":
            case "anthropic.claude-sonnet-4-20250514-v1:0":
                try {
                    setAnthropicV3Text(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Mistral AI Models
            case "mistral.mistral-7b-instruct-v0:2":
            case "mistral.mixtral-8x7b-instruct-v0:1":
            case "mistral.mistral-large-2402-v1:0":
            case "mistral.mistral-large-2407-v1:0":
            case "mistral.mistral-small-2402-v1:0":
            case "mistral.pixtral-large-2502-v1:0":
                try {
                    setMistralText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Amazon Nova Models (using v3 format)
            case "amazon.nova-lite-v1:0":
            case "amazon.nova-micro-v1:0":
            case "amazon.nova-premier-v1:0":
            case "amazon.nova-pro-v1:0":
                try {
                    setAnthropicV3Text(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Cohere Models
            case "cohere.command-r-plus-v1:0":
            case "cohere.command-r-v1:0":
                try {
                    setCohereText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            // Meta Llama Models (Llama 3+ supported)
            case "meta.llama3-8b-instruct-v1:0":
            case "meta.llama3-70b-instruct-v1:0":
            case "meta.llama3-1-8b-instruct-v1:0":
            case "meta.llama3-1-70b-instruct-v1:0":
            case "meta.llama3-1-405b-instruct-v1:0":
            case "meta.llama3-2-1b-instruct-v1:0":
            case "meta.llama3-2-3b-instruct-v1:0":
            case "meta.llama3-2-11b-instruct-v1:0":
            case "meta.llama3-2-90b-instruct-v1:0":
            case "meta.llama3-3-70b-instruct-v1:0":
            case "meta.llama4-maverick-17b-instruct-v1:0":
            case "meta.llama4-scout-17b-instruct-v1:0":
                try {
                    setLlamaText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                break;

            default:
                throw new IllegalStateException("Unexpected model: " + modelId);
        }
    }

    private void setTitanText(InvokeModelResponse result, Message message) {
        message.setBody(result.body().asUtf8String());
    }

    private void setAi21Text(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString.get("completions"));
    }

    private void setAnthropicText(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString.get("completion"));
    }

    private void setAnthropicV3Text(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString);
    }

    private void setMistralText(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString);
    }

    private void setCohereText(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString.get("text"));
    }

    private void setLlamaText(InvokeModelResponse result, Message message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonString = mapper.readTree(result.body().asUtf8String());
        message.setBody(jsonString.get("generation"));
    }

    private void invokeTextModelStreaming(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof InvokeModelWithResponseStreamRequest) {
                processStreamingRequest((InvokeModelWithResponseStreamRequest) payload, exchange);
            }
        } else {
            InvokeModelWithResponseStreamRequest.Builder builder = InvokeModelWithResponseStreamRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_CONTENT_TYPE))) {
                String contentType = exchange.getIn().getHeader(BedrockConstants.MODEL_CONTENT_TYPE, String.class);
                builder.contentType(contentType);
            } else {
                throw new IllegalArgumentException("Model Content Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE))) {
                String acceptContentType = exchange.getIn().getHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, String.class);
                builder.accept(acceptContentType);
            } else {
                throw new IllegalArgumentException("Model Accept Content Type must be specified");
            }
            InvokeModelWithResponseStreamRequest request = builder
                    .body(SdkBytes.fromUtf8String(String.valueOf(exchange.getMessage().getBody())))
                    .modelId(getConfiguration().getModelId())
                    .build();
            processStreamingRequest(request, exchange);
        }
    }

    private void invokeImageModelStreaming(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        // Image streaming works the same way as text streaming
        invokeTextModelStreaming(bedrockRuntimeClient, exchange);
    }

    private void invokeEmbeddingsModelStreaming(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        // Embeddings streaming works the same way as text streaming
        invokeTextModelStreaming(bedrockRuntimeClient, exchange);
    }

    private void processStreamingRequest(
            InvokeModelWithResponseStreamRequest request,
            Exchange exchange) {

        try {
            String streamOutputMode = getConfiguration().getStreamOutputMode();
            if (streamOutputMode == null) {
                streamOutputMode = "complete";
            }

            // Check if mode is overridden in headers
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.STREAM_OUTPUT_MODE))) {
                streamOutputMode = exchange.getIn().getHeader(BedrockConstants.STREAM_OUTPUT_MODE, String.class);
            }

            Message message = getMessageForResponse(exchange);
            BedrockStreamHandler.StreamMetadata metadata = new BedrockStreamHandler.StreamMetadata();

            if ("chunks".equals(streamOutputMode)) {
                // Chunks mode - emit each chunk as separate message
                List<String> allChunks = new ArrayList<>();
                getEndpoint().getBedrockRuntimeAsyncClient().invokeModelWithResponseStream(
                        request,
                        BedrockStreamHandler.createChunksHandler(
                                getConfiguration().getModelId(),
                                metadata,
                                allChunks,
                                null))
                        .join();

                message.setBody(allChunks);
                if (getConfiguration().isIncludeStreamingMetadata()) {
                    setStreamingMetadata(message, metadata);
                }
            } else {
                // Complete mode - accumulate all chunks and return complete response
                StringBuilder fullText = new StringBuilder();
                getEndpoint().getBedrockRuntimeAsyncClient().invokeModelWithResponseStream(
                        request,
                        BedrockStreamHandler.createCompleteHandler(
                                getConfiguration().getModelId(),
                                metadata,
                                fullText))
                        .join();

                message.setBody(fullText.toString());
                if (getConfiguration().isIncludeStreamingMetadata()) {
                    setStreamingMetadata(message, metadata);
                }
            }

        } catch (AwsServiceException ase) {
            LOG.trace("Invoke Model Streaming command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
    }

    private void setStreamingMetadata(Message message, BedrockStreamHandler.StreamMetadata metadata) {
        if (metadata.getCompletionReason() != null) {
            message.setHeader(BedrockConstants.STREAMING_COMPLETION_REASON, metadata.getCompletionReason());
        }
        if (metadata.getTokenCount() != null) {
            message.setHeader(BedrockConstants.STREAMING_TOKEN_COUNT, metadata.getTokenCount());
        }
        message.setHeader(BedrockConstants.STREAMING_CHUNK_COUNT, metadata.getChunkCount());
    }

    private void converse(BedrockRuntimeClient bedrockRuntimeClient, Exchange exchange) throws InvalidPayloadException {
        ConverseRequest request;

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof ConverseRequest) {
                request = (ConverseRequest) payload;
            } else {
                throw new IllegalArgumentException(
                        "Converse operation requires ConverseRequest in POJO mode");
            }
        } else {
            // Build request from headers and body
            ConverseRequest.Builder builder = ConverseRequest.builder();

            // Set model ID
            builder.modelId(getConfiguration().getModelId());

            // Get messages from header or body
            @SuppressWarnings("unchecked")
            List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_MESSAGES, List.class);
            if (messages != null) {
                builder.messages(messages);
            } else {
                throw new IllegalArgumentException(
                        "Converse operation requires messages in header " + BedrockConstants.CONVERSE_MESSAGES);
            }

            // Optional: System prompts
            @SuppressWarnings("unchecked")
            List<SystemContentBlock> system
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_SYSTEM, List.class);
            if (system != null) {
                builder.system(system);
            }

            // Optional: Inference configuration
            InferenceConfiguration inferenceConfig
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, InferenceConfiguration.class);
            if (inferenceConfig != null) {
                builder.inferenceConfig(inferenceConfig);
            }

            // Optional: Tool configuration
            ToolConfiguration toolConfig
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_TOOL_CONFIG, ToolConfiguration.class);
            if (toolConfig != null) {
                builder.toolConfig(toolConfig);
            }

            // Optional: Additional model request fields
            software.amazon.awssdk.core.document.Document additionalFields = exchange.getMessage()
                    .getHeader(BedrockConstants.CONVERSE_ADDITIONAL_MODEL_REQUEST_FIELDS,
                            software.amazon.awssdk.core.document.Document.class);
            if (additionalFields != null) {
                builder.additionalModelRequestFields(additionalFields);
            }

            request = builder.build();
        }

        try {
            ConverseResponse response = bedrockRuntimeClient.converse(request);

            org.apache.camel.Message message = getMessageForResponse(exchange);

            // Set the output message content as body
            if (response.output() != null && response.output().message() != null) {
                software.amazon.awssdk.services.bedrockruntime.model.Message outputMessage = response.output().message();
                message.setHeader(BedrockConstants.CONVERSE_OUTPUT_MESSAGE, outputMessage);

                // Extract text content from the message
                StringBuilder textContent = new StringBuilder();
                for (ContentBlock content : outputMessage.content()) {
                    if (content.text() != null) {
                        textContent.append(content.text());
                    }
                }
                message.setBody(textContent.toString());
            }

            // Set metadata headers
            if (response.stopReason() != null) {
                message.setHeader(BedrockConstants.CONVERSE_STOP_REASON, response.stopReason().toString());
            }
            if (response.usage() != null) {
                message.setHeader(BedrockConstants.CONVERSE_USAGE, response.usage());
            }

        } catch (AwsServiceException ase) {
            LOG.trace("Converse command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
    }

    private void converseStream(Exchange exchange) throws InvalidPayloadException {
        ConverseStreamRequest request;

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof ConverseStreamRequest) {
                request = (ConverseStreamRequest) payload;
            } else {
                throw new IllegalArgumentException(
                        "ConverseStream operation requires ConverseStreamRequest in POJO mode");
            }
        } else {
            // Build request from headers and body
            ConverseStreamRequest.Builder builder = ConverseStreamRequest.builder();

            // Set model ID
            builder.modelId(getConfiguration().getModelId());

            // Get messages from header or body
            @SuppressWarnings("unchecked")
            List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_MESSAGES, List.class);
            if (messages != null) {
                builder.messages(messages);
            } else {
                throw new IllegalArgumentException(
                        "ConverseStream operation requires messages in header " + BedrockConstants.CONVERSE_MESSAGES);
            }

            // Optional: System prompts
            @SuppressWarnings("unchecked")
            List<SystemContentBlock> system
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_SYSTEM, List.class);
            if (system != null) {
                builder.system(system);
            }

            // Optional: Inference configuration
            InferenceConfiguration inferenceConfig
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, InferenceConfiguration.class);
            if (inferenceConfig != null) {
                builder.inferenceConfig(inferenceConfig);
            }

            // Optional: Tool configuration
            ToolConfiguration toolConfig
                    = exchange.getMessage().getHeader(BedrockConstants.CONVERSE_TOOL_CONFIG, ToolConfiguration.class);
            if (toolConfig != null) {
                builder.toolConfig(toolConfig);
            }

            // Optional: Additional model request fields
            software.amazon.awssdk.core.document.Document additionalFields = exchange.getMessage()
                    .getHeader(BedrockConstants.CONVERSE_ADDITIONAL_MODEL_REQUEST_FIELDS,
                            software.amazon.awssdk.core.document.Document.class);
            if (additionalFields != null) {
                builder.additionalModelRequestFields(additionalFields);
            }

            request = builder.build();
        }

        processConverseStreamingRequest(request, exchange);
    }

    private void processConverseStreamingRequest(ConverseStreamRequest request, Exchange exchange) {
        try {
            String streamOutputMode = getConfiguration().getStreamOutputMode();
            if (streamOutputMode == null) {
                streamOutputMode = "complete";
            }

            // Check if mode is overridden in headers
            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockConstants.STREAM_OUTPUT_MODE))) {
                streamOutputMode = exchange.getIn().getHeader(BedrockConstants.STREAM_OUTPUT_MODE, String.class);
            }

            org.apache.camel.Message message = getMessageForResponse(exchange);
            org.apache.camel.component.aws2.bedrock.runtime.stream.ConverseStreamHandler.StreamMetadata metadata
                    = new org.apache.camel.component.aws2.bedrock.runtime.stream.ConverseStreamHandler.StreamMetadata();

            if ("chunks".equals(streamOutputMode)) {
                // Chunks mode - emit each chunk as separate message
                List<String> allChunks = new ArrayList<>();
                getEndpoint().getBedrockRuntimeAsyncClient().converseStream(
                        request,
                        org.apache.camel.component.aws2.bedrock.runtime.stream.ConverseStreamHandler.createChunksHandler(
                                metadata,
                                allChunks,
                                null))
                        .join();

                message.setBody(allChunks);
                if (getConfiguration().isIncludeStreamingMetadata()) {
                    setConverseStreamingMetadata(message, metadata);
                }
            } else {
                // Complete mode - accumulate all chunks and return complete response
                StringBuilder fullText = new StringBuilder();
                getEndpoint().getBedrockRuntimeAsyncClient().converseStream(
                        request,
                        org.apache.camel.component.aws2.bedrock.runtime.stream.ConverseStreamHandler.createCompleteHandler(
                                metadata,
                                fullText))
                        .join();

                message.setBody(fullText.toString());
                if (getConfiguration().isIncludeStreamingMetadata()) {
                    setConverseStreamingMetadata(message, metadata);
                }
            }

        } catch (AwsServiceException ase) {
            LOG.trace("Converse Stream command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
    }

    private void setConverseStreamingMetadata(
            org.apache.camel.Message message,
            org.apache.camel.component.aws2.bedrock.runtime.stream.ConverseStreamHandler.StreamMetadata metadata) {
        if (metadata.getStopReason() != null) {
            message.setHeader(BedrockConstants.CONVERSE_STOP_REASON, metadata.getStopReason());
        }
        if (metadata.getUsage() != null) {
            message.setHeader(BedrockConstants.CONVERSE_USAGE, metadata.getUsage());
        }
        message.setHeader(BedrockConstants.STREAMING_CHUNK_COUNT, metadata.getChunkCount());
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
