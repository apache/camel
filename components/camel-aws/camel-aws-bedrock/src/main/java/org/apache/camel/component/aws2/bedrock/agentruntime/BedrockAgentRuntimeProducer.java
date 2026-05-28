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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowCompletionEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInputContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowTraceEvent;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

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
            case invokeFlow:
                invokeFlow(exchange);
                break;
            case retrieve:
                retrieve(getEndpoint().getBedrockAgentRuntimeClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private BedrockAgentRuntimeOperations determineOperation(Exchange exchange) {
        BedrockAgentRuntimeOperations operation
                = exchange.getIn().getHeader(BedrockAgentRuntimeConstants.OPERATION, BedrockAgentRuntimeOperations.class);
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
            bedrockAgentRuntimeProducerToString
                    = "BedrockAgentRuntimeProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
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
            if (payload instanceof RetrieveAndGenerateRequest retrieveAndGenerateRequest) {
                RetrieveAndGenerateResponse result;
                try {
                    result = bedrockAgentRuntimeClient.retrieveAndGenerate(retrieveAndGenerateRequest);
                } catch (AwsServiceException ase) {
                    LOG.trace("Retrieve and Generate command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                prepareResponse(result, message);
            }
        } else {
            String inputText = exchange.getMessage().getMandatoryBody(String.class);
            KnowledgeBaseVectorSearchConfiguration knowledgeBaseVectorSearchConfiguration
                    = KnowledgeBaseVectorSearchConfiguration.builder()
                            .build();
            KnowledgeBaseRetrievalConfiguration knowledgeBaseRetrievalConfiguration
                    = KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(knowledgeBaseVectorSearchConfiguration)
                            .build();
            KnowledgeBaseRetrieveAndGenerateConfiguration configuration = KnowledgeBaseRetrieveAndGenerateConfiguration
                    .builder().knowledgeBaseId(getConfiguration().getKnowledgeBaseId())
                    .modelArn(getConfiguration().getModelId())
                    .retrievalConfiguration(knowledgeBaseRetrievalConfiguration).build();

            RetrieveAndGenerateType type = RetrieveAndGenerateType.KNOWLEDGE_BASE;

            RetrieveAndGenerateConfiguration build
                    = RetrieveAndGenerateConfiguration.builder().knowledgeBaseConfiguration(configuration).type(type).build();

            RetrieveAndGenerateInput input = RetrieveAndGenerateInput.builder()
                    .text(inputText).build();

            RetrieveAndGenerateRequest.Builder request = RetrieveAndGenerateRequest.builder();

            request.retrieveAndGenerateConfiguration(build).input(input);

            if (ObjectHelper.isNotEmpty(exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID))) {
                request.sessionId(exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID, String.class));
            }

            RetrieveAndGenerateResponse retrieveAndGenerateResponse
                    = bedrockAgentRuntimeClient.retrieveAndGenerate(request.build());

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

    private void retrieve(BedrockAgentRuntimeClient bedrockAgentRuntimeClient, Exchange exchange)
            throws InvalidPayloadException {
        RetrieveRequest request;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof RetrieveRequest retrieveRequest) {
                request = retrieveRequest;
            } else {
                throw new IllegalArgumentException(
                        "retrieve operation requires a RetrieveRequest body when pojoRequest=true");
            }
        } else {
            request = buildRetrieveRequest(exchange);
        }

        RetrieveResponse response;
        try {
            response = bedrockAgentRuntimeClient.retrieve(request);
        } catch (AwsServiceException ase) {
            LOG.trace("Retrieve command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }

        Message message = getMessageForResponse(exchange);
        prepareRetrieveResponse(response, message);
    }

    private RetrieveRequest buildRetrieveRequest(Exchange exchange) throws InvalidPayloadException {
        String knowledgeBaseId = getConfiguration().getKnowledgeBaseId();
        if (ObjectHelper.isEmpty(knowledgeBaseId)) {
            throw new IllegalArgumentException("retrieve operation requires knowledgeBaseId in the endpoint configuration");
        }

        String queryText = exchange.getMessage().getMandatoryBody(String.class);

        KnowledgeBaseVectorSearchConfiguration.Builder vectorSearchBuilder = KnowledgeBaseVectorSearchConfiguration.builder();
        Integer numberOfResults
                = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.NUMBER_OF_RESULTS, Integer.class);
        if (numberOfResults != null) {
            vectorSearchBuilder.numberOfResults(numberOfResults);
        }
        String overrideSearchType
                = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.OVERRIDE_SEARCH_TYPE, String.class);
        if (ObjectHelper.isNotEmpty(overrideSearchType)) {
            vectorSearchBuilder.overrideSearchType(overrideSearchType);
        }

        KnowledgeBaseRetrievalConfiguration retrievalConfiguration = KnowledgeBaseRetrievalConfiguration.builder()
                .vectorSearchConfiguration(vectorSearchBuilder.build())
                .build();

        RetrieveRequest.Builder builder = RetrieveRequest.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .retrievalQuery(KnowledgeBaseQuery.builder().text(queryText).build())
                .retrievalConfiguration(retrievalConfiguration);

        String nextToken = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.NEXT_TOKEN, String.class);
        if (ObjectHelper.isNotEmpty(nextToken)) {
            builder.nextToken(nextToken);
        }

        return builder.build();
    }

    private void prepareRetrieveResponse(RetrieveResponse response, Message message) {
        List<KnowledgeBaseRetrievalResult> results
                = response.hasRetrievalResults() ? response.retrievalResults() : Collections.emptyList();
        message.setHeader(BedrockAgentRuntimeConstants.RETRIEVED_RESULTS, results);
        if (ObjectHelper.isNotEmpty(response.nextToken())) {
            message.setHeader(BedrockAgentRuntimeConstants.NEXT_TOKEN, response.nextToken());
        }
        if (ObjectHelper.isNotEmpty(response.guardrailActionAsString())) {
            message.setHeader(BedrockAgentRuntimeConstants.RETRIEVE_GUARDRAIL_ACTION, response.guardrailActionAsString());
        }
        message.setBody(results);
    }

    private void invokeFlow(Exchange exchange) throws InvalidPayloadException {
        BedrockAgentRuntimeAsyncClient asyncClient = getEndpoint().getBedrockAgentRuntimeAsyncClient();
        if (asyncClient == null) {
            throw new IllegalStateException(
                    "BedrockAgentRuntimeAsyncClient is not available. The invokeFlow operation requires an async client; "
                                            + "set operation=invokeFlow on the endpoint or autowire a BedrockAgentRuntimeAsyncClient.");
        }

        InvokeFlowRequest request;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getMessage().getMandatoryBody();
            if (payload instanceof InvokeFlowRequest flowRequest) {
                request = flowRequest;
            } else {
                throw new IllegalArgumentException(
                        "invokeFlow operation requires an InvokeFlowRequest body when pojoRequest=true");
            }
        } else {
            request = buildInvokeFlowRequest(exchange);
        }

        List<FlowOutputEvent> outputs = Collections.synchronizedList(new ArrayList<>());
        List<FlowTraceEvent> traces = Collections.synchronizedList(new ArrayList<>());
        String[] completionReason = new String[1];

        InvokeFlowResponseHandler handler = InvokeFlowResponseHandler.builder()
                .subscriber(InvokeFlowResponseHandler.Visitor.builder()
                        .onFlowOutputEvent(outputs::add)
                        .onFlowTraceEvent(traces::add)
                        .onFlowCompletionEvent((FlowCompletionEvent event) -> {
                            if (ObjectHelper.isNotEmpty(event.completionReasonAsString())) {
                                completionReason[0] = event.completionReasonAsString();
                            }
                        })
                        .build())
                .build();

        try {
            asyncClient.invokeFlow(request, handler).join();
        } catch (AwsServiceException ase) {
            LOG.trace("InvokeFlow command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        } catch (RuntimeException re) {
            // CompletableFuture.join() wraps checked exceptions in CompletionException; rethrow root cause when it is
            // an AWS service exception so callers see the same behavior as synchronous AWS calls.
            Throwable cause = re.getCause();
            if (cause instanceof AwsServiceException awsCause) {
                throw awsCause;
            }
            throw re;
        }

        Message message = getMessageForResponse(exchange);
        prepareFlowResponse(outputs, traces, completionReason[0], message);
    }

    private InvokeFlowRequest buildInvokeFlowRequest(Exchange exchange) throws InvalidPayloadException {
        String flowIdentifier = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.FLOW_IDENTIFIER, String.class);
        if (ObjectHelper.isEmpty(flowIdentifier)) {
            flowIdentifier = getConfiguration().getFlowIdentifier();
        }
        if (ObjectHelper.isEmpty(flowIdentifier)) {
            throw new IllegalArgumentException(
                    "invokeFlow operation requires flowIdentifier in configuration or header "
                                               + BedrockAgentRuntimeConstants.FLOW_IDENTIFIER);
        }

        String flowAliasIdentifier
                = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.FLOW_ALIAS_IDENTIFIER, String.class);
        if (ObjectHelper.isEmpty(flowAliasIdentifier)) {
            flowAliasIdentifier = getConfiguration().getFlowAliasIdentifier();
        }
        if (ObjectHelper.isEmpty(flowAliasIdentifier)) {
            throw new IllegalArgumentException(
                    "invokeFlow operation requires flowAliasIdentifier in configuration or header "
                                               + BedrockAgentRuntimeConstants.FLOW_ALIAS_IDENTIFIER);
        }

        Boolean enableTrace
                = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.FLOW_ENABLE_TRACE, Boolean.class);
        if (enableTrace == null) {
            enableTrace = getConfiguration().isEnableTrace();
        }

        InvokeFlowRequest.Builder builder = InvokeFlowRequest.builder()
                .flowIdentifier(flowIdentifier)
                .flowAliasIdentifier(flowAliasIdentifier)
                .enableTrace(enableTrace)
                .inputs(buildFlowInputs(exchange));

        String executionId = exchange.getMessage().getHeader(BedrockAgentRuntimeConstants.FLOW_EXECUTION_ID, String.class);
        if (ObjectHelper.isNotEmpty(executionId)) {
            builder.executionId(executionId);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<FlowInput> buildFlowInputs(Exchange exchange) throws InvalidPayloadException {
        Object body = exchange.getMessage().getMandatoryBody();

        if (body instanceof List<?> listBody) {
            if (!listBody.isEmpty() && listBody.get(0) instanceof FlowInput) {
                return (List<FlowInput>) listBody;
            }
            throw new IllegalArgumentException(
                    "invokeFlow expects the body to be a List<FlowInput>, a single FlowInput or a String when pojoRequest=false");
        }

        if (body instanceof FlowInput singleInput) {
            return Collections.singletonList(singleInput);
        }

        if (body instanceof String text) {
            FlowInput input = FlowInput.builder()
                    .nodeName("FlowInputNode")
                    .nodeOutputName("document")
                    .content(FlowInputContent.builder().document(Document.fromString(text)).build())
                    .build();
            return Collections.singletonList(input);
        }

        throw new IllegalArgumentException(
                "invokeFlow expects the body to be a List<FlowInput>, a single FlowInput or a String when pojoRequest=false. "
                                           + "Got: " + body.getClass().getName());
    }

    private void prepareFlowResponse(
            List<FlowOutputEvent> outputs, List<FlowTraceEvent> traces, String completionReason, Message message) {
        message.setHeader(BedrockAgentRuntimeConstants.FLOW_OUTPUTS, new ArrayList<>(outputs));
        if (!traces.isEmpty()) {
            message.setHeader(BedrockAgentRuntimeConstants.FLOW_TRACES, new ArrayList<>(traces));
        }
        if (ObjectHelper.isNotEmpty(completionReason)) {
            message.setHeader(BedrockAgentRuntimeConstants.FLOW_COMPLETION_REASON, completionReason);
        }

        // Surface the document payload of the last FlowOutputEvent as the body. This matches the user-facing
        // contract: "what did the flow return at the end?". When no outputs were produced the body is left untouched
        // so callers can still inspect headers to understand why the flow stopped.
        if (!outputs.isEmpty()) {
            FlowOutputEvent last = outputs.get(outputs.size() - 1);
            if (last.content() != null && last.content().document() != null) {
                Document doc = last.content().document();
                if (doc.isString()) {
                    message.setBody(doc.asString());
                } else {
                    message.setBody(doc);
                }
            } else {
                message.setBody(last);
            }
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
