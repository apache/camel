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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AthenaComponentTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("amazonAthenaClient")
    private AmazonAthenaClientMock client = new AmazonAthenaClientMock();

    @Test
    public void getQueryExecution() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:getQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, "11111111-1111-1111-1111-111111111111");
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals(QueryExecutionState.SUCCEEDED,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));

        GetQueryExecutionResponse result = message.getBody(GetQueryExecutionResponse.class);
        assertEquals("11111111-1111-1111-1111-111111111111", result.queryExecution().queryExecutionId());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void getQueryResultsForStreamList() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:getQueryResults", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, "11111111-1111-1111-1111-111111111111");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_TYPE, Athena2OutputType.StreamList);
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));

        GetQueryResultsIterable result = message.getBody(GetQueryResultsIterable.class);
        List<GetQueryResultsResponse> responses = new ArrayList<>();
        result.forEach(responses::add);
        assertEquals(1, responses.size());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void getQueryResultsForSelectList() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:getQueryResults", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, "11111111-1111-1111-1111-111111111111");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_TYPE, Athena2OutputType.SelectList);
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));

        GetQueryResultsResponse result = message.getBody(GetQueryResultsResponse.class);
        assertEquals(1, result.resultSet().rows().size());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void getQueryResultsForS3Pointer() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:getQueryResults", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, "11111111-1111-1111-1111-111111111111");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_TYPE, Athena2OutputType.S3Pointer);
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals(QueryExecutionState.SUCCEEDED,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));

        String result = message.getBody(String.class);
        assertEquals("s3://bucket/file.csv", result);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void listQueryExecutions() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:listQueryExecutions", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Athena2Constants.MAX_RESULTS, 42);
                exchange.getIn().setHeader(Athena2Constants.NEXT_TOKEN, "next-token");
            }
        }).getMessage();

        assertEquals("next-token", message.getHeader(Athena2Constants.NEXT_TOKEN, String.class));

        ListQueryExecutionsResponse result = message.getBody(ListQueryExecutionsResponse.class);
        assertEquals(Arrays.asList(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222"),
                result.queryExecutionIds());
        assertEquals("next-token", result.nextToken());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void startQueryExecution() throws Exception {
        result.expectedMessageCount(1);

        Message message = template.send("direct:startQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("SELECT 1");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_LOCATION, "s3://bucket/file.csv");
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals(1, message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Integer.class) >= 0);

        assertNull(message.getBody());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void startQueryExecutionAndWaitForQueryCompletion() {
        result.expectedMessageCount(1);

        Message message = template.send("direct:startQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("SELECT 1");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_LOCATION, "s3://bucket/file.csv");
                exchange.getIn().setHeader(Athena2Constants.WAIT_TIMEOUT, 60_000);
                exchange.getIn().setHeader(Athena2Constants.INITIAL_DELAY, 1);
            }
        }).getMessage();

        assertEquals("11111111-1111-1111-1111-111111111111",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));
        assertEquals(QueryExecutionState.SUCCEEDED,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertEquals(1, message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Integer.class) > 0);

        GetQueryExecutionResponse result = message.getBody(GetQueryExecutionResponse.class);
        assertEquals("11111111-1111-1111-1111-111111111111", result.queryExecution().queryExecutionId());
    }

    @Test
    public void startQueryExecutionAndWaitForQueryCompletionWithTransientErrors() {
        result.expectedMessageCount(1);

        // 1111... will be returned on the first call to startQueryExecution, 2222... on the second call
        client.setStartQueryExecutionResults(new LinkedList<>(
                Arrays.asList(
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222")));

        client.setGetQueryExecutionResults(new LinkedList<>(
                Arrays.asList(
                        QueryExecution.builder()
                                .queryExecutionId("11111111-1111-1111-1111-111111111111")
                                .status(QueryExecutionStatus.builder().state(QueryExecutionState.FAILED).build())
                                .resultConfiguration(
                                        ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                                .build(),
                        QueryExecution.builder()
                                .queryExecutionId("22222222-2222-2222-2222-222222222222")
                                .status(QueryExecutionStatus.builder().state(QueryExecutionState.SUCCEEDED).build())
                                .resultConfiguration(
                                        ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                                .build())));

        Message message = template.send("direct:startQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("SELECT 1");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_LOCATION, "s3://bucket/file.csv");
                exchange.getIn().setHeader(Athena2Constants.WAIT_TIMEOUT, 60_000);
                exchange.getIn().setHeader(Athena2Constants.INITIAL_DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.MAX_ATTEMPTS, 2);
                exchange.getIn().setHeader(Athena2Constants.RETRY, "always");
            }
        }).getMessage();

        assertEquals("22222222-2222-2222-2222-222222222222",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));
        assertEquals(QueryExecutionState.SUCCEEDED,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertEquals(2, message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Integer.class) > 0);

        GetQueryExecutionResponse result = message.getBody(GetQueryExecutionResponse.class);
        assertEquals("22222222-2222-2222-2222-222222222222", result.queryExecution().queryExecutionId());
    }

    @Test
    public void startQueryExecutionAndWaitForQueryCompletionWithUnrecoverableErrors() {
        result.expectedMessageCount(1);

        // 1111... will be returned on the first call to startQueryExecution, 2222... on the second call
        client.setStartQueryExecutionResults(new LinkedList<>(
                Arrays.asList(
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222")));

        client.setGetQueryExecutionResults(new LinkedList<>(
                Arrays.asList(
                        QueryExecution.builder()
                                .queryExecutionId("11111111-1111-1111-1111-111111111111")
                                .status(QueryExecutionStatus.builder().state(QueryExecutionState.FAILED).build())
                                .resultConfiguration(
                                        ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                                .build(),
                        QueryExecution.builder()
                                .queryExecutionId("22222222-2222-2222-2222-222222222222")
                                .status(QueryExecutionStatus.builder().state(QueryExecutionState.FAILED).build())
                                .resultConfiguration(
                                        ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                                .build())));

        Message message = template.send("direct:startQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("SELECT 1");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_LOCATION, "s3://bucket/file.csv");
                exchange.getIn().setHeader(Athena2Constants.WAIT_TIMEOUT, 60_000);
                exchange.getIn().setHeader(Athena2Constants.INITIAL_DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.MAX_ATTEMPTS, 2);
                exchange.getIn().setHeader(Athena2Constants.RETRY, "always");
            }
        }).getMessage();

        assertEquals("22222222-2222-2222-2222-222222222222",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));
        assertEquals(QueryExecutionState.FAILED,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertEquals(2, message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Integer.class) > 0);

        GetQueryExecutionResponse result = message.getBody(GetQueryExecutionResponse.class);
        assertEquals("22222222-2222-2222-2222-222222222222", result.queryExecution().queryExecutionId());
    }

    @Test
    public void startQueryExecutionAndWaitForQueryCompletionTimesOut() {
        result.expectedMessageCount(1);

        // 3333... will be returned on the first call to startQueryExecution
        client.setStartQueryExecutionResults(new LinkedList<>(
                Collections.singletonList(
                        "33333333-3333-3333-3333-333333333333")));

        client.setGetQueryExecutionResults(new LinkedList<>(
                Collections.singletonList(
                        QueryExecution.builder()
                                .queryExecutionId("33333333-3333-3333-3333-333333333333") // causes 500ms sleep
                                .status(QueryExecutionStatus.builder().state(QueryExecutionState.RUNNING).build()) // not complete
                                .resultConfiguration(
                                        ResultConfiguration.builder().outputLocation("s3://bucket/file.csv").build())
                                .build())));

        Message message = template.send("direct:startQueryExecution", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("SELECT 1");
                exchange.getIn().setHeader(Athena2Constants.OUTPUT_LOCATION, "s3://bucket/file.csv");
                exchange.getIn().setHeader(Athena2Constants.WAIT_TIMEOUT, 100); // insufficient for query execution time (500ms)
                exchange.getIn().setHeader(Athena2Constants.INITIAL_DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.DELAY, 1);
                exchange.getIn().setHeader(Athena2Constants.MAX_ATTEMPTS, 1);
                exchange.getIn().setHeader(Athena2Constants.RETRY, "always");
            }
        }).getMessage();

        assertEquals("33333333-3333-3333-3333-333333333333",
                message.getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertEquals("s3://bucket/file.csv", message.getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));
        assertEquals(QueryExecutionState.RUNNING,
                message.getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertEquals(1, message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(message.getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Integer.class) > 500);

        GetQueryExecutionResponse result = message.getBody(GetQueryExecutionResponse.class);
        assertEquals("33333333-3333-3333-3333-333333333333", result.queryExecution().queryExecutionId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getQueryExecution")
                        .to("aws2-athena://label?operation=getQueryExecution&accessKey=unused&secretKey=unused&region=eu-west-1")
                        .to("mock:result");

                from("direct:getQueryResults")
                        .to("aws2-athena://label?operation=getQueryResults&accessKey=unused&secretKey=unused&region=eu-west-1")
                        .to("mock:result");

                from("direct:listQueryExecutions")
                        .to("aws2-athena://label?operation=listQueryExecutions&accessKey=unused&secretKey=unused&region=eu-west-1")
                        .to("mock:result");

                from("direct:startQueryExecution")
                        .to("aws2-athena://label?accessKey=unused&secretKey=unused")
                        .to("mock:result");
            }
        };
    }
}
