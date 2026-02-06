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
package org.apache.camel.component.aws.securityhub;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFinding;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingFilters;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingIdentifier;
import software.amazon.awssdk.services.securityhub.model.BatchImportFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.BatchImportFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.BatchUpdateFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.BatchUpdateFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.DescribeHubRequest;
import software.amazon.awssdk.services.securityhub.model.DescribeHubResponse;
import software.amazon.awssdk.services.securityhub.model.GetFindingHistoryRequest;
import software.amazon.awssdk.services.securityhub.model.GetFindingHistoryResponse;
import software.amazon.awssdk.services.securityhub.model.GetFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.GetFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.ListEnabledProductsForImportRequest;
import software.amazon.awssdk.services.securityhub.model.ListEnabledProductsForImportResponse;
import software.amazon.awssdk.services.securityhub.model.NoteUpdate;
import software.amazon.awssdk.services.securityhub.model.RelatedFinding;
import software.amazon.awssdk.services.securityhub.model.SeverityUpdate;
import software.amazon.awssdk.services.securityhub.model.VerificationState;
import software.amazon.awssdk.services.securityhub.model.WorkflowUpdate;

/**
 * A Producer which sends messages to the AWS Security Hub Service SDK v2
 * <a href="https://aws.amazon.com/security-hub/">AWS Security Hub</a>
 */
