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
package org.apache.camel.component.aws2.athena.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.athena.Athena2Constants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.InvalidRequestException;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.access.key and -Daws.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class AthenaComponentManualIT extends CamelTestSupport {

    private final String s3Bucket = "s3://your-s3-bucket/" + UUID.randomUUID().toString() + "/";

    @Test
    public void athenaGetQueryExecutionTest() {
        Exchange exchange = template.send("direct:athenaGetQueryExecutionTest", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
        assertNotNull(exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        GetQueryExecutionResponse response = exchange.getMessage().getBody(GetQueryExecutionResponse.class);
        assertEquals("SELECT 1", response.queryExecution().query());
    }

    @Test
    public void athenaGetQueryResultsAsStreamListTest() {
        Exchange exchange
                = template.send("direct:athenaGetQueryResultsAsStreamListTest", ExchangePattern.InOut, new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertValidQueryExecutionIdHeader(exchange);

        GetQueryResultsIterable resultsIterable = exchange.getMessage().getBody(GetQueryResultsIterable.class);
        List<GetQueryResultsResponse> responses = new ArrayList<>();
        resultsIterable.forEach(responses::add);
        assertEquals(1, responses.size());

        ResultSet resultSet = responses.get(0).resultSet();

        assertEquals("id", resultSet.resultSetMetadata().columnInfo().get(0).name());
        assertEquals("name", resultSet.resultSetMetadata().columnInfo().get(1).name());

        assertEquals(3, resultSet.rows().size());

        assertEquals("id", resultSet.rows().get(0).data().get(0).varCharValue());
        assertEquals("1", resultSet.rows().get(1).data().get(0).varCharValue());
        assertEquals("2", resultSet.rows().get(2).data().get(0).varCharValue());

        assertEquals("name", resultSet.rows().get(0).data().get(1).varCharValue());
        assertEquals("a", resultSet.rows().get(1).data().get(1).varCharValue());
        assertEquals("b", resultSet.rows().get(2).data().get(1).varCharValue());
    }

    @Test
    public void athenaGetQueryResultsAsSelectListTest() {
        Exchange exchange
                = template.send("direct:athenaGetQueryResultsAsSelectListTest", ExchangePattern.InOut, new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertValidQueryExecutionIdHeader(exchange);

        GetQueryResultsResponse response = exchange.getMessage().getBody(GetQueryResultsResponse.class);
        ResultSet resultSet = response.resultSet();

        assertEquals("id", resultSet.resultSetMetadata().columnInfo().get(0).name());
        assertEquals("name", resultSet.resultSetMetadata().columnInfo().get(1).name());

        assertEquals(3, resultSet.rows().size());

        assertEquals("id", resultSet.rows().get(0).data().get(0).varCharValue());
        assertEquals("1", resultSet.rows().get(1).data().get(0).varCharValue());
        assertEquals("2", resultSet.rows().get(2).data().get(0).varCharValue());

        assertEquals("name", resultSet.rows().get(0).data().get(1).varCharValue());
        assertEquals("a", resultSet.rows().get(1).data().get(1).varCharValue());
        assertEquals("b", resultSet.rows().get(2).data().get(1).varCharValue());
    }

    @Test
    public void athenaGetQueryResultsAsS3PointerTest() {
        Exchange exchange
                = template.send("direct:athenaGetQueryResultsAsS3PointerTest", ExchangePattern.InOut, new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
        assertEquals(QueryExecutionState.SUCCEEDED,
                exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        String response = exchange.getMessage().getBody(String.class);
        assertTrue(response.startsWith(s3Bucket));
    }

    @Test
    public void athenaListQueryExecutionsTest() {
        Exchange exchange = template.send("direct:athenaListQueryExecutionsTest", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        ListQueryExecutionsResponse response = exchange.getMessage().getBody(ListQueryExecutionsResponse.class);
        assertFalse(response.queryExecutionIds().isEmpty());
    }

    @Test
    public void athenaListQueryExecutionsSetsNextTokenTest() {
        Exchange exchange
                = template.send("direct:athenaListQueryExecutionsSetsNextTokenTest", ExchangePattern.InOut, new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertNotNull(exchange.getMessage().getHeader(Athena2Constants.NEXT_TOKEN));

        ListQueryExecutionsResponse response = exchange.getMessage().getBody(ListQueryExecutionsResponse.class);
        assertFalse(response.queryExecutionIds().isEmpty());
    }

    @Test
    public void athenaStartQueryExecutionIncludesTraceInQuery() {
        Exchange exchange = template
                .send("direct:athenaStartQueryExecutionIncludesTraceInQuery", ExchangePattern.InOut, new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        GetQueryExecutionResponse response = exchange.getMessage().getBody(GetQueryExecutionResponse.class);
        assertTrue(response.queryExecution().query().startsWith(
                "-- {\"fromEndpointUri\": \"direct://athenaStartQueryExecutionIncludesTraceInQuery\", \"exchangeId\": "));
        assertTrue(response.queryExecution().query().endsWith("\nSELECT 1"));
    }

    @Test
    public void athenaStartQueryExecutionWaitForQueryCompletionTest() {
        Exchange exchange = template.send("direct:athenaStartQueryExecutionWaitForQueryCompletionTest", ExchangePattern.InOut,
                new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
        assertEquals(QueryExecutionState.SUCCEEDED,
                exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertTrue(exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class) > 0);
        assertTrue(exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Long.class) > 0);

        GetQueryExecutionResponse response = exchange.getMessage().getBody(GetQueryExecutionResponse.class);
        assertEquals("SELECT 1", response.queryExecution().query());
    }

    @Test
    public void athenaStartQueryExecutionByDefaultDoesNotWaitForCompletionTest() {
        Exchange exchange
                = template.send("direct:athenaStartQueryExecutionByDefaultDoesNotWaitForCompletionTest", ExchangePattern.InOut,
                        new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                            }
                        });

        assertValidQueryExecutionIdHeader(exchange);
        assertNull(exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE));
        assertNull(exchange.getMessage().getHeader(Athena2Constants.OUTPUT_LOCATION, String.class));

        assertTrue(exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class) > 0);
        assertTrue(exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Long.class) > 0);

        assertNull(exchange.getMessage().getBody());
    }

    @Test
    public void athenaStartQueryExecutionHandlesInvalidSqlTest() {
        Exchange exchange = template.send("direct:athenaStartQueryExecutionHandlesInvalidSqlTest", ExchangePattern.InOut,
                new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
        assertEquals(QueryExecutionState.FAILED,
                exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        assertEquals(3, exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ATTEMPTS, Integer.class));
        assertTrue(exchange.getMessage().getHeader(Athena2Constants.START_QUERY_EXECUTION_ELAPSED_MILLIS, Long.class) > 0);

        GetQueryExecutionResponse response = exchange.getMessage().getBody(GetQueryExecutionResponse.class);
        assertEquals(QueryExecutionState.FAILED, response.queryExecution().status().state());
        assertEquals("SELECT INVALID SQL", response.queryExecution().query());
    }

    @Test
    public void athenaStartQueryExecutionHandlesMalformedSqlTest() {
        Exchange exchange = template.send("direct:athenaStartQueryExecutionHandlesMalformedSqlTest", ExchangePattern.InOut,
                new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                    }
                });

        assertTrue(exchange.isFailed());

        InvalidRequestException exception = exchange.getException(InvalidRequestException.class);
        assertEquals("MALFORMED_QUERY", exception.athenaErrorCode());
    }

    @Test
    public void athenaStartQueryExecutionTest() {
        Exchange exchange = template.send("direct:athenaStartQueryExecutionTest", ExchangePattern.InOut, new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        assertValidQueryExecutionIdHeader(exchange);
    }

    @Test
    public void athenaStartQueryExecutionAndGetQueryExecutionUsingHeaders() {
        Exchange exchange
                = template.send("direct:athenaStartQueryExecutionAndGetQueryExecutionUsingHeaders", ExchangePattern.InOut,
                        new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                            }
                        });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
    }

    @Test
    public void athenaStartQueryExecutionAndGetQueryResultsByWaitingWithALoop() {
        Exchange exchange
                = template.send("direct:athenaStartQueryExecutionAndGetQueryResultsByWaitingWithALoop", ExchangePattern.InOut,
                        new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                            }
                        });

        assertValidQueryExecutionIdHeader(exchange);
        assertValidOutputLocationHeader(exchange);
        assertEquals(QueryExecutionState.SUCCEEDED,
                exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_STATE, QueryExecutionState.class));

        String response = exchange.getMessage().getBody(String.class);
        assertTrue(response.startsWith(s3Bucket));
    }

    private void assertValidQueryExecutionIdHeader(Exchange exchange) {
        UUID queryExecutionId
                = UUID.fromString(exchange.getMessage().getHeader(Athena2Constants.QUERY_EXECUTION_ID, String.class));
        assertNotNull(queryExecutionId);
    }

    private void assertValidOutputLocationHeader(Exchange exchange) {
        String outputLocation = exchange.getMessage().getHeader(Athena2Constants.OUTPUT_LOCATION, String.class);
        assertTrue(outputLocation.startsWith(s3Bucket));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsCreds = "&accessKey={{aws.access.key}}&secretKey={{aws.secret.key}}&region=eu-west-1";

                from("direct:athenaGetQueryExecutionTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryExecution&" + awsCreds);

                from("direct:athenaGetQueryResultsAsStreamListTest")
                        .setBody(constant(
                                "SELECT * FROM ("
                                          + "    VALUES"
                                          + "        (1, 'a'),"
                                          + "        (2, 'b')"
                                          + ") AS t (id, name)"))
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&outputLocation=" + s3Bucket
                            + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryResults&outputType=StreamList&" + awsCreds);

                from("direct:athenaGetQueryResultsAsSelectListTest")
                        .setBody(constant(
                                "SELECT * FROM ("
                                          + "    VALUES"
                                          + "        (1, 'a'),"
                                          + "        (2, 'b')"
                                          + ") AS t (id, name)"))
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&outputLocation=" + s3Bucket
                            + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryResults&outputType=SelectList&" + awsCreds);

                from("direct:athenaGetQueryResultsAsS3PointerTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&outputLocation=" + s3Bucket
                            + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryResults&outputType=S3Pointer&" + awsCreds);

                from("direct:athenaListQueryExecutionsTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds)
                        .to("aws2-athena://label?operation=listQueryExecutions&" + awsCreds);

                from("direct:athenaListQueryExecutionsSetsNextTokenTest")
                        .loop(2)
                            .setBody(constant("SELECT 1"))
                            .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds)
                        .end()
                        .to("aws2-athena://label?operation=listQueryExecutions&maxResults=1&" + awsCreds);

                from("direct:athenaStartQueryExecutionIncludesTraceInQuery")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&includeTrace=true&outputLocation=" + s3Bucket
                            + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryExecution&" + awsCreds);

                from("direct:athenaStartQueryExecutionWaitForQueryCompletionTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&outputLocation=" + s3Bucket
                            + "&" + awsCreds);

                from("direct:athenaStartQueryExecutionByDefaultDoesNotWaitForCompletionTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds);

                from("direct:athenaStartQueryExecutionHandlesInvalidSqlTest")
                        .setBody(constant("SELECT INVALID SQL")) // parses, but query fails to complete
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&maxAttempts=3&retry=always&outputLocation="
                            + s3Bucket + "&" + awsCreds);

                from("direct:athenaStartQueryExecutionHandlesMalformedSqlTest")
                        .setBody(constant("MALFORMED SQL")) // unparseable by athena
                        .to("aws2-athena://label?operation=startQueryExecution&waitTimeout=60000&maxAttempts=3&outputLocation="
                            + s3Bucket + "&" + awsCreds);

                from("direct:athenaStartQueryExecutionTest")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds);

                from("direct:athenaStartQueryExecutionAndGetQueryExecutionUsingHeaders")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds)
                        .to("aws2-athena://label?operation=getQueryExecution&" + awsCreds);

                from("direct:athenaStartQueryExecutionAndGetQueryResultsByWaitingWithALoop")
                        .setBody(constant("SELECT 1"))
                        .to("aws2-athena://label?operation=startQueryExecution&outputLocation=" + s3Bucket + "&" + awsCreds)
                        .loopDoWhile(simple("${header." + Athena2Constants.QUERY_EXECUTION_STATE + "} != 'SUCCEEDED'"))
                            .delay(1_000)
                            .to("aws2-athena://label?operation=getQueryExecution&" + awsCreds)
                        .end()
                        .to("aws2-athena://label?operation=getQueryResults&outputType=S3Pointer&" + awsCreds);
            }
        };
    }
}
