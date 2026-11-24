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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.timestream.Timestream2Constants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.timestreamquery.model.ColumnInfo;
import software.amazon.awssdk.services.timestreamquery.model.Datum;
import software.amazon.awssdk.services.timestreamquery.model.QueryRequest;
import software.amazon.awssdk.services.timestreamquery.model.QueryResponse;
import software.amazon.awssdk.services.timestreamquery.model.Row;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The query operation reads the page of the CamelAwsTimestreamQueryNextToken header and sets the token of the next
 * page, or reads all the pages with queryAllPages.
 */
public class Timestream2QueryPagingTest extends CamelTestSupport {

    private final List<QueryRequest> requests = new ArrayList<>();

    /**
     * Three pages: page 1 has two rows, page 2 has none (the query still runs), page 3 has one row.
     */
    @BindToRegistry("awsTimestreamQueryClient")
    AmazonTimestreamQueryClientMock clientMock = new AmazonTimestreamQueryClientMock() {
        @Override
        public QueryResponse query(QueryRequest queryRequest) {
            requests.add(queryRequest);
            String token = queryRequest.nextToken();
            QueryResponse.Builder page = QueryResponse.builder().queryId("query-1");
            if (token == null) {
                return page.columnInfo(ColumnInfo.builder().name("measure").build())
                        .rows(row("a"), row("b")).nextToken("t2").build();
            } else if ("t2".equals(token)) {
                return page.rows(List.of()).nextToken("t3").build();
            } else {
                return page.columnInfo(ColumnInfo.builder().name("measure").build()).rows(row("c")).build();
            }
        }
    };

    private static Row row(String value) {
        return Row.builder().data(Datum.builder().scalarValue(value).build()).build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> rows(Exchange exchange) {
        return exchange.getVariable("rows", List.class);
    }

    private static List<String> values(QueryResponse response) {
        return response.rows().stream().map(r -> r.data().get(0).scalarValue()).toList();
    }

    @Test
    public void onePageGivesTheTokenOfTheNextPage() {
        Exchange exchange = template.request("direct:query",
                e -> e.getIn().setHeader(Timestream2Constants.QUERY_STRING, "SELECT * FROM db.t"));

        QueryResponse response = exchange.getMessage().getBody(QueryResponse.class);
        assertThat(values(response)).containsExactly("a", "b");
        assertThat(exchange.getMessage().getHeader(Timestream2Constants.QUERY_NEXT_TOKEN)).isEqualTo("t2");
    }

    @Test
    public void theTokenHeaderReadsThatPage() {
        Exchange exchange = template.request("direct:query", e -> {
            e.getIn().setHeader(Timestream2Constants.QUERY_STRING, "SELECT * FROM db.t");
            e.getIn().setHeader(Timestream2Constants.QUERY_NEXT_TOKEN, "t3");
        });

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).nextToken()).isEqualTo("t3");
        assertThat(values(exchange.getMessage().getBody(QueryResponse.class))).containsExactly("c");
        assertThat(exchange.getMessage().getHeaders()).doesNotContainKey(Timestream2Constants.QUERY_NEXT_TOKEN);
    }

    @Test
    public void aRouteCanLoopOverThePages() {
        Exchange exchange = template.request("direct:loop",
                e -> e.getIn().setHeader(Timestream2Constants.QUERY_STRING, "SELECT * FROM db.t"));

        assertThat(requests).extracting(QueryRequest::nextToken).containsExactly(null, "t2", "t3");
        assertThat(rows(exchange)).containsExactly("a", "b", "c");
    }

    @Test
    public void queryAllPagesGivesAllTheRows() {
        Exchange exchange = template.request("direct:all",
                e -> e.getIn().setHeader(Timestream2Constants.QUERY_STRING, "SELECT * FROM db.t"));

        QueryResponse response = exchange.getMessage().getBody(QueryResponse.class);
        assertThat(values(response)).containsExactly("a", "b", "c");
        assertThat(response.nextToken()).isNull();
        assertThat(response.columnInfo()).extracting(ColumnInfo::name).containsExactly("measure");
        assertThat(requests).extracting(QueryRequest::nextToken).containsExactly(null, "t2", "t3");
        assertThat(requests).extracting(QueryRequest::queryString).containsOnly("SELECT * FROM db.t");
        assertThat(exchange.getMessage().getHeaders()).doesNotContainKey(Timestream2Constants.QUERY_NEXT_TOKEN);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=query");

                // the predicate of loopDoWhile is checked before each iteration, also the first, so the loop runs
                // while there are more pages, which ends after the last page even when the result has no rows
                from("direct:loop")
                        .setVariable("rows", () -> new ArrayList<String>())
                        .setVariable("morePages", constant(true))
                        .loopDoWhile(variable("morePages"))
                            .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient"
                                + "&operation=query")
                            .process(exchange -> rows(exchange)
                                    .addAll(values(exchange.getIn().getBody(QueryResponse.class))))
                            .process(exchange -> exchange.setVariable("morePages",
                                    exchange.getIn().getHeader(Timestream2Constants.QUERY_NEXT_TOKEN) != null))
                        .end();

                from("direct:all")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=query"
                            + "&queryAllPages=true");
            }
        };
    }
}
