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
package org.apache.camel.component.aws2.redshift.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.services.redshiftdata.model.BatchExecuteStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.BatchExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.DescribeTableRequest;
import software.amazon.awssdk.services.redshiftdata.model.DescribeTableResponse;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.ExecutionMode;
import software.amazon.awssdk.services.redshiftdata.model.GetStatementResultRequest;
import software.amazon.awssdk.services.redshiftdata.model.GetStatementResultResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesRequest;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListSchemasRequest;
import software.amazon.awssdk.services.redshiftdata.model.ListSchemasResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListStatementsRequest;
import software.amazon.awssdk.services.redshiftdata.model.ListStatementsResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListTablesRequest;
import software.amazon.awssdk.services.redshiftdata.model.ListTablesResponse;
import software.amazon.awssdk.services.redshiftdata.model.ResultFormatString;
import software.amazon.awssdk.services.redshiftdata.model.SqlParameter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The request fields that the producer takes from headers: the token of the next page, the filters of listStatements,
 * and the wait, session, format, execution mode and parameters of the statement operations.
 */
public class RedshiftData2ProducerHeadersTest extends CamelTestSupport {

    private final AtomicReference<SdkRequest> lastRequest = new AtomicReference<>();

    @BindToRegistry("capturingClient")
    AmazonRedshiftDataClientMock client = new AmazonRedshiftDataClientMock() {
        @Override
        public ListDatabasesResponse listDatabases(ListDatabasesRequest request) {
            lastRequest.set(request);
            return super.listDatabases(request);
        }

        @Override
        public ListSchemasResponse listSchemas(ListSchemasRequest request) {
            lastRequest.set(request);
            return super.listSchemas(request);
        }

        @Override
        public ListStatementsResponse listStatements(ListStatementsRequest request) {
            lastRequest.set(request);
            return super.listStatements(request);
        }

        @Override
        public ListTablesResponse listTables(ListTablesRequest request) {
            lastRequest.set(request);
            return super.listTables(request);
        }

        @Override
        public DescribeTableResponse describeTable(DescribeTableRequest request) {
            lastRequest.set(request);
            return super.describeTable(request);
        }

        @Override
        public GetStatementResultResponse getStatementResult(GetStatementResultRequest request) {
            lastRequest.set(request);
            return super.getStatementResult(request);
        }

        @Override
        public ExecuteStatementResponse executeStatement(ExecuteStatementRequest request) {
            lastRequest.set(request);
            return super.executeStatement(request);
        }

        @Override
        public BatchExecuteStatementResponse batchExecuteStatement(BatchExecuteStatementRequest request) {
            lastRequest.set(request);
            return super.batchExecuteStatement(request);
        }
    };

    private SdkRequest send(String operation, Map<String, Object> headers) {
        lastRequest.set(null);
        template.requestBodyAndHeaders("direct:start", null, withOperation(operation, headers));
        return lastRequest.get();
    }

    private static Map<String, Object> withOperation(String operation, Map<String, Object> headers) {
        HashMap<String, Object> all = new HashMap<>(headers);
        all.put(RedshiftData2Constants.OPERATION, operation);
        return all;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "listDatabases", "listSchemas", "listStatements", "listTables", "describeTable", "getStatementResult" })
    public void nextTokenHeaderAsksForTheNextPage(String operation) {
        SdkRequest request = send(operation, Map.of(RedshiftData2Constants.NEXT_TOKEN, "page-2"));

        assertEquals("page-2", request.getValueForField("NextToken", String.class).orElse(null), operation);
    }

    @Test
    public void listStatementsIsFilteredByClusterWorkgroupAndDatabase() {
        ListStatementsRequest request = (ListStatementsRequest) send("listStatements", Map.of(
                RedshiftData2Constants.CLUSTER_IDENTIFIER, "cluster-1",
                RedshiftData2Constants.WORKGROUP_NAME, "workgroup-1",
                RedshiftData2Constants.DATABASE, "dev"));

        assertEquals("cluster-1", request.clusterIdentifier());
        assertEquals("workgroup-1", request.workgroupName());
        assertEquals("dev", request.database());
    }

    @Test
    public void getStatementResultWaits() {
        GetStatementResultRequest request = (GetStatementResultRequest) send("getStatementResult", Map.of(
                RedshiftData2Constants.STATEMENT_ID, "statement1",
                RedshiftData2Constants.WAIT_TIME_SECONDS, 5));

        assertEquals(5, request.waitTimeSeconds());
    }

    @Test
    public void executeStatementTakesTheWaitSessionAndFormatHeaders() {
        ExecuteStatementRequest request = (ExecuteStatementRequest) send("executeStatement", Map.of(
                RedshiftData2Constants.SQL_STATEMENT, "select 1",
                RedshiftData2Constants.WAIT_TIME_SECONDS, 10,
                RedshiftData2Constants.RESULT_FORMAT, "CSV",
                RedshiftData2Constants.SESSION_ID, "session-1",
                RedshiftData2Constants.SESSION_KEEP_ALIVE_SECONDS, 60));

        assertEquals(10, request.waitTimeSeconds());
        assertEquals(ResultFormatString.CSV, request.resultFormat());
        assertEquals("session-1", request.sessionId());
        assertEquals(60, request.sessionKeepAliveSeconds());
    }

    @Test
    public void batchExecuteStatementTakesTheParametersAndTheExecutionMode() {
        List<SqlParameter> parameters = List.of(SqlParameter.builder().name("id").value("1").build());
        BatchExecuteStatementRequest request = (BatchExecuteStatementRequest) send("batchExecuteStatement", Map.of(
                RedshiftData2Constants.SQL_STATEMENT_LIST, List.of("insert into t values (:id)"),
                RedshiftData2Constants.SQL_PARAMETER_LIST, parameters,
                RedshiftData2Constants.EXECUTION_MODE, "AUTO_COMMIT",
                RedshiftData2Constants.WAIT_TIME_SECONDS, 3,
                RedshiftData2Constants.RESULT_FORMAT, "JSON",
                RedshiftData2Constants.SESSION_KEEP_ALIVE_SECONDS, 30));

        assertEquals(parameters, request.parameters());
        assertEquals(ExecutionMode.AUTO_COMMIT, request.executionMode());
        assertEquals(3, request.waitTimeSeconds());
        assertEquals(ResultFormatString.JSON, request.resultFormat());
        assertEquals(30, request.sessionKeepAliveSeconds());
    }

    @Test
    public void noOperationFailsWithAClearMessage() {
        assertThatThrownBy(() -> template.requestBody("direct:noOperation", "select 1"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "No operation: set the " + RedshiftData2Constants.OPERATION + " header or the operation option");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws2-redshift-data://test?awsRedshiftDataClient=#capturingClient&operation=listDatabases");
                from("direct:noOperation")
                        .to("aws2-redshift-data://test?awsRedshiftDataClient=#capturingClient");
            }
        };
    }
}
