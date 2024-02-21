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
package org.apache.camel.component.aws2.timestream.query;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;
import software.amazon.awssdk.services.timestreamquery.model.*;

public class AmazonTimestreamQueryClientMock implements TimestreamQueryClient {

    public AmazonTimestreamQueryClientMock() {
    }

    @Override
    public DescribeEndpointsResponse describeEndpoints(DescribeEndpointsRequest describeEndpointsRequest) {
        DescribeEndpointsResponse.Builder result = DescribeEndpointsResponse.builder();
        Endpoint.Builder endpoint = Endpoint.builder();
        endpoint.address("query.timestream.region.amazonaws.com");
        List<Endpoint> endpointList = new ArrayList<>();
        endpointList.add(endpoint.build());
        result.endpoints(endpointList);
        return result.build();
    }

    @Override
    public CancelQueryResponse cancelQuery(CancelQueryRequest cancelQueryRequest) {
        CancelQueryResponse.Builder result = CancelQueryResponse.builder();
        result.cancellationMessage("Query Cancelled");
        return result.build();
    }

    @Override
    public CreateScheduledQueryResponse createScheduledQuery(CreateScheduledQueryRequest createScheduledQueryRequest) {
        CreateScheduledQueryResponse.Builder result = CreateScheduledQueryResponse.builder();
        result.arn("aws-timestream:test:scheduled-query:arn");
        return result.build();
    }

    @Override
    public DeleteScheduledQueryResponse deleteScheduledQuery(DeleteScheduledQueryRequest deleteScheduledQueryRequest) {
        DeleteScheduledQueryResponse.Builder result = DeleteScheduledQueryResponse.builder();
        return result.build();
    }

    @Override
    public DescribeScheduledQueryResponse describeScheduledQuery(DescribeScheduledQueryRequest describeScheduledQueryRequest) {
        DescribeScheduledQueryResponse.Builder result = DescribeScheduledQueryResponse.builder();
        ScheduledQueryDescription.Builder description = ScheduledQueryDescription.builder();
        description.arn("aws-timestream:test:scheduled-query:arn");
        result.scheduledQuery(description.build());
        return result.build();
    }

    @Override
    public ExecuteScheduledQueryResponse executeScheduledQuery(ExecuteScheduledQueryRequest executeScheduledQueryRequest) {
        ExecuteScheduledQueryResponse.Builder result = ExecuteScheduledQueryResponse.builder();
        return result.build();
    }

    @Override
    public ListScheduledQueriesResponse listScheduledQueries(ListScheduledQueriesRequest listScheduledQueriesRequest) {
        ListScheduledQueriesResponse.Builder result = ListScheduledQueriesResponse.builder();
        List<ScheduledQuery> scheduledQueries = new ArrayList<>();
        ScheduledQuery.Builder scheduledQuery = ScheduledQuery.builder();
        scheduledQuery.arn("aws-timestream:test:scheduled-query:arn");
        scheduledQueries.add(scheduledQuery.build());
        result.scheduledQueries(scheduledQueries);
        return result.build();
    }

    @Override
    public PrepareQueryResponse prepareQuery(PrepareQueryRequest prepareQueryRequest) {
        PrepareQueryResponse.Builder result = PrepareQueryResponse.builder();
        result.queryString("select * from test_db");
        return result.build();
    }

    @Override
    public QueryResponse query(QueryRequest queryRequest) {
        QueryResponse.Builder result = QueryResponse.builder();
        result.queryId("query-1");
        return result.build();
    }

    @Override
    public UpdateScheduledQueryResponse updateScheduledQuery(UpdateScheduledQueryRequest updateScheduledQueryRequest) {
        UpdateScheduledQueryResponse.Builder result = UpdateScheduledQueryResponse.builder();
        return result.build();
    }

    @Override
    public String serviceName() {
        return TimestreamQueryClient.SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
