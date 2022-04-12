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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class InfluxDbEnsureDatabaseExistsTest extends AbstractInfluxDbTest {

    private static final String DB_NAME = "dbName";

    @EndpointInject("mock:test")
    MockEndpoint successEndpoint;

    @EndpointInject("mock:error")
    MockEndpoint errorEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(0));

                //test route
                from("direct:test")
                        .to("influxdb:influxDbBean?databaseName=dbName&checkDatabaseExistence=true&autoCreateDatabase=true")
                        .to("mock:test");
            }
        };
    }

    @BeforeEach
    public void resetEndpoints() {
        errorEndpoint.reset();
        successEndpoint.reset();
    }

    @Test
    public void testWithExistingDB() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        QueryResult queryResult = new QueryResult();
        queryResult.setResults(null);
        QueryResult.Result result = new QueryResult.Result();
        QueryResult.Series serie = new QueryResult.Series();
        serie.setValues(Collections.singletonList(Collections.singletonList(DB_NAME)));
        result.setSeries(Collections.singletonList(serie));
        queryResult.setResults(Collections.singletonList(result));

        Mockito.when(mockedDbConnection.query(Mockito.any(Query.class))).thenReturn(queryResult);

        sendBody("direct:test", createPoint());

        //if db exist, there will be only 1 call to show databases.
        Mockito.verify(mockedDbConnection, Mockito.times(2)).query(Mockito.any(Query.class));

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @Test
    public void testWithNonExistingDB() throws InterruptedException {

        errorEndpoint.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        QueryResult queryResult = new QueryResult();
        queryResult.setResults(Collections.singletonList(new QueryResult.Result()));

        sendBody("direct:test", createPoint());

        //if db does not exist, there will be 2 queries (show databases and create database)
        Mockito.verify(mockedDbConnection, Mockito.times(2)).query(Mockito.any(Query.class));

        errorEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    private Point createPoint() {
        return Point.measurement("cpu")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("idle", 90L)
                .addField("user", 9L)
                .addField("system", 1L)
                .build();
    }

}
