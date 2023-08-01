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
package org.apache.camel.component.aws2.athena;

import java.util.Arrays;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.EncryptionConfiguration;
import software.amazon.awssdk.services.athena.model.EncryptionOption;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsRequest;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * A Producer that runs queries with the AWS Athena Service.
 */
public class Athena2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Athena2Producer.class);

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Athena2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Athena2Operations operation = determineOperation(exchange);
        switch (operation) {
            case getQueryExecution:
                getQueryExecution(getEndpoint().getAthenaClient(), exchange);
                break;
            case getQueryResults:
                getQueryResults(getEndpoint().getAthenaClient(), exchange);
                break;
            case listQueryExecutions:
                listQueryExecutions(getEndpoint().getAthenaClient(), exchange);
                break;
            case startQueryExecution:
                startQueryExecution(getEndpoint().getAthenaClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid operation [" + operation + "] specified.  Must be one of "
                                                   + Arrays.asList(Athena2Operations.values()));
        }
    }

    private Athena2Operations determineOperation(Exchange exchange) {
        Athena2Operations operation = exchange.getIn().getHeader(Athena2Constants.OPERATION, Athena2Operations.class);
        if (operation == null) {
            Athena2Operations defaultOperation = Athena2Operations.startQueryExecution;
            operation = getConfiguration().getOperation() == null ? defaultOperation : getConfiguration().getOperation();
        }
        return operation;
    }

    /**
     * Invokes Athena's GetQueryExecution API.
     */
    private void getQueryExecution(AthenaClient athenaClient, Exchange exchange) {
        String queryExecutionId = determineQueryExecutionId(exchange);
        GetQueryExecutionResponse getQueryExecutionResponse = doGetQueryExecution(queryExecutionId, athenaClient);
        Message message = getMessageForResponse(exchange);
        message.setHeader(Athena2Constants.QUERY_EXECUTION_ID, getQueryExecutionResponse.queryExecution().queryExecutionId());
        message.setHeader(Athena2Constants.QUERY_EXECUTION_STATE, getQueryExecutionResponse.queryExecution().status().state());
        message.setHeader(Athena2Constants.OUTPUT_LOCATION,
                getQueryExecutionResponse.queryExecution().resultConfiguration().outputLocation());
        message.setBody(getQueryExecutionResponse);
    }

    private GetQueryExecutionResponse doGetQueryExecution(String queryExecutionId, AthenaClient athenaClient) {
        GetQueryExecutionRequest.Builder request = GetQueryExecutionRequest.builder();
        request.queryExecutionId(queryExecutionId);
        return athenaClient.getQueryExecution(request.build());
    }

    /**
     * Invokes Athena's GetQueryResults API. Can return different result formats by specifying a
     * {@link Athena2OutputType}.
     */
    private void getQueryResults(AthenaClient athenaClient, Exchange exchange) {
        String queryExecutionId = determineQueryExecutionId(exchange);

        Message message = getMessageForResponse(exchange);
        message.setHeader(Athena2Constants.QUERY_EXECUTION_ID, queryExecutionId);

        Athena2OutputType outputType = determineOutputType(exchange);
        if (outputType == Athena2OutputType.StreamList) {
            GetQueryResultsRequest request = doGetQueryResultsRequest(queryExecutionId, exchange).build();
            GetQueryResultsIterable response = athenaClient.getQueryResultsPaginator(request);
            message.setBody(response);
        } else if (outputType == Athena2OutputType.SelectList) {
            GetQueryResultsRequest request = doGetQueryResultsRequest(queryExecutionId, exchange).build();
            GetQueryResultsResponse response = athenaClient.getQueryResults(request);
            message.setHeader(Athena2Constants.NEXT_TOKEN, response.nextToken());
            message.setBody(response);
        } else if (outputType == Athena2OutputType.S3Pointer) {
            GetQueryExecutionResponse response = doGetQueryExecution(queryExecutionId, athenaClient);
            String outputLocation = response.queryExecution().resultConfiguration().outputLocation();
            message.setHeader(Athena2Constants.QUERY_EXECUTION_STATE, response.queryExecution().status().state());
            message.setHeader(Athena2Constants.OUTPUT_LOCATION,
                    response.queryExecution().resultConfiguration().outputLocation());
            message.setBody(outputLocation);
        } else {
            throw new IllegalArgumentException(
                    "AWS Athena output type [" + outputType + "] is not supported.  Must be "
                                               + "one of " + Arrays.asList(Athena2OutputType.values()));
        }
    }

    private Athena2OutputType determineOutputType(Exchange exchange) {
        Athena2OutputType outputType = exchange.getIn().getHeader(Athena2Constants.OUTPUT_TYPE, Athena2OutputType.class);

        if (ObjectHelper.isEmpty(outputType)) {
            outputType = getConfiguration().getOutputType();
            LOG.trace("AWS Athena output type is missing, using default one [{}]", outputType);
        }

        return outputType;
    }

    private GetQueryResultsRequest.Builder doGetQueryResultsRequest(String queryExecutionId, Exchange exchange) {
        GetQueryResultsRequest.Builder request = GetQueryResultsRequest.builder();

        request.queryExecutionId(queryExecutionId);

        Integer maxResults = determineMaxResults(exchange);
        request.maxResults(maxResults);

        String nextToken = determineNextToken(exchange);
        request.nextToken(nextToken);

        return request;
    }

    /**
     * Invokes Athena's ListQueryExecutions API.
     */
    private void listQueryExecutions(AthenaClient athenaClient, Exchange exchange) {
        ListQueryExecutionsRequest.Builder request = ListQueryExecutionsRequest.builder();

        Integer maxResults = determineMaxResults(exchange);
        request.maxResults(maxResults);

        String nextToken = determineNextToken(exchange);
        request.nextToken(nextToken);

        String workGroup = determineWorkGroup(exchange);
        request.workGroup(workGroup);

        ListQueryExecutionsResponse response = athenaClient.listQueryExecutions(request.build());
        Message message = getMessageForResponse(exchange);
        message.setHeader(Athena2Constants.NEXT_TOKEN, response.nextToken());
        message.setBody(response);
    }

    /**
     * Invokes Athena's StartQueryExecution API, with support for waiting for the query to complete and retry of failed
     * queries. See the section of the user docs titled "Waiting for Query Completion and Retrying Failed Queries" to
     * learn more about the wait/retry options.
     */
    private void startQueryExecution(AthenaClient athenaClient, Exchange exchange) {
        GetQueryExecutionResponse getQueryExecutionResponse = null;

        Athena2QueryHelper athena2QueryHelper = new Athena2QueryHelper(exchange, getConfiguration());
        String queryExecutionId = null;
        while (athena2QueryHelper.shouldAttempt()) {
            queryExecutionId = doStartQueryExecution(athenaClient, exchange).queryExecutionId();
            athena2QueryHelper.markAttempt();

            while (athena2QueryHelper.shouldWait()) {
                athena2QueryHelper.doWait();

                getQueryExecutionResponse = doGetQueryExecution(queryExecutionId, athenaClient);
                athena2QueryHelper.setStatusFrom(getQueryExecutionResponse);
            }
        }

        Message message = getMessageForResponse(exchange);
        message.setHeader(Athena2Constants.QUERY_EXECUTION_ID, queryExecutionId);
        message.setHeader(Athena2Constants.QUERY_EXECUTION_STATE, getQueryExecutionResponse == null
                ? null : getQueryExecutionResponse.queryExecution().status().state());
        message.setHeader(Athena2Constants.OUTPUT_LOCATION, getQueryExecutionResponse == null
                ? null : getQueryExecutionResponse.queryExecution().resultConfiguration().outputLocation());

        message.setHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, athena2QueryHelper.getAttempts());
        message.setHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, athena2QueryHelper.getElapsedMillis());

        message.setBody(getQueryExecutionResponse);
    }

    private StartQueryExecutionResponse doStartQueryExecution(AthenaClient athenaClient, Exchange exchange) {
        StartQueryExecutionRequest.Builder request = StartQueryExecutionRequest.builder();

        String clientRequestToken = determineClientRequestToken(exchange);
        if (ObjectHelper.isNotEmpty(clientRequestToken)) {
            request.clientRequestToken(clientRequestToken);
        }

        String database = determineDatabase(exchange);
        if (ObjectHelper.isNotEmpty(database)) {
            QueryExecutionContext.Builder queryExecutionContext = QueryExecutionContext.builder();
            queryExecutionContext.database(database);
            request.queryExecutionContext(queryExecutionContext.build());
        }

        String queryString = determineQueryString(exchange);
        request.queryString(queryString);

        ResultConfiguration.Builder resultConfiguration = ResultConfiguration.builder();
        EncryptionOption encryptionOption = determineEncryptionOption(exchange);
        String kmsKey = determineKmsKey(exchange);
        if (ObjectHelper.isNotEmpty(encryptionOption) || ObjectHelper.isNotEmpty(kmsKey)) {

            if (ObjectHelper.isEmpty(encryptionOption)) {
                throw new IllegalArgumentException("AWS Athena encryption option is required when setting KMS key.");
            }

            EncryptionConfiguration.Builder encryptionConfiguration = EncryptionConfiguration.builder();
            encryptionConfiguration.encryptionOption(encryptionOption);
            encryptionConfiguration.kmsKey(kmsKey);

            resultConfiguration.encryptionConfiguration(encryptionConfiguration.build());
        }

        String outputLocation = determineOutputLocation(exchange);
        resultConfiguration.outputLocation(outputLocation);

        request.resultConfiguration(resultConfiguration.build());

        String workGroup = determineWorkGroup(exchange);
        if (ObjectHelper.isNotEmpty(workGroup)) {
            request.workGroup(workGroup);
        }

        StartQueryExecutionResponse response = athenaClient.startQueryExecution(request.build());

        if (LOG.isTraceEnabled()) {
            LOG.trace("AWS Athena startQueryExecution successful, queryExecutionId={}", response.queryExecutionId());
        }
        return response;
    }

    private String determineQueryExecutionId(final Exchange exchange) {
        String queryExecutionId = exchange.getIn().getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class);

        if (ObjectHelper.isEmpty(queryExecutionId)) {
            queryExecutionId = getConfiguration().getQueryExecutionId();
        }

        if (ObjectHelper.isEmpty(queryExecutionId)) {
            throw new IllegalArgumentException("AWS Athena query execution id is required.");
        }

        return queryExecutionId;
    }

    private Integer determineMaxResults(final Exchange exchange) {
        Integer maxResults = exchange.getIn().getHeader(Athena2Constants.MAX_RESULTS, Integer.class);

        if (ObjectHelper.isEmpty(maxResults)) {
            maxResults = getConfiguration().getMaxResults();
            LOG.trace("AWS Athena max results is missing, using default one [{}]", maxResults);
        }

        return maxResults;
    }

    private boolean determineIncludeTrace(final Exchange exchange) {
        Boolean includeTrace = exchange.getIn().getHeader(Athena2Constants.INCLUDE_TRACE, Boolean.class);

        if (ObjectHelper.isEmpty(includeTrace)) {
            includeTrace = getConfiguration().isIncludeTrace();
            LOG.trace("AWS Athena include trace is missing, using default one [{}]", includeTrace);
        }

        return includeTrace;
    }

    private String determineNextToken(final Exchange exchange) {
        String nextToken = exchange.getIn().getHeader(Athena2Constants.NEXT_TOKEN, String.class);

        if (ObjectHelper.isEmpty(nextToken)) {
            nextToken = getConfiguration().getNextToken();
            LOG.trace("AWS Athena next token is missing, using default one [{}]", nextToken);
        }

        return nextToken;
    }

    private String determineClientRequestToken(final Exchange exchange) {
        String clientRequestToken = exchange.getIn().getHeader(Athena2Constants.CLIENT_REQUEST_TOKEN, String.class);

        if (ObjectHelper.isEmpty(clientRequestToken)) {
            clientRequestToken = getConfiguration().getClientRequestToken();
            LOG.trace("AWS Athena client request token is missing, using default one [{}]", clientRequestToken);
        }

        return clientRequestToken;
    }

    private String determineDatabase(final Exchange exchange) {
        String database = exchange.getIn().getHeader(Athena2Constants.DATABASE, String.class);

        if (ObjectHelper.isEmpty(database)) {
            database = getConfiguration().getDatabase();
            LOG.trace("AWS Athena database is missing, using default one [{}]", database);
        }

        return database;
    }

    private String determineQueryString(final Exchange exchange) {
        String queryString = exchange.getIn().getBody(String.class);

        if (ObjectHelper.isEmpty(queryString)) {
            queryString = exchange.getIn().getHeader(Athena2Constants.QUERY_STRING, String.class);
        }

        if (ObjectHelper.isEmpty(queryString)) {
            queryString = getConfiguration().getQueryString();
        }

        if (ObjectHelper.isEmpty(queryString)) {
            throw new IllegalArgumentException("AWS Athena query string is required.");
        }

        boolean includeTrace = determineIncludeTrace(exchange);
        if (includeTrace) {
            queryString = "-- {\"fromEndpointUri\": \"" + exchange.getFromEndpoint().getEndpointUri() + "\", "
                          + "\"exchangeId\": \"" + exchange.getExchangeId() + "\", "
                          + "\"exchangeFromRouteId\": \"" + exchange.getFromRouteId() + "\"}"
                          + "\n"
                          + queryString;
        }

        return queryString;
    }

    private EncryptionOption determineEncryptionOption(final Exchange exchange) {
        EncryptionOption encryptionOption
                = exchange.getIn().getHeader(Athena2Constants.ENCRYPTION_OPTION, EncryptionOption.class);

        if (ObjectHelper.isEmpty(encryptionOption)) {
            encryptionOption = getConfiguration().getEncryptionOption();
            LOG.trace("AWS Athena encryption option is missing, using default one [{}]", encryptionOption);
        }

        return encryptionOption;
    }

    private String determineKmsKey(final Exchange exchange) {
        String kmsKey = exchange.getIn().getHeader(Athena2Constants.KMS_KEY, String.class);

        if (ObjectHelper.isEmpty(kmsKey)) {
            kmsKey = getConfiguration().getKmsKey();
            LOG.trace("AWS Athena kms key is missing, using default one [{}]", kmsKey);
        }

        return kmsKey;
    }

    private String determineOutputLocation(final Exchange exchange) {
        String outputLocation = exchange.getIn().getHeader(Athena2Constants.OUTPUT_LOCATION, String.class);

        if (ObjectHelper.isEmpty(outputLocation)) {
            outputLocation = getConfiguration().getOutputLocation();
        }

        if (ObjectHelper.isEmpty(outputLocation)) {
            throw new IllegalArgumentException("AWS Athena output location is required.");
        }

        return outputLocation;
    }

    private String determineWorkGroup(final Exchange exchange) {
        String workGroup = exchange.getIn().getHeader(Athena2Constants.WORK_GROUP, String.class);

        if (ObjectHelper.isEmpty(workGroup)) {
            workGroup = getConfiguration().getWorkGroup();
            LOG.trace("AWS Athena work group is missing, using default one [{}]", workGroup);
        }

        return workGroup;
    }

    protected Athena2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public Athena2Endpoint getEndpoint() {
        return (Athena2Endpoint) super.getEndpoint();
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
            producerHealthCheck = new Athena2ProducerHealthCheck(getEndpoint(), id);
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
