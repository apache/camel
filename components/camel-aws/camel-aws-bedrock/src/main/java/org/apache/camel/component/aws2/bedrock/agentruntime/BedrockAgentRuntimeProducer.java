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
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

/**
 * A Producer which sends messages to the Amazon Bedrock Agent Runtime Service
 * <a href="http://aws.amazon.com/bedrock/">AWS Bedrock</a>
 */
public class BedrockAgentRuntimeProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockAgentRuntimeProducer.class);
    private transient String bedrockAgentRuntimeProducerToString;

    public BedrockAgentRuntimeProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case retrieveAndGenerate:
                retrieveAndGenerate(getEndpoint().getBedrockAgentRuntimeClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private BedrockAgentRuntimeOperations determineOperation(Exchange exchange) {
        BedrockAgentRuntimeOperations operation =
                exchange.getIn().getHeader(BedrockAgentRuntimeConstants.OPERATION, BedrockAgentRuntimeOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected BedrockAgentRuntimeConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (bedrockAgentRuntimeProducerToString == null) {
            bedrockAgentRuntimeProducerToString = "BedrockAgentRuntimeProducer["
                    + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return bedrockAgentRuntimeProducerToString;
    }

    @Override
    public BedrockAgentRuntimeEndpoint getEndpoint() {
        return (BedrockAgentRuntimeEndpoint) super.getEndpoint();
    }

    private void retrieveAndGenerate(BedrockAgentRuntimeClient bedrockAgentRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof RetrieveAndGenerateRequest) {
                RetrieveAndGenerateResponse result;
                try {
                    result = bedrockAgentRuntimeClient.retrieveAndGenerate((RetrieveAndGenerateRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace(
                            "Retrieve and Generate command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareResponse(result, message);
            }
        } else {
            String inputText = exchange.getMessage().getMandatoryBody(String.class);
            KnowledgeBaseVectorSearchConfiguration knowledgeBaseVectorSearchConfiguration =
                    KnowledgeBaseVectorSearchConfiguration.builder().build();
            KnowledgeBaseRetrievalConfiguration knowledgeBaseRetrievalConfiguration =
                    KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(knowledgeBaseVectorSearchConfiguration)
                            .build();
            KnowledgeBaseRetrieveAndGenerateConfiguration configuration =
                    KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                            .knowledgeBaseId(getConfiguration().getKnowledgeBaseId())
                            .modelArn(getConfiguration().getModelId())
                            .retrievalConfiguration(knowledgeBaseRetrievalConfiguration)
                            .build();

            RetrieveAndGenerateType type = RetrieveAndGenerateType.KNOWLEDGE_BASE;

            RetrieveAndGenerateConfiguration build = RetrieveAndGenerateConfiguration.builder()
                    .knowledgeBaseConfiguration(configuration)
                    .type(type)
                    .build();

            RetrieveAndGenerateInput input =
                    RetrieveAndGenerateInput.builder().text(inputText).build();

            RetrieveAndGenerateRequest.Builder request = RetrieveAndGenerateRequest.builder();

            request.retrieveAndGenerateConfiguration(build).input(input);

            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID))) {
                request.sessionId(
                        exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID, String.class));
            }

            RetrieveAndGenerateResponse retrieveAndGenerateResponse =
                    bedrockAgentRuntimeClient.retrieveAndGenerate(request.build());

            Message message = getMessageForResponse(exchange);
            prepareResponse(retrieveAndGenerateResponse, message);
        }
    }

    private void prepareResponse(RetrieveAndGenerateResponse result, Message message) {
        if (result.hasCitations()) {
            message.setHeader(BedrockAgentRuntimeConstants.CITATIONS, result.citations());
        }
        if (ObjectHelper.isNotEmpty(result.sessionId())) {
            message.setHeader(BedrockAgentRuntimeConstants.SESSION_ID, result.sessionId());
        }
        message.setBody(result.output().text());
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
