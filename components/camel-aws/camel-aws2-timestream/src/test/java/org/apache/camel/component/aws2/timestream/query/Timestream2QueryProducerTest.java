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
import software.amazon.awssdk.services.timestreamquery.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Timestream2QueryProducerTest extends CamelTestSupport {

    @BindToRegistry("awsTimestreamQueryClient")
    AmazonTimestreamQueryClientMock clientMock = new AmazonTimestreamQueryClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void timestreamDescribeQueryEndpointsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeQueryEndpoints", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeEndpoints);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeEndpointsResponse resultGet = (DescribeEndpointsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.endpoints().size());
        assertEquals("query.timestream.region.amazonaws.com", resultGet.endpoints().get(0).address());
    }

    @Test
    public void timestreamDescribeQueryEndpointsPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeQueryEndpointsPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeEndpoints);
                exchange.getIn().setBody(DescribeEndpointsRequest.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeEndpointsResponse resultGet = (DescribeEndpointsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.endpoints().size());
        assertEquals("query.timestream.region.amazonaws.com", resultGet.endpoints().get(0).address());
    }

    @Test
    public void timestreamCreateScheduledQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createScheduledQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.createScheduledQuery);
                DimensionMapping.Builder builder = DimensionMapping.builder();
                builder.dimensionValueType("dimensionValue");
                List<DimensionMapping> dimensionMappingList = new ArrayList<>();
                dimensionMappingList.add(builder.build());
                MultiMeasureMappings.Builder multiMeasureMapping = MultiMeasureMappings.builder();
                multiMeasureMapping.targetMultiMeasureName("MM1");
                List<MultiMeasureMappings> multiMeasureMappings = new ArrayList<>();
                multiMeasureMappings.add(multiMeasureMapping.build());
                exchange.getIn().setHeader(Timestream2Constants.DIMENSION_MAPPING_LIST, dimensionMappingList);
                exchange.getIn().setHeader(Timestream2Constants.MULTI_MEASURE_MAPPINGS, multiMeasureMappings);
                exchange.getIn().setHeader(Timestream2Constants.DATABASE_NAME, "TESTDB");
                exchange.getIn().setHeader(Timestream2Constants.TABLE_NAME, "TESTTABLE");
                exchange.getIn().setHeader(Timestream2Constants.TIME_COLUMN, "time");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateScheduledQueryResponse resultGet = (CreateScheduledQueryResponse) exchange.getIn().getBody();
        assertEquals("aws-timestream:test:scheduled-query:arn", resultGet.arn());
    }

    @Test
    public void timestreamDeleteScheduledQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteScheduledQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.deleteScheduledQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteScheduledQueryResponse resultGet = (DeleteScheduledQueryResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamExecuteScheduledQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:executeScheduledQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.executeScheduledQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ExecuteScheduledQueryResponse resultGet = (ExecuteScheduledQueryResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamUpdateScheduledQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateScheduledQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.updateScheduledQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        UpdateScheduledQueryResponse resultGet = (UpdateScheduledQueryResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void timestreamDescribeScheduledQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeScheduledQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.describeScheduledQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeScheduledQueryResponse resultGet = (DescribeScheduledQueryResponse) exchange.getIn().getBody();
        assertEquals("aws-timestream:test:scheduled-query:arn", resultGet.scheduledQuery().arn());
    }

    @Test
    public void timestreamListScheduledQueriesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listScheduledQueries", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.listScheduledQueries);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListScheduledQueriesResponse resultGet = (ListScheduledQueriesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.scheduledQueries().size());
        assertEquals("aws-timestream:test:scheduled-query:arn", resultGet.scheduledQueries().get(0).arn());
    }

    @Test
    public void timestreamPrepareQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:prepareQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.prepareQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        PrepareQueryResponse resultGet = (PrepareQueryResponse) exchange.getIn().getBody();
        assertEquals("select * from test_db", resultGet.queryString());
    }

    @Test
    public void timestreamQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:query", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.query);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        QueryResponse resultGet = (QueryResponse) exchange.getIn().getBody();
        assertEquals("query-1", resultGet.queryId());
    }

    @Test
    public void timestreamCancelQueryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:cancelQuery", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Timestream2Constants.OPERATION, Timestream2Operations.cancelQuery);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CancelQueryResponse resultGet = (CancelQueryResponse) exchange.getIn().getBody();
        assertEquals("Query Cancelled", resultGet.cancellationMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:describeQueryEndpoints")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=describeEndpoints")
                        .to("mock:result");
                from("direct:describeQueryEndpointsPojo")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=describeEndpoints&pojoRequest=true")
                        .to("mock:result");
                from("direct:createScheduledQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=createScheduledQuery")
                        .to("mock:result");
                from("direct:deleteScheduledQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=deleteScheduledQuery")
                        .to("mock:result");
                from("direct:executeScheduledQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=executeScheduledQuery")
                        .to("mock:result");
                from("direct:updateScheduledQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=updateScheduledQuery")
                        .to("mock:result");
                from("direct:describeScheduledQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=describeScheduledQuery")
                        .to("mock:result");
                from("direct:listScheduledQueries")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=listScheduledQueries")
                        .to("mock:result");
                from("direct:prepareQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=prepareQuery")
                        .to("mock:result");
                from("direct:query")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=query")
                        .to("mock:result");
                from("direct:cancelQuery")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=cancelQuery")
                        .to("mock:result");

            }
        };
    }
}
