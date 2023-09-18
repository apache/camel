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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.redshiftdata.model.BatchExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.CancelStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.DescribeTableResponse;
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementResponse;
import software.amazon.awssdk.services.redshiftdata.model.GetStatementResultResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListSchemasResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListStatementsResponse;
import software.amazon.awssdk.services.redshiftdata.model.ListTablesResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedshiftData2ProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void redshiftDataListDatabasesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listDatabases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.listDatabases);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListDatabasesResponse resultGet = (ListDatabasesResponse) exchange.getIn().getBody();
        List<String> resultList = new ArrayList<>();
        resultList.add("database1");
        resultList.add("database2");
        assertEquals(resultList, resultGet.databases());
    }

    @Test
    public void redshiftDataListSchemasTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listSchemas", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.listSchemas);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListSchemasResponse resultGet = (ListSchemasResponse) exchange.getIn().getBody();
        assertEquals("schema1", resultGet.schemas().get(0));
        assertEquals("schema2", resultGet.schemas().get(1));
    }

    @Test
    public void redshiftDataListStatementsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listStatements", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.listStatements);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListStatementsResponse resultGet = (ListStatementsResponse) exchange.getIn().getBody();
        assertEquals("statement1", resultGet.statements().get(0).statementName());
    }

    @Test
    public void redshiftDataListTablesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listTables", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.listTables);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListTablesResponse resultGet = (ListTablesResponse) exchange.getIn().getBody();
        assertEquals("table1", resultGet.tables().get(0).name());
    }

    @Test
    public void redshiftDataDescribeTableTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeTable", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.describeTable);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeTableResponse resultGet = (DescribeTableResponse) exchange.getIn().getBody();
        assertEquals("table1", resultGet.tableName());
    }

    @Test
    public void redshiftDataExecuteStatementTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:executeStatement", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.executeStatement);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ExecuteStatementResponse resultGet = (ExecuteStatementResponse) exchange.getIn().getBody();
        assertEquals("statement1", resultGet.id());
    }

    @Test
    public void redshiftDataBatchExecuteStatementTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:batchExecuteStatement", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.batchExecuteStatement);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        BatchExecuteStatementResponse resultGet = (BatchExecuteStatementResponse) exchange.getIn().getBody();
        assertEquals("statement1", resultGet.id());
    }

    @Test
    public void redshiftDataCancelStatementTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:cancelStatement", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.cancelStatement);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CancelStatementResponse resultGet = (CancelStatementResponse) exchange.getIn().getBody();
        assertEquals(true, resultGet.status());
    }

    @Test
    public void redshiftDataDescribeStatementTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeStatement", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.describeStatement);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeStatementResponse resultGet = (DescribeStatementResponse) exchange.getIn().getBody();
        assertEquals("statement1", resultGet.id());
    }

    @Test
    public void redshiftDataGetStatementResultTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getStatementResult", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.getStatementResult);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetStatementResultResponse resultGet = (GetStatementResultResponse) exchange.getIn().getBody();
        assertEquals(10, resultGet.totalNumRows());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/aws2/redshift/data/RedshiftDataComponentSpringTest-context.xml");
    }
}
