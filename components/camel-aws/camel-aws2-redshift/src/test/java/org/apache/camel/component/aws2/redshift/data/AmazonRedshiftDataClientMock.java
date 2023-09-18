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

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;
import software.amazon.awssdk.services.redshiftdata.model.BatchExecuteStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.BatchExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.CancelStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.CancelStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.DescribeTableRequest;
import software.amazon.awssdk.services.redshiftdata.model.DescribeTableResponse;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementResponse;
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
import software.amazon.awssdk.services.redshiftdata.model.StatementData;
import software.amazon.awssdk.services.redshiftdata.model.TableMember;

public class AmazonRedshiftDataClientMock implements RedshiftDataClient {

    public AmazonRedshiftDataClientMock() {
    }

    @Override
    public BatchExecuteStatementResponse batchExecuteStatement(BatchExecuteStatementRequest batchExecuteStatementRequest) {
        BatchExecuteStatementResponse.Builder result = BatchExecuteStatementResponse.builder();
        result.id("statement1");
        return result.build();
    }

    @Override
    public CancelStatementResponse cancelStatement(CancelStatementRequest cancelStatementRequest) {
        CancelStatementResponse.Builder result = CancelStatementResponse.builder();
        result.status(true);
        return result.build();
    }

    @Override
    public DescribeStatementResponse describeStatement(DescribeStatementRequest describeStatementRequest) {
        DescribeStatementResponse.Builder result = DescribeStatementResponse.builder();
        result.id("statement1");
        return result.build();
    }

    @Override
    public DescribeTableResponse describeTable(DescribeTableRequest describeTableRequest) {
        DescribeTableResponse.Builder result = DescribeTableResponse.builder();
        result.tableName("table1");
        return result.build();
    }

    @Override
    public ExecuteStatementResponse executeStatement(ExecuteStatementRequest executeStatementRequest) {
        ExecuteStatementResponse.Builder result = ExecuteStatementResponse.builder();
        result.id("statement1");
        return result.build();
    }

    @Override
    public GetStatementResultResponse getStatementResult(GetStatementResultRequest getStatementResultRequest) {
        GetStatementResultResponse.Builder result = GetStatementResultResponse.builder();
        result.totalNumRows(10L);
        return result.build();
    }

    @Override
    public ListDatabasesResponse listDatabases(ListDatabasesRequest listDatabasesRequest) {
        ListDatabasesResponse.Builder result = ListDatabasesResponse.builder();
        result.databases("database1", "database2");
        return result.build();
    }

    @Override
    public ListSchemasResponse listSchemas(ListSchemasRequest listSchemasRequest) {
        ListSchemasResponse.Builder result = ListSchemasResponse.builder();
        result.schemas("schema1", "schema2");
        return result.build();
    }

    @Override
    public ListStatementsResponse listStatements(ListStatementsRequest listStatementsRequest) {
        ListStatementsResponse.Builder result = ListStatementsResponse.builder();
        StatementData data = StatementData.builder().statementName("statement1").build();
        List<StatementData> statements = new ArrayList<>();
        statements.add(data);
        return result.statements(statements).build();
    }

    @Override
    public ListTablesResponse listTables(ListTablesRequest listTablesRequest) {
        ListTablesResponse.Builder result = ListTablesResponse.builder();
        TableMember.Builder table = TableMember.builder();
        table.name("table1");
        List<TableMember> tables = new ArrayList<>();
        tables.add(table.build());
        result.tables(tables);
        return result.build();
    }

    @Override
    public String serviceName() {
        return RedshiftDataClient.SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
