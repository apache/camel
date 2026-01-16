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
package org.apache.camel.component.cassandra.integration;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.CassandraEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the requestTimeout parameter in the Cassandra component.
 */
public class CassandraComponentRequestTimeoutIT extends BaseCassandra {

    static final String CQL_INSERT = "insert into camel_user(login, first_name, last_name) values (?, ?, ?)";
    static final String CQL_SELECT = "select login, first_name, last_name from camel_user";

    @Produce("direct:inputWithTimeout")
    ProducerTemplate producerWithTimeout;

    @Produce("direct:inputWithTimeoutUnprepared")
    ProducerTemplate producerWithTimeoutUnprepared;

    @Produce("direct:inputWithoutTimeout")
    ProducerTemplate producerWithoutTimeout;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Producer routes with different timeout configurations
                from("direct:inputWithTimeout")
                        .toF("cql://%s/%s?cql=%s&requestTimeout=30000", getUrl(), KEYSPACE_NAME, CQL_INSERT);

                from("direct:inputWithTimeoutUnprepared")
                        .toF("cql://%s/%s?cql=%s&requestTimeout=30000&prepareStatements=false", getUrl(), KEYSPACE_NAME,
                                CQL_INSERT);

                from("direct:inputWithoutTimeout")
                        .toF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL_INSERT);

                // Consumer routes with different timeout configurations
                fromF("cql://%s/%s?cql=%s&requestTimeout=30000", getUrl(), KEYSPACE_NAME, CQL_SELECT)
                        .to("mock:resultWithTimeout");

                fromF("cql://%s/%s?cql=%s&requestTimeout=30000&prepareStatements=false", getUrl(), KEYSPACE_NAME, CQL_SELECT)
                        .to("mock:resultWithTimeoutUnprepared");
            }
        };
    }

    /**
     * Test that the requestTimeout parameter is correctly set on the endpoint.
     */
    @Test
    public void testEndpointRequestTimeoutConfiguration() {
        CassandraEndpoint endpointWithTimeout = getMandatoryEndpoint(
                String.format("cql://%s/%s?cql=%s&requestTimeout=30000", getUrl(), KEYSPACE_NAME, CQL_INSERT),
                CassandraEndpoint.class);
        assertEquals(30000, endpointWithTimeout.getRequestTimeout());

        CassandraEndpoint endpointWithoutTimeout = getMandatoryEndpoint(
                String.format("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL_INSERT),
                CassandraEndpoint.class);
        assertEquals(0, endpointWithoutTimeout.getRequestTimeout());
    }

    /**
     * Test producer with requestTimeout using prepared statements.
     */
    @Test
    public void testProducerWithTimeoutPreparedStatements() {
        producerWithTimeout.requestBody(Arrays.asList("timeout_user1", "Timeout", "User1"));

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "timeout_user1");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Timeout", row.getString("first_name"));
        assertEquals("User1", row.getString("last_name"));
    }

    /**
     * Test producer with requestTimeout using unprepared statements.
     */
    @Test
    public void testProducerWithTimeoutUnpreparedStatements() {
        producerWithTimeoutUnprepared.requestBody(Arrays.asList("timeout_user2", "Timeout", "User2"));

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "timeout_user2");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("Timeout", row.getString("first_name"));
        assertEquals("User2", row.getString("last_name"));
    }

    /**
     * Test producer without requestTimeout (uses driver default).
     */
    @Test
    public void testProducerWithoutTimeout() {
        producerWithoutTimeout.requestBody(Arrays.asList("no_timeout_user", "NoTimeout", "User"));

        ResultSet resultSet = getSession()
                .execute("select login, first_name, last_name from camel_user where login = ?", "no_timeout_user");
        Row row = resultSet.one();
        assertNotNull(row);
        assertEquals("NoTimeout", row.getString("first_name"));
        assertEquals("User", row.getString("last_name"));
    }

    /**
     * Test consumer with requestTimeout using prepared statements.
     */
    @Test
    public void testConsumerWithTimeoutPreparedStatements() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultWithTimeout");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test consumer with requestTimeout using unprepared statements.
     */
    @Test
    public void testConsumerWithTimeoutUnpreparedStatements() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultWithTimeoutUnprepared");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test that different timeout values can be configured.
     */
    @Test
    public void testDifferentTimeoutValues() {
        // Test with 1 second timeout
        CassandraEndpoint endpoint1s = getMandatoryEndpoint(
                String.format("cql://%s/%s?cql=%s&requestTimeout=1000", getUrl(), KEYSPACE_NAME, CQL_SELECT),
                CassandraEndpoint.class);
        assertEquals(1000, endpoint1s.getRequestTimeout());

        // Test with 60 second timeout
        CassandraEndpoint endpoint60s = getMandatoryEndpoint(
                String.format("cql://%s/%s?cql=%s&requestTimeout=60000", getUrl(), KEYSPACE_NAME, CQL_SELECT),
                CassandraEndpoint.class);
        assertEquals(60000, endpoint60s.getRequestTimeout());

        // Test with 5 minute timeout
        CassandraEndpoint endpoint5m = getMandatoryEndpoint(
                String.format("cql://%s/%s?cql=%s&requestTimeout=300000", getUrl(), KEYSPACE_NAME, CQL_SELECT),
                CassandraEndpoint.class);
        assertEquals(300000, endpoint5m.getRequestTimeout());
    }
}
