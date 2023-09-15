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
package org.apache.camel.component.aws2.redshiftdata;

import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;
import software.amazon.awssdk.services.redshiftdata.model.*;

public class AmazonRedshiftDataClientMock implements RedshiftDataClient {

    public AmazonRedshiftDataClientMock() {
    }

    @Override
    public BatchExecuteStatementResponse batchExecuteStatement(BatchExecuteStatementRequest batchExecuteStatementRequest) {
        return null;
    }

    @Override
    public CancelStatementResponse cancelStatement(CancelStatementRequest cancelStatementRequest) {
        return null;
    }

    @Override
    public DescribeStatementResponse describeStatement(DescribeStatementRequest describeStatementRequest) {
        return null;
    }

    @Override
    public DescribeTableResponse describeTable(DescribeTableRequest describeTableRequest) {
        return null;
    }

    @Override
    public ExecuteStatementResponse executeStatement(ExecuteStatementRequest executeStatementRequest) {
        return null;
    }

    @Override
    public GetStatementResultResponse getStatementResult(GetStatementResultRequest getStatementResultRequest) {
        return null;
    }

    @Override
    public ListDatabasesResponse listDatabases(ListDatabasesRequest listDatabasesRequest) {
        ListDatabasesResponse.Builder result = ListDatabasesResponse.builder();
        result.databases("database1", "database2");
        return result.build();
    }

    @Override
    public ListSchemasResponse listSchemas(ListSchemasRequest listSchemasRequest) {
        return null;
    }

    @Override
    public ListStatementsResponse listStatements(ListStatementsRequest listStatementsRequest) {
        return null;
    }

    @Override
    public ListTablesResponse listTables(ListTablesRequest listTablesRequest) {
        return null;
    }

    @Override
    public String serviceName() {
        return RedshiftDataClient.SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
