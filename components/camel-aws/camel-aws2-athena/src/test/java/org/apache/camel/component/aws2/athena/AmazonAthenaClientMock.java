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

import java.util.LinkedList;
import java.util.Queue;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.AthenaServiceClientConfiguration;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsRequest;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.ResultSetMetadata;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

public class AmazonAthenaClientMock implements AthenaClient {

    private Queue<String> startQueryExecutionResults = new LinkedList<>();
    private Queue<QueryExecution> getQueryExecutionResults = new LinkedList<>();

    /**
     * Optionally provide a FIFO queue of results in the order they should be returned for each call to
     * {@link #startQueryExecution(StartQueryExecutionRequest)}.
     *
     * @param startQueryExecutionResults FIFO ordered queue of results in the order they will be returned
     */
    public void setStartQueryExecutionResults(LinkedList<String> startQueryExecutionResults) {
        this.startQueryExecutionResults = startQueryExecutionResults;
    }

    /**
     * Optionally provide a FIFO queue of results in the order they should be returned for each call to
     * {@link #getQueryExecution(GetQueryExecutionRequest)}.
     *
     * @param getQueryExecutionResults FIFO ordered queue of results in the order they will be returned
     */
    public void setGetQueryExecutionResults(LinkedList<QueryExecution> getQueryExecutionResults) {
        this.getQueryExecutionResults = getQueryExecutionResults;
    }

    @Override
    public GetQueryExecutionResponse getQueryExecution(GetQueryExecutionRequest getQueryExecutionRequest)
            throws SdkException {
        QueryExecution defaultResult = QueryExecution.builder()
                .queryExecutionId("11111111-1111-1111-1111-111111111111")
                .status(QueryExecutionStatus.builder().state(QueryExecutionState.SUCCEEDED).build())
                .resultConfiguration(ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                .build();
        QueryExecution result = getQueryExecutionResults.isEmpty() ? defaultResult : getQueryExecutionResults.poll();

        // if query execution id is 3333..., sleep for 500 ms to imitate a long running query
        if ("33333333-3333-3333-3333-333333333333".equals(result.queryExecutionId())) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // noop
            }
        }

        return GetQueryExecutionResponse.builder()
                .queryExecution(result)
                .build();
    }

    @Override
    public ListQueryExecutionsResponse listQueryExecutions(ListQueryExecutionsRequest listQueryExecutionsRequest)
            throws SdkException {
        return ListQueryExecutionsResponse.builder()
                .queryExecutionIds(
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222")
                .nextToken(listQueryExecutionsRequest.nextToken())
                .build();
    }

    @Override
    public StartQueryExecutionResponse startQueryExecution(StartQueryExecutionRequest startQueryExecutionRequest)
            throws SdkException {
        String defaultResult = "11111111-1111-1111-1111-111111111111";
        String result = startQueryExecutionResults.isEmpty() ? defaultResult : startQueryExecutionResults.poll();

        return StartQueryExecutionResponse.builder()
                .queryExecutionId(result)
                .build();
    }

    @Override
    public AthenaServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public GetQueryResultsResponse getQueryResults(GetQueryResultsRequest getQueryResultsRequest) throws SdkException {
        return GetQueryResultsResponse.builder()
                .nextToken(null)
                .resultSet(ResultSet.builder()
                        .resultSetMetadata(ResultSetMetadata.builder()
                                .columnInfo(ColumnInfo.builder().name("id").build())
                                .build())
                        .rows(Row.builder()
                                .data(Datum.builder().varCharValue("42").build())
                                .build())
                        .build())
                .build();
    }

    @Override
    public GetQueryResultsIterable getQueryResultsPaginator(GetQueryResultsRequest getQueryResultsRequest)
            throws SdkException {
        return new GetQueryResultsIterable(this, getQueryResultsRequest);
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {
        // noop
    }

}
