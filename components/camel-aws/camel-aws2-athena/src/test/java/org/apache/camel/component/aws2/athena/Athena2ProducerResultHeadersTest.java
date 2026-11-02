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
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsRequest;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The output location and next token headers that an operation sets as its result are not taken as the input of a later
 * operation in the same route.
 */
public class Athena2ProducerResultHeadersTest extends CamelTestSupport {

    private static final String QUERY_1 = "11111111-1111-1111-1111-111111111111";
    private static final String QUERY_2 = "22222222-2222-2222-2222-222222222222";
    private static final String ATHENA = "aws2-athena://label?outputLocation=s3://bucket/path/&queryString=RAW(SELECT 1)";

    @BindToRegistry("amazonAthenaClient")
    private final RecordingAthenaClient client = new RecordingAthenaClient();

    @Test
    public void secondStartQueryExecutionUsesOutputLocationOptionNotResultFileOfFirst() {
        // the first query waits for its completion, and sets the output location header to its result file
        Exchange exchange = template.send("direct:startTwice", e -> {
        });

        assertThat(exchange.getException()).isNull();
        assertThat(client.startRequests).hasSize(2);
        assertThat(client.startRequests.get(0).resultConfiguration().outputLocation()).isEqualTo("s3://bucket/path/");
        assertThat(client.startRequests.get(1).resultConfiguration().outputLocation()).isEqualTo("s3://bucket/path/");
    }

    @Test
    public void startQueryExecutionAfterGetQueryExecutionUsesOutputLocationOption() {
        Exchange exchange = template.send("direct:getThenStart",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.startRequests).hasSize(1);
        assertThat(client.startRequests.get(0).resultConfiguration().outputLocation()).isEqualTo("s3://bucket/path/");
    }

    @Test
    public void startQueryExecutionAfterS3PointerUsesOutputLocationOption() {
        Exchange exchange = template.send("direct:s3PointerThenStart",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.startRequests).hasSize(1);
        assertThat(client.startRequests.get(0).resultConfiguration().outputLocation()).isEqualTo("s3://bucket/path/");
    }

    @Test
    public void outputLocationHeaderSetByRouteAfterAnOperationIsUsed() {
        Exchange exchange = template.send("direct:getThenSetHeaderThenStart",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.startRequests).hasSize(1);
        assertThat(client.startRequests.get(0).resultConfiguration().outputLocation()).isEqualTo("s3://other/location/");
    }

    @Test
    public void getQueryResultsDoesNotUseNextTokenOfListQueryExecutions() {
        Exchange exchange = template.send("direct:listThenResults",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.resultsRequests).hasSize(1);
        assertThat(client.resultsRequests.get(0).nextToken()).isNull();
    }

    @Test
    public void listQueryExecutionsDoesNotUseNextTokenOfGetQueryResults() {
        Exchange exchange = template.send("direct:resultsThenList",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.listRequests).hasSize(1);
        assertThat(client.listRequests.get(0).nextToken()).isNull();
    }

    @Test
    public void getQueryResultsDoesNotUseNextTokenOfAnotherQuery() {
        Exchange exchange = template.send("direct:resultsOfTwoQueries",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.resultsRequests).hasSize(2);
        assertThat(client.resultsRequests.get(1).queryExecutionId()).isEqualTo(QUERY_2);
        assertThat(client.resultsRequests.get(1).nextToken()).isNull();
    }

    @Test
    public void getQueryResultsPagesWithItsOwnNextToken() {
        Exchange exchange = template.send("direct:resultsTwice",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));

