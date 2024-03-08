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
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

/**
 * A Producer which sends messages to the Amazon Bedrock Agent Service <a href="http://aws.amazon.com/bedrock/">AWS
 * Bedrock</a>
 */
public class BedrockAgentProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockAgentProducer.class);
    private transient String bedrockAgentProducerToString;

    public BedrockAgentProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case startIngestionJob:
                startIngestionJob(getEndpoint().getBedrockAgentClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private BedrockAgentOperations determineOperation(Exchange exchange) {
        BedrockAgentOperations operation
                = exchange.getIn().getHeader(BedrockAgentConstants.OPERATION, BedrockAgentOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected BedrockAgentConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (bedrockAgentProducerToString == null) {
            bedrockAgentProducerToString
                    = "BedrockAgentProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return bedrockAgentProducerToString;
    }

    @Override
    public BedrockAgentEndpoint getEndpoint() {
        return (BedrockAgentEndpoint) super.getEndpoint();
    }

    private void startIngestionJob(BedrockAgentClient bedrockAgentClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof StartIngestionJobRequest) {
                StartIngestionJobResponse result;
                try {
                    result = bedrockAgentClient.startIngestionJob((StartIngestionJobRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Ingestion Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareResponse(result, message);
            }
        } else {
            String knowledgeBaseId;
            String dataSourceId;
            StartIngestionJobRequest.Builder builder = StartIngestionJobRequest.builder();
            if (ObjectHelper.isEmpty(getConfiguration().getKnowledgeBaseId())) {
                if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockAgentConstants.KNOWLEDGE_BASE_ID))) {
                    knowledgeBaseId = exchange.getIn().getHeader(BedrockAgentConstants.KNOWLEDGE_BASE_ID, String.class);
                } else {
                    throw new IllegalArgumentException("KnowledgeBaseId must be specified");
                }
            } else {
                knowledgeBaseId = getConfiguration().getKnowledgeBaseId();
            }
            if (ObjectHelper.isEmpty(getConfiguration().getDataSourceId())) {
                if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockAgentConstants.DATASOURCE_ID))) {
                    dataSourceId = exchange.getIn().getHeader(BedrockAgentConstants.DATASOURCE_ID, String.class);
                } else {
                    throw new IllegalArgumentException("DataSourceId must be specified");
                }
            } else {
                dataSourceId = getConfiguration().getDataSourceId();
            }
            builder.knowledgeBaseId(knowledgeBaseId);
            builder.dataSourceId(dataSourceId);
            StartIngestionJobResponse output = bedrockAgentClient.startIngestionJob(builder.build());
            Message message = getMessageForResponse(exchange);
            prepareResponse(output, message);
        }
    }

    private void prepareResponse(StartIngestionJobResponse result, Message message) {
        message.setBody(result.ingestionJob().ingestionJobId());
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
