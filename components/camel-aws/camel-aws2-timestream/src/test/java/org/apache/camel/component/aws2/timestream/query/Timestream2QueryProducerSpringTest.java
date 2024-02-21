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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.aws2.timestream.Timestream2Constants;
import org.apache.camel.component.aws2.timestream.Timestream2Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.timestreamquery.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Timestream2QueryProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void timestreamDescribeQueryEndpointTest() throws Exception {

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
    public void timestreamDescribeQueryEndpointPojoTest() throws Exception {

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
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/aws2/timestream/TimestreamComponentSpringTest-context.xml");
    }
}
