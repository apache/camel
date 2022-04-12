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
package org.apache.camel.component.jdbc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractJdbcGeneratedKeysTest extends AbstractJdbcTestSupport {

    @SuppressWarnings("unchecked")
    protected void testRetrieveGeneratedKeys(String query, Map<String, Object> parameters) {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body and add possible parameters
        exchange.getIn().setBody(query);
        exchange.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        setHeaders(exchange, parameters);

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA));
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT));

        List<Map<String, Object>> generatedKeys
                = out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to an ArrayList - was: " + out.getMessage().getBody());
        assertEquals(1, generatedKeys.size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(BigDecimal.valueOf(2), row.get("1"), "auto increment value should be 2");

        assertEquals(1, out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    protected void testRetrieveGeneratedKeys(String query) {
        testRetrieveGeneratedKeys(query, null);
    }

    @SuppressWarnings("unchecked")
    protected void testRetrieveGeneratedKeysWithStringGeneratedColumns(
            String query,
            Map<String, Object> parameters) {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body and add possible parameters
        exchange.getIn().setBody(query);
        exchange.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader(JdbcConstants.JDBC_GENERATED_COLUMNS, new String[] { "ID" });
        setHeaders(exchange, parameters);

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA));
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT));

        List<Map<String, Object>> generatedKeys
                = out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to an ArrayList - was: " + out.getMessage().getBody());
        assertEquals(1, generatedKeys.size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(BigDecimal.valueOf(2), row.get("1"), "auto increment value should be 2");

        assertEquals(1, out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    protected void testRetrieveGeneratedKeysWithStringGeneratedColumns(String query) {
        testRetrieveGeneratedKeysWithStringGeneratedColumns(query, null);
    }

    @SuppressWarnings("unchecked")
    protected void testRetrieveGeneratedKeysWithIntGeneratedColumns(
            String query,
            Map<String, Object> parameters) {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body and add possible parameters
        exchange.getIn().setBody(query);
        exchange.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader(JdbcConstants.JDBC_GENERATED_COLUMNS, new int[] { 1 });
        setHeaders(exchange, parameters);

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA));
        assertNotNull(out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT));

        List<Map<String, Object>> generatedKeys
                = out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to an ArrayList - was: " + out.getMessage().getBody());
        assertEquals(1, generatedKeys.size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(BigDecimal.valueOf(2), row.get("1"), "auto increment value should be 2");

        assertEquals(1, out.getMessage().getHeader(JdbcConstants.JDBC_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    protected void testRetrieveGeneratedKeysWithIntGeneratedColumns(String query) {
        testRetrieveGeneratedKeysWithIntGeneratedColumns(query, null);
    }

    protected void testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown(
            String query,
            Map<String, Object> parameters) {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:hello");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body and add possible parameters
        exchange.getIn().setBody(query);
        exchange.getIn().setHeader(JdbcConstants.JDBC_RETRIEVE_GENERATED_KEYS, true);
        setHeaders(exchange, parameters);

        // set wrong data type for generated columns
        exchange.getIn().setHeader(JdbcConstants.JDBC_GENERATED_COLUMNS, new Object[] {});

        // now we send the exchange to the endpoint, and receives the response from Camel
        template.send(endpoint, exchange);

        assertTrue(exchange.isFailed());
    }

    protected void testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown(String query) {
        testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown(query, null);
    }

    private void setHeaders(Exchange exchange, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                exchange.getIn().setHeader(parameter.getKey(), parameter.getValue());
            }
        }
    }

    @Override
    protected abstract RouteBuilder createRouteBuilder();

}
