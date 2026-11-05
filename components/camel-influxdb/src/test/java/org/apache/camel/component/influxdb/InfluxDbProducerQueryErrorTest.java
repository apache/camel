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
package org.apache.camel.component.influxdb;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * InfluxDB answers a query it cannot run, such as one on a database that does not exist, with the error in the result
 * instead of an error status, and the client returns that result: such an error must fail the exchange.
 */
class InfluxDbProducerQueryErrorTest extends AbstractInfluxDbTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query")
                        .to("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=query")
                        .to("mock:result");
            }
        };
    }

    @Test
    void errorOfTheStatementFailsTheExchange() throws Exception {
        // the answer of InfluxDB to a query on a database that does not exist
        answer(queryResult(null, result("database not found: myTestTimeSeries")));
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = query();

        CamelInfluxDbException failure = assertInstanceOf(CamelInfluxDbException.class, exchange.getException(),
                "exception of an exchange whose query failed");
        assertEquals("The query failed in InfluxDB: database not found: myTestTimeSeries", failure.getMessage());
        result.assertIsSatisfied();
    }

    @Test
    void errorOfTheQueryFailsTheExchange() throws Exception {
        answer(queryResult("query interrupted"));
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = query();

        CamelInfluxDbException failure = assertInstanceOf(CamelInfluxDbException.class, exchange.getException(),
                "exception of an exchange whose query failed");
        assertEquals("The query failed in InfluxDB: query interrupted", failure.getMessage());
        result.assertIsSatisfied();
    }

    @Test
    void errorOfTheSecondStatementFailsTheExchange() throws Exception {
        answer(queryResult(null, result(null), result("measurement not found")));
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = query();

        CamelInfluxDbException failure = assertInstanceOf(CamelInfluxDbException.class, exchange.getException(),
                "exception of an exchange whose second statement failed");
        assertEquals("The query failed in InfluxDB: measurement not found", failure.getMessage());
        result.assertIsSatisfied();
    }

    @Test
    void resultWithoutErrorIsTheBody() throws Exception {
        QueryResult answer = queryResult(null, result(null));
        answer(answer);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange exchange = query();

        assertNull(exchange.getException());
        assertSame(answer, exchange.getMessage().getBody());
        result.assertIsSatisfied();
    }

    private Exchange query() {
        return template.send("direct:query",
                e -> e.getIn().setHeader(InfluxDbConstants.INFLUXDB_QUERY, "select * from cpu"));
    }

    private void answer(QueryResult queryResult) {
        when(mockedDbConnection.query(any(Query.class))).thenReturn(queryResult);
    }

    private static QueryResult queryResult(String error, QueryResult.Result... results) {
        QueryResult queryResult = new QueryResult();
        queryResult.setError(error);
        queryResult.setResults(List.of(results));
        return queryResult;
    }

    private static QueryResult.Result result(String error) {
        QueryResult.Result result = new QueryResult.Result();
        result.setError(error);
        if (error == null) {
            QueryResult.Series series = new QueryResult.Series();
            series.setName("cpu");
            series.setColumns(List.of("time", "idle"));
            series.setValues(List.of(List.of("2026-11-03T00:00:00Z", 90)));
            result.setSeries(List.of(series));
        }
        return result;
    }
}
