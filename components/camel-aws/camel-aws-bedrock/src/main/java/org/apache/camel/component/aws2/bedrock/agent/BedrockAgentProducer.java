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
import org.apache.camel.component.aws.common.AwsExceptionUtil;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.*;

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
            case listIngestionJobs:
                listIngestionJobs(getEndpoint().getBedrockAgentClient(), exchange);
                break;
            case getIngestionJob:
                getIngestionJob(getEndpoint().getBedrockAgentClient(), exchange);
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
        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified, either with the operation option or the " + BedrockAgentConstants.OPERATION
                                               + " header");
        }
        return operation;
    }

    /**
     * The id of the header, or else of the option: the header overrides the option for this message.
     */
    private static String getId(Exchange exchange, String header, String option, String name) {
        String id = exchange.getMessage().getHeader(header, String.class);
        if (ObjectHelper.isEmpty(id)) {
            id = option;
        }
        if (ObjectHelper.isEmpty(id)) {
            throw new IllegalArgumentException(name + " must be specified");
        }
        return id;
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
            if (payload instanceof StartIngestionJobRequest startIngestionJobRequest) {
                StartIngestionJobResponse result;
                try {
                    result = bedrockAgentClient.startIngestionJob(startIngestionJobRequest);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Ingestion Job command returned the error code {}", AwsExceptionUtil.errorCode(ase));
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareIngestionJobResponse(result, message);
            } else {
                throw new IllegalArgumentException(
                        "startIngestionJob operation requires a StartIngestionJobRequest body when pojoRequest=true");
            }
        } else {
            StartIngestionJobRequest.Builder builder = StartIngestionJobRequest.builder();
            builder.knowledgeBaseId(getId(exchange, BedrockAgentConstants.KNOWLEDGE_BASE_ID,
                    getConfiguration().getKnowledgeBaseId(), "KnowledgeBaseId"));
            builder.dataSourceId(
                    getId(exchange, BedrockAgentConstants.DATASOURCE_ID, getConfiguration().getDataSourceId(), "DataSourceId"));
            StartIngestionJobResponse output = bedrockAgentClient.startIngestionJob(builder.build());
            Message message = getMessageForResponse(exchange);
            prepareIngestionJobResponse(output, message);
        }
    }

    private void listIngestionJobs(BedrockAgentClient bedrockAgentClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof ListIngestionJobsRequest listIngestionJobsRequest) {
                ListIngestionJobsResponse result;
                try {
                    result = bedrockAgentClient.listIngestionJobs(listIngestionJobsRequest);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Ingestion Job command returned the error code {}", AwsExceptionUtil.errorCode(ase));
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareListIngestionJobsResponse(result, message);
            } else {
                throw new IllegalArgumentException(
                        "listIngestionJobs operation requires a ListIngestionJobsRequest body when pojoRequest=true");
            }
        } else {
            ListIngestionJobsRequest.Builder builder = ListIngestionJobsRequest.builder();
            builder.knowledgeBaseId(getId(exchange, BedrockAgentConstants.KNOWLEDGE_BASE_ID,
                    getConfiguration().getKnowledgeBaseId(), "KnowledgeBaseId"));
            builder.dataSourceId(
                    getId(exchange, BedrockAgentConstants.DATASOURCE_ID, getConfiguration().getDataSourceId(), "DataSourceId"));
            ListIngestionJobsResponse output = bedrockAgentClient.listIngestionJobs(builder.build());
            Message message = getMessageForResponse(exchange);
            prepareListIngestionJobsResponse(output, message);
        }
    }

    private void getIngestionJob(BedrockAgentClient bedrockAgentClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof GetIngestionJobRequest getIngestionJobRequest) {
                GetIngestionJobResponse result;
                try {
                    result = bedrockAgentClient.getIngestionJob(getIngestionJobRequest);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Ingestion Job command returned the error code {}", AwsExceptionUtil.errorCode(ase));
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareGetIngestionJobResponse(result, message);
            } else {
                throw new IllegalArgumentException(
                        "getIngestionJob operation requires a GetIngestionJobRequest body when pojoRequest=true");
            }
        } else {
            GetIngestionJobRequest.Builder builder = GetIngestionJobRequest.builder();
            builder.knowledgeBaseId(getId(exchange, BedrockAgentConstants.KNOWLEDGE_BASE_ID,
                    getConfiguration().getKnowledgeBaseId(), "KnowledgeBaseId"));
            builder.dataSourceId(
                    getId(exchange, BedrockAgentConstants.DATASOURCE_ID, getConfiguration().getDataSourceId(), "DataSourceId"));
            builder.ingestionJobId(getId(exchange, BedrockAgentConstants.INGESTION_JOB_ID,
                    getConfiguration().getIngestionJobId(), "IngestionJobId"));
            GetIngestionJobResponse output = bedrockAgentClient.getIngestionJob(builder.build());
            Message message = getMessageForResponse(exchange);
            prepareGetIngestionJobResponse(output, message);
        }
    }

    private void prepareIngestionJobResponse(StartIngestionJobResponse result, Message message) {
        message.setBody(result.ingestionJob().ingestionJobId());
    }

    private void prepareListIngestionJobsResponse(ListIngestionJobsResponse result, Message message) {
        if (result.hasIngestionJobSummaries()) {
            message.setBody(result.ingestionJobSummaries());
        }
    }

    private void prepareGetIngestionJobResponse(GetIngestionJobResponse result, Message message) {
        message.setBody(result.ingestionJob());
        message.setHeader(BedrockAgentConstants.INGESTION_JOB_STATUS, result.ingestionJob().status());
        if (result.ingestionJob().hasFailureReasons()) {
            message.setHeader(BedrockAgentConstants.INGESTION_JOB_FAILURE_REASONS, result.ingestionJob().failureReasons());
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
