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
package org.apache.camel.component.aws2.timestream.write;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;
import software.amazon.awssdk.services.timestreamwrite.model.*;

public class AmazonTimestreamWriteClientMock implements TimestreamWriteClient {

    public AmazonTimestreamWriteClientMock() {
    }

    @Override
    public DescribeEndpointsResponse describeEndpoints(DescribeEndpointsRequest describeEndpointsRequest) {
        DescribeEndpointsResponse.Builder result = DescribeEndpointsResponse.builder();
        Endpoint.Builder endpoint = Endpoint.builder();
        endpoint.address("ingest.timestream.region.amazonaws.com");
        List<Endpoint> endpointList = new ArrayList<>();
        endpointList.add(endpoint.build());
        result.endpoints(endpointList);
        return result.build();
    }

    @Override
    public CreateBatchLoadTaskResponse createBatchLoadTask(CreateBatchLoadTaskRequest createBatchLoadTaskRequest) {
        CreateBatchLoadTaskResponse.Builder result = CreateBatchLoadTaskResponse.builder();
        result.taskId("task-1");
        return result.build();
    }

    @Override
    public CreateDatabaseResponse createDatabase(CreateDatabaseRequest createDatabaseRequest) {
        CreateDatabaseResponse.Builder result = CreateDatabaseResponse.builder();
        result.database(Database.builder().databaseName("testDb").build());
        return result.build();
    }

    @Override
    public CreateTableResponse createTable(CreateTableRequest createTableRequest) {
        CreateTableResponse.Builder result = CreateTableResponse.builder();
        result.table(Table.builder().tableName("testTable").build());
        return result.build();
    }

    @Override
    public DeleteDatabaseResponse deleteDatabase(DeleteDatabaseRequest deleteDatabaseRequest) {
        DeleteDatabaseResponse.Builder result = DeleteDatabaseResponse.builder();
        return result.build();
    }

    @Override
    public DeleteTableResponse deleteTable(DeleteTableRequest deleteTableRequest) {
        DeleteTableResponse.Builder result = DeleteTableResponse.builder();
        return result.build();
    }

    @Override
    public DescribeBatchLoadTaskResponse describeBatchLoadTask(DescribeBatchLoadTaskRequest describeBatchLoadTaskRequest) {
        DescribeBatchLoadTaskResponse.Builder result = DescribeBatchLoadTaskResponse.builder();
        result.batchLoadTaskDescription(BatchLoadTaskDescription.builder().taskId("task-1").build());
        return result.build();
    }

    @Override
    public DescribeDatabaseResponse describeDatabase(DescribeDatabaseRequest describeDatabaseRequest) {
        DescribeDatabaseResponse.Builder result = DescribeDatabaseResponse.builder();
        result.database(Database.builder().databaseName("testDb").build());
        return result.build();
    }

    @Override
    public DescribeTableResponse describeTable(DescribeTableRequest describeTableRequest) {
        DescribeTableResponse.Builder result = DescribeTableResponse.builder();
        result.table(Table.builder().tableName("testTable").build());
        return result.build();
    }

    @Override
    public ListBatchLoadTasksResponse listBatchLoadTasks(ListBatchLoadTasksRequest listBatchLoadTasksRequest) {
        ListBatchLoadTasksResponse.Builder result = ListBatchLoadTasksResponse.builder();
        BatchLoadTask.Builder batchLoadTask = BatchLoadTask.builder();
        batchLoadTask.taskId("task-1");
        List<BatchLoadTask> batchLoadTasks = new ArrayList<>();
        batchLoadTasks.add(batchLoadTask.build());
        result.batchLoadTasks(batchLoadTasks);
        return result.build();
    }

    @Override
    public ListDatabasesResponse listDatabases(ListDatabasesRequest listDatabasesRequest) {
        ListDatabasesResponse.Builder result = ListDatabasesResponse.builder();
        Database.Builder database = Database.builder();
        database.databaseName("testDb");
        List<Database> databases = new ArrayList<>();
        databases.add(database.build());
        result.databases(databases);
        return result.build();
    }

    @Override
    public ListTablesResponse listTables(ListTablesRequest listTablesRequest) {
        ListTablesResponse.Builder result = ListTablesResponse.builder();
        Table.Builder table = Table.builder().tableName("testTable");
        List<Table> tables = new ArrayList<>();
        tables.add(table.build());
        result.tables(tables);
        return result.build();
    }

    @Override
    public ResumeBatchLoadTaskResponse resumeBatchLoadTask(ResumeBatchLoadTaskRequest resumeBatchLoadTaskRequest) {
        ResumeBatchLoadTaskResponse.Builder result = ResumeBatchLoadTaskResponse.builder();
        return result.build();
    }

    @Override
    public UpdateDatabaseResponse updateDatabase(UpdateDatabaseRequest updateDatabaseRequest) {
        UpdateDatabaseResponse.Builder result = UpdateDatabaseResponse.builder();
        result.database(Database.builder().databaseName("testDb").build());
        return result.build();
    }

    @Override
    public UpdateTableResponse updateTable(UpdateTableRequest updateTableRequest) {
        UpdateTableResponse.Builder result = UpdateTableResponse.builder();
        result.table(Table.builder().tableName("testTable").build());
        return result.build();
    }

    @Override
    public WriteRecordsResponse writeRecords(WriteRecordsRequest writeRecordsRequest) {
        WriteRecordsResponse.Builder result = WriteRecordsResponse.builder();
        result.recordsIngested(RecordsIngested.builder().total(5).build());
        return result.build();
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
