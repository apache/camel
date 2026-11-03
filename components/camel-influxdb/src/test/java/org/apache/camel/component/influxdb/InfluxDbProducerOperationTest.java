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
import org.apache.camel.util.ObjectHelper;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operation is one of insert, query or ping, in any case, and an endpoint with another operation fails to start
 * instead of failing its first message.
 */
class InfluxDbProducerOperationTest extends AbstractInfluxDbTest {

    @Test
    void queryInUpperCaseRunsTheQuery() throws Exception {
        QueryResult queryResult = new QueryResult();
        queryResult.setResults(List.of(new QueryResult.Result()));
        when(mockedDbConnection.query(any(Query.class))).thenReturn(queryResult);
        addRouteTo("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=QUERY");

        Exchange exchange = template.send("direct:start",
                e -> e.getIn().setHeader(InfluxDbConstants.INFLUXDB_QUERY, "select * from cpu"));

        assertNull(exchange.getException());
        assertSame(queryResult, exchange.getMessage().getBody());
    }

    @Test
    void pingInMixedCasePings() throws Exception {
        Pong pong = new Pong();
        pong.setVersion("1.8.10");
        when(mockedDbConnection.ping()).thenReturn(pong);
        addRouteTo("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=Ping");

        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody("test"));

        assertNull(exchange.getException());
        assertSame(pong, exchange.getMessage().getBody());
    }

    @Test
    void insertInUpperCaseWritesThePoint() throws Exception {
        addRouteTo("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=INSERT");

        Point point = Point.measurement("cpu").addField("idle", 90L).build();

        Exchange exchange = template.send("direct:start", e -> e.getIn().setBody(point));

        assertNull(exchange.getException());
        verify(mockedDbConnection).write("myTestTimeSeries", "default", point);
    }

    @Test
    void unsupportedOperationFailsTheStartOfTheRoute() {
        Exception failure = assertThrows(Exception.class,
                () -> addRouteTo("influxdb:influxDbBean?databaseName={{influxdb.testDb}}&operation=delete"));

        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, failure);
        assertNotNull(cause, "IllegalArgumentException in the causes of " + failure);
        assertEquals("The operation delete is not supported: use one of insert, query or ping", cause.getMessage());
    }

    private void addRouteTo(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri);
            }
        });
    }
}
