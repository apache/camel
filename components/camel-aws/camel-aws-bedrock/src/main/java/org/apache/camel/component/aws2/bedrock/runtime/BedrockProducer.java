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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

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
        switch (getConfiguration().getModelId()) {
            case "amazon.titan-text-express-v1", "amazon.titan-text-lite-v1", "amazon.titan-text-premier-v1:0",
                    "amazon.titan-embed-text-v2:0" ->
                setTitanText(result, message);
            case "ai21.j2-ultra-v1", "ai21.j2-mid-v1" -> {
                try {
                    setAi21Text(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            case "anthropic.claude-instant-v1", "anthropic.claude-v2", "anthropic.claude-v2:1" -> {
                try {
                    setAnthropicText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            case "anthropic.claude-3-sonnet-20240229-v1:0", "anthropic.claude-3-haiku-20240307-v1:0" -> {
                try {
                    setAnthropicV3Text(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            case "mistral.mistral-7b-instruct-v0:2", "mistral.mixtral-8x7b-instruct-v0:1",
                    "mistral.mistral-large-2402-v1:0" -> {
                try {
                    setMistralText(result, message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + getConfiguration().getModelId());
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

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