public class SecurityHubProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityHubProducer.class);

    private transient String securityHubProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public SecurityHubProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case batchImportFindings:
                batchImportFindings(getEndpoint().getSecurityHubClient(), exchange);
                break;
            case getFindings:
                getFindings(getEndpoint().getSecurityHubClient(), exchange);
                break;
            case batchUpdateFindings:
                batchUpdateFindings(getEndpoint().getSecurityHubClient(), exchange);
                break;
            case getFindingHistory:
                getFindingHistory(getEndpoint().getSecurityHubClient(), exchange);
                break;
            case describeHub:
                describeHub(getEndpoint().getSecurityHubClient(), exchange);
                break;
            case listEnabledProductsForImport:
                listEnabledProductsForImport(getEndpoint().getSecurityHubClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private SecurityHubOperations determineOperation(Exchange exchange) {
        SecurityHubOperations operation
                = exchange.getIn().getHeader(SecurityHubConstants.OPERATION, SecurityHubOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected SecurityHubConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (securityHubProducerToString == null) {
            securityHubProducerToString = "SecurityHubProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return securityHubProducerToString;
    }

    @SuppressWarnings("unchecked")
    private void batchImportFindings(SecurityHubClient securityHubClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                BatchImportFindingsRequest.class,
                securityHubClient::batchImportFindings,
                () -> {
                    BatchImportFindingsRequest.Builder builder = BatchImportFindingsRequest.builder();
                    List<AwsSecurityFinding> findings
                            = exchange.getIn().getHeader(SecurityHubConstants.FINDINGS, List.class);
                    if (findings == null || findings.isEmpty()) {
                        // Try to get findings from body
                        Object body = exchange.getIn().getBody();
                        if (body instanceof List list) {
                            findings = (List<AwsSecurityFinding>) list;
                        } else if (body instanceof AwsSecurityFinding asf) {
                            findings = List.of(asf);
                        }
                    }
                    if (findings == null || findings.isEmpty()) {
                        throw new IllegalArgumentException("At least one finding must be specified");
                    }
                    builder.findings(findings);
                    return securityHubClient.batchImportFindings(builder.build());
                },
                "Batch Import Findings",
                (BatchImportFindingsResponse response, Message message) -> {
                    message.setHeader(SecurityHubConstants.FAILED_COUNT, response.failedCount());
                    message.setHeader(SecurityHubConstants.SUCCESS_COUNT, response.successCount());
                });
    }

    private void getFindings(SecurityHubClient securityHubClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetFindingsRequest.class,
                securityHubClient::getFindings,
                () -> {
                    GetFindingsRequest.Builder builder = GetFindingsRequest.builder();
                    AwsSecurityFindingFilters filters
                            = exchange.getIn().getHeader(SecurityHubConstants.FILTERS, AwsSecurityFindingFilters.class);
                    if (filters != null) {
                        builder.filters(filters);
                    }
                    String nextToken = exchange.getIn().getHeader(SecurityHubConstants.NEXT_TOKEN, String.class);
                    if (nextToken != null) {
                        builder.nextToken(nextToken);
                    }
                    Integer maxResults = exchange.getIn().getHeader(SecurityHubConstants.MAX_RESULTS, Integer.class);
                    if (maxResults != null) {
                        builder.maxResults(maxResults);
                    }
                    return securityHubClient.getFindings(builder.build());
                },
                "Get Findings",
                (GetFindingsResponse response, Message message) -> {
                    message.setHeader(SecurityHubConstants.NEXT_TOKEN, response.nextToken());
                });
    }

    @SuppressWarnings("unchecked")
    private void batchUpdateFindings(SecurityHubClient securityHubClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                BatchUpdateFindingsRequest.class,
                securityHubClient::batchUpdateFindings,
                () -> {
                    BatchUpdateFindingsRequest.Builder builder = BatchUpdateFindingsRequest.builder();

                    List<AwsSecurityFindingIdentifier> findingIdentifiers
                            = exchange.getIn().getHeader(SecurityHubConstants.FINDING_IDENTIFIERS, List.class);
                    if (findingIdentifiers == null || findingIdentifiers.isEmpty()) {
                        throw new IllegalArgumentException("Finding identifiers must be specified");
                    }
                    builder.findingIdentifiers(findingIdentifiers);

                    NoteUpdate note = exchange.getIn().getHeader(SecurityHubConstants.NOTE, NoteUpdate.class);
                    if (note != null) {
                        builder.note(note);
                    }

                    SeverityUpdate severity = exchange.getIn().getHeader(SecurityHubConstants.SEVERITY, SeverityUpdate.class);
                    if (severity != null) {
                        builder.severity(severity);
                    }

                    WorkflowUpdate workflow = exchange.getIn().getHeader(SecurityHubConstants.WORKFLOW, WorkflowUpdate.class);
                    if (workflow != null) {
                        builder.workflow(workflow);
                    }

                    String verificationState
                            = exchange.getIn().getHeader(SecurityHubConstants.VERIFICATION_STATE, String.class);
                    if (verificationState != null) {
                        builder.verificationState(VerificationState.fromValue(verificationState));
                    }

                    Integer confidence = exchange.getIn().getHeader(SecurityHubConstants.CONFIDENCE, Integer.class);
                    if (confidence != null) {
                        builder.confidence(confidence);
                    }

                    Integer criticality = exchange.getIn().getHeader(SecurityHubConstants.CRITICALITY, Integer.class);
                    if (criticality != null) {
                        builder.criticality(criticality);
                    }

                    Map<String, String> userDefinedFields
                            = exchange.getIn().getHeader(SecurityHubConstants.USER_DEFINED_FIELDS, Map.class);
                    if (userDefinedFields != null) {
                        builder.userDefinedFields(userDefinedFields);
                    }

                    List<RelatedFinding> relatedFindings
                            = exchange.getIn().getHeader(SecurityHubConstants.RELATED_FINDINGS, List.class);
                    if (relatedFindings != null) {
                        builder.relatedFindings(relatedFindings);
                    }

                    List<String> types = exchange.getIn().getHeader(SecurityHubConstants.TYPES, List.class);
                    if (types != null) {
                        builder.types(types);
                    }

                    return securityHubClient.batchUpdateFindings(builder.build());
                },
                "Batch Update Findings",
                (BatchUpdateFindingsResponse response, Message message) -> {
                    message.setHeader(SecurityHubConstants.PROCESSED_FINDINGS, response.processedFindings());
                    message.setHeader(SecurityHubConstants.UNPROCESSED_FINDINGS, response.unprocessedFindings());
                });
    }

    private void getFindingHistory(SecurityHubClient securityHubClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetFindingHistoryRequest.class,
                securityHubClient::getFindingHistory,
                () -> {
                    GetFindingHistoryRequest.Builder builder = GetFindingHistoryRequest.builder();

                    String findingId = exchange.getIn().getHeader(SecurityHubConstants.FINDING_ID, String.class);
                    String productArn = exchange.getIn().getHeader(SecurityHubConstants.PRODUCT_ARN, String.class);

                    if (ObjectHelper.isEmpty(findingId) || ObjectHelper.isEmpty(productArn)) {
                        throw new IllegalArgumentException("Finding ID and Product ARN must be specified");
                    }

                    AwsSecurityFindingIdentifier findingIdentifier = AwsSecurityFindingIdentifier.builder()
                            .id(findingId)
                            .productArn(productArn)
                            .build();
                    builder.findingIdentifier(findingIdentifier);

                    String nextToken = exchange.getIn().getHeader(SecurityHubConstants.NEXT_TOKEN, String.class);
                    if (nextToken != null) {
                        builder.nextToken(nextToken);
                    }

                    Integer maxResults = exchange.getIn().getHeader(SecurityHubConstants.MAX_RESULTS, Integer.class);
                    if (maxResults != null) {
                        builder.maxResults(maxResults);
                    }

                    return securityHubClient.getFindingHistory(builder.build());
                },
                "Get Finding History",
                (GetFindingHistoryResponse response, Message message) -> {
                    message.setHeader(SecurityHubConstants.NEXT_TOKEN, response.nextToken());
                });
    }

    private void describeHub(SecurityHubClient securityHubClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeHubRequest.class,
                securityHubClient::describeHub,
                () -> {
                    DescribeHubRequest.Builder builder = DescribeHubRequest.builder();
                    return securityHubClient.describeHub(builder.build());
                },
                "Describe Hub",
                (DescribeHubResponse response, Message message) -> {
                    // Response body contains the hub details
                });
    }

    private void listEnabledProductsForImport(SecurityHubClient securityHubClient, Exchange exchange)
            throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListEnabledProductsForImportRequest.class,
                securityHubClient::listEnabledProductsForImport,
                () -> {
                    ListEnabledProductsForImportRequest.Builder builder = ListEnabledProductsForImportRequest.builder();

                    String nextToken = exchange.getIn().getHeader(SecurityHubConstants.NEXT_TOKEN, String.class);
                    if (nextToken != null) {
                        builder.nextToken(nextToken);
                    }

                    Integer maxResults = exchange.getIn().getHeader(SecurityHubConstants.MAX_RESULTS, Integer.class);
                    if (maxResults != null) {
                        builder.maxResults(maxResults);
                    }

                    return securityHubClient.listEnabledProductsForImport(builder.build());
                },
                "List Enabled Products For Import",
                (ListEnabledProductsForImportResponse response, Message message) -> {
                    message.setHeader(SecurityHubConstants.NEXT_TOKEN, response.nextToken());
                });
    }

    @Override
    public SecurityHubEndpoint getEndpoint() {
        return (SecurityHubEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes a Security Hub operation with POJO request support.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName)
            throws InvalidPayloadException {
        executeOperation(exchange, requestClass, pojoExecutor, headerExecutor, operationName, null);
    }

    /**
     * Executes a Security Hub operation with POJO request support and optional response post-processing.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName,
            BiConsumer<RES, Message> responseProcessor)
            throws InvalidPayloadException {

        RES result;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (requestClass.isInstance(payload)) {
                try {
                    result = pojoExecutor.apply(requestClass.cast(payload));
                } catch (AwsServiceException ase) {
                    LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            } else {
                throw new IllegalArgumentException(
                        String.format("Expected body of type %s but was %s",
                                requestClass.getName(),
                                payload != null ? payload.getClass().getName() : "null"));
            }
        } else {
            try {
                result = headerExecutor.get();
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (responseProcessor != null) {
            responseProcessor.accept(result, message);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new SecurityHubProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }
}
