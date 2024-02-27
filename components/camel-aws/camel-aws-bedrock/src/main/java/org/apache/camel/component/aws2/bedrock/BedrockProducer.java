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
package org.apache.camel.component.aws2.bedrock;

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
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private BedrockOperations determineOperation(Exchange exchange) {
        BedrockOperations operation = exchange.getIn().getHeader(BedrockConstants.OPERATION, BedrockOperations.class);
        if (operation == null) {
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
            message.setBody(result.body().asUtf8String());
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
