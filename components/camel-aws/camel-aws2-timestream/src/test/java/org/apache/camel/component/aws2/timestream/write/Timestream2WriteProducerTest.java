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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.timestream.Timestream2Constants;
import org.apache.camel.component.aws2.timestream.Timestream2Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.timestreamwrite.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Timestream2WriteProducerTest extends CamelTestSupport {

    @BindToRegistry("awsTimestreamWriteClient")
    AmazonTimestreamWriteClientMock clientMock = new AmazonTimestreamWriteClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void timestreamDescribeWriteEndpointsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeWriteEndpoints", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeEndpoints);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeEndpointsResponse resultGet = (DescribeEndpointsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.endpoints().size());
        assertEquals("ingest.timestream.region.amazonaws.com", resultGet.endpoints().get(0).address());
    }

    @Test
    public void timestreamDescribeWriteEndpointsPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeWriteEndpointsPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeEndpoints);
                exchange.getIn().setBody(DescribeEndpointsRequest.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeEndpointsResponse resultGet = (DescribeEndpointsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.endpoints().size());
        assertEquals("ingest.timestream.region.amazonaws.com", resultGet.endpoints().get(0).address());
    }

    @Test
    public void timestreamCreateBatchLoadTaskTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createBatchLoadTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.createBatchLoadTask);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateBatchLoadTaskResponse resultGet = (CreateBatchLoadTaskResponse) exchange.getIn().getBody();
        assertEquals("task-1", resultGet.taskId());
    }

    @Test
    public void timestreamDescribeBatchLoadTaskTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeBatchLoadTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeBatchLoadTask);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeBatchLoadTaskResponse resultGet = (DescribeBatchLoadTaskResponse) exchange.getIn().getBody();
        assertEquals("task-1", resultGet.batchLoadTaskDescription().taskId());
    }

    @Test
    public void timestreamResumeBatchLoadTaskTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:resumeBatchLoadTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.resumeBatchLoadTask);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ResumeBatchLoadTaskResponse resultGet = (ResumeBatchLoadTaskResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamListBatchLoadTasksTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listBatchLoadTasks", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.listBatchLoadTasks);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListBatchLoadTasksResponse resultGet = (ListBatchLoadTasksResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.batchLoadTasks().size());
        assertEquals("task-1", resultGet.batchLoadTasks().get(0).taskId());
    }

    @Test
    public void timestreamCreateDatabaseTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createDatabase", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.createDatabase);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateDatabaseResponse resultGet = (CreateDatabaseResponse) exchange.getIn().getBody();
        assertEquals("testDb", resultGet.database().databaseName());
    }

    @Test
    public void timestreamDeleteDatabaseTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteDatabase", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.deleteDatabase);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteDatabaseResponse resultGet = (DeleteDatabaseResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamDescribeDatabaseTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeDatabase", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeDatabase);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeDatabaseResponse resultGet = (DescribeDatabaseResponse) exchange.getIn().getBody();
        assertEquals("testDb", resultGet.database().databaseName());
    }

    @Test
    public void timestreamUpdateDatabaseTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateDatabase", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.updateDatabase);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        UpdateDatabaseResponse resultGet = (UpdateDatabaseResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamListDatabasesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listDatabases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.listDatabases);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListDatabasesResponse resultGet = (ListDatabasesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.databases().size());
        assertEquals("testDb", resultGet.databases().get(0).databaseName());
    }

    @Test
    public void timestreamCreateTableTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createTable", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.createTable);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateTableResponse resultGet = (CreateTableResponse) exchange.getIn().getBody();
        assertEquals("testTable", resultGet.table().tableName());
    }

    @Test
    public void timestreamDeleteTableTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteTable", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.deleteTable);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteTableResponse resultGet = (DeleteTableResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamDescribeTableTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeTable", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeTable);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeTableResponse resultGet = (DescribeTableResponse) exchange.getIn().getBody();
        assertEquals("testTable", resultGet.table().tableName());
    }

    @Test
    public void timestreamUpdateTableTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateTable", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.updateTable);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        UpdateTableResponse resultGet = (UpdateTableResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamListTablesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listTables", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.listTables);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListTablesResponse resultGet = (ListTablesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.tables().size());
        assertEquals("testTable", resultGet.tables().get(0).tableName());
    }

    @Test
    public void timestreamWriteRecordsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:writeRecords", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.writeRecords);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        WriteRecordsResponse resultGet = (WriteRecordsResponse) exchange.getIn().getBody();
        assertEquals(5, resultGet.recordsIngested().total());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:describeWriteEndpoints")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=describeEndpoints")
                        .to("mock:result");
                from("direct:describeWriteEndpointsPojo")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=describeEndpoints&pojoRequest=true")
                        .to("mock:result");
                from("direct:createBatchLoadTask")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=createBatchLoadTask")
                        .to("mock:result");
                from("direct:describeBatchLoadTask")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=describeBatchLoadTask")
                        .to("mock:result");
                from("direct:resumeBatchLoadTask")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=resumeBatchLoadTask")
                        .to("mock:result");
                from("direct:listBatchLoadTasks")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=listBatchLoadTasks")
                        .to("mock:result");
                from("direct:createDatabase")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=createDatabase")
                        .to("mock:result");
                from("direct:deleteDatabase")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=deleteDatabase")
                        .to("mock:result");
                from("direct:describeDatabase")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=describeDatabase")
                        .to("mock:result");
                from("direct:updateDatabase")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=updateDatabase")
                        .to("mock:result");
                from("direct:listDatabases")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=listDatabases")
                        .to("mock:result");
                from("direct:createTable")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=createTable")
                        .to("mock:result");
                from("direct:deleteTable")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=deleteTable")
                        .to("mock:result");
                from("direct:describeTable")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=describeTable")
                        .to("mock:result");
                from("direct:updateTable")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=updateTable")
                        .to("mock:result");
                from("direct:listTables")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=listTables")
                        .to("mock:result");
                from("direct:writeRecords")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=writeRecords")
                        .to("mock:result");

            }
        };
    }
}