        assertThat(exchange.getException()).isNull();
        assertThat(client.resultsRequests).hasSize(2);
        assertThat(client.resultsRequests.get(0).nextToken()).isNull();
        assertThat(client.resultsRequests.get(1).nextToken()).isEqualTo("results-token-1");
    }

    @Test
    public void listQueryExecutionsPagesWithItsOwnNextToken() {
        Exchange exchange = template.send("direct:listTwice", e -> {
        });

        assertThat(exchange.getException()).isNull();
        assertThat(client.listRequests).hasSize(2);
        assertThat(client.listRequests.get(0).nextToken()).isNull();
        assertThat(client.listRequests.get(1).nextToken()).isEqualTo("list-token-1");
    }

    @Test
    public void queryExecutionWithoutResultConfiguration() {
        QueryExecution managed = QueryExecution.builder()
                .queryExecutionId(QUERY_1)
                .status(QueryExecutionStatus.builder().state(QueryExecutionState.SUCCEEDED).build())
                .build();
        client.setGetQueryExecutionResults(new LinkedList<>(List.of(managed, managed, managed)));

        Exchange get = template.send("aws2-athena://label?operation=getQueryExecution",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));
        assertThat(get.getException()).isNull();
        assertThat(get.getMessage().getHeader(Athena2Constants.OUTPUT_LOCATION)).isNull();

        Exchange pointer = template.send("aws2-athena://label?operation=getQueryResults&outputType=S3Pointer",
                e -> e.getIn().setHeader(Athena2Constants.QUERY_EXECUTION_ID, QUERY_1));
        assertThat(pointer.getException()).isNull();
        assertThat(pointer.getMessage().getHeader(Athena2Constants.OUTPUT_LOCATION)).isNull();
        assertThat(pointer.getMessage().getBody()).isNull();

        Exchange start = template.send(ATHENA + "&waitTimeout=60000&initialDelay=1&delay=1", e -> {
        });
        assertThat(start.getException()).isNull();
        assertThat(start.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE))
                .isEqualTo(QueryExecutionState.SUCCEEDED);
        assertThat(start.getMessage().getHeader(Athena2Constants.OUTPUT_LOCATION)).isNull();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startTwice")
                        .to(ATHENA + "&waitTimeout=60000&initialDelay=1&delay=1")
                        .to(ATHENA);

                from("direct:getThenStart")
                        .to("aws2-athena://label?operation=getQueryExecution")
                        .to(ATHENA);

                from("direct:s3PointerThenStart")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=S3Pointer")
                        .to(ATHENA);

                from("direct:getThenSetHeaderThenStart")
                        .to("aws2-athena://label?operation=getQueryExecution")
                        .setHeader(Athena2Constants.OUTPUT_LOCATION, constant("s3://other/location/"))
                        .to(ATHENA);

                from("direct:listThenResults")
                        .to("aws2-athena://label?operation=listQueryExecutions")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList");

                from("direct:resultsThenList")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList")
                        .to("aws2-athena://label?operation=listQueryExecutions");

                from("direct:resultsOfTwoQueries")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList")
                        .setHeader(Athena2Constants.QUERY_EXECUTION_ID, constant(QUERY_2))
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList");

                from("direct:resultsTwice")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList")
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList");

                from("direct:listTwice")
                        .to("aws2-athena://label?operation=listQueryExecutions")
                        .to("aws2-athena://label?operation=listQueryExecutions");
            }
        };
    }

    /**
     * Records the requests, and answers getQueryResults and listQueryExecutions with a next token.
     */
    private static final class RecordingAthenaClient extends AmazonAthenaClientMock {

        private final List<StartQueryExecutionRequest> startRequests = new ArrayList<>();
        private final List<GetQueryResultsRequest> resultsRequests = new ArrayList<>();
        private final List<ListQueryExecutionsRequest> listRequests = new ArrayList<>();

        @Override
        public StartQueryExecutionResponse startQueryExecution(StartQueryExecutionRequest request) throws SdkException {
            startRequests.add(request);
            return super.startQueryExecution(request);
        }

        @Override
        public GetQueryResultsResponse getQueryResults(GetQueryResultsRequest request) throws SdkException {
            resultsRequests.add(request);
            return super.getQueryResults(request).toBuilder()
                    .nextToken("results-token-" + resultsRequests.size())
                    .build();
        }

        @Override
        public ListQueryExecutionsResponse listQueryExecutions(ListQueryExecutionsRequest request) throws SdkException {
            listRequests.add(request);
            return super.listQueryExecutions(request).toBuilder()
                    .nextToken("list-token-" + listRequests.size())
                    .build();
        }
    }
}
