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
package org.apache.camel.component.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlGeneratedKeysTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // Only HSQLDB seem to handle:
        // - more than one generated column in row
        // - return all keys generated in batch insert
        db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.HSQL).addScript("sql/createAndPopulateDatabase3.sql").build();

        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        if (db != null) {
            db.shutdown();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);
                from("direct:insert").to("sql:insert into projects (project, license, description) values (#, #, #)");
                from("direct:batch").to("sql:insert into projects (project, license, description) values (#, #, #)?batch=true");
                from("direct:select").to("sql:select * from projects order by id asc");
                from("direct:insert2").to("sql:insert into developers (name, position) values (#, #)");
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRetrieveGeneratedKey() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:insert");

        Object body = new Object[] { "project x", "ASF", "new project" };
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        assertNotNull(out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA));
        assertSame(body, out.getMessage().getBody());

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to a List - was: "
                                     + out.getMessage().getBody());
        assertEquals(1, generatedKeys.get(0).size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(3, row.get("ID"), "auto increment value should be 3");

        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRetrieveGeneratedKeys() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:insert2");

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new Object[] { "Star Swirl", "Wizard" });
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_COLUMNS, new String[] { "ID1", "ID2" });
        exchange.getIn().setHeader("foo", "123");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        assertNotNull(out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA));
        assertEquals("123", out.getMessage().getHeader("foo"));

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to a List - was: " + out.getMessage().getBody());
        assertEquals(2, generatedKeys.get(0).size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(5, row.get("ID1"), "auto increment value of ID1 should be 5");
        assertEquals(6, row.get("ID2"), "auto increment value of ID2 should be 6");

        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRetrieveGeneratedKeysForBatch() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:batch");

        Exchange exchange = endpoint.createExchange();
        List<Object[]> payload = new ArrayList<>(4);
        payload.add(new Object[] { "project x", "ASF", "new project x" });
        payload.add(new Object[] { "project y", "ASF", "new project y" });
        payload.add(new Object[] { "project z", "ASF", "new project z" });
        payload.add(new Object[] { "project q", "ASF", "new project q" });
        exchange.getIn().setBody(payload);
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader("foo", "123");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        assertNotNull(out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA));
        assertEquals("123", out.getMessage().getHeader("foo"));

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to a List - was: " + out.getMessage().getBody());

        // it seems not to work with Derby...
        assertEquals(4, generatedKeys.size());

        int id = 3;
        for (Map<String, Object> row : generatedKeys) {
            assertEquals(id++, row.get("ID"), "auto increment value should be " + id);
        }

        assertEquals(4, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be four");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRetrieveGeneratedKeyWithStringGeneratedColumns() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:insert");

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new Object[] { "project x", "ASF", "new project" });
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_COLUMNS, new String[] { "ID" });
        exchange.getIn().setHeader("foo", "123");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage());
        assertNotNull(out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA));
        assertEquals("123", out.getMessage().getHeader("foo"));

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to a List - was: " + out.getMessage().getBody());
        assertEquals(1, generatedKeys.get(0).size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(3, row.get("ID"), "auto increment value should be 3");

        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRetrieveGeneratedKeyWithIntGeneratedColumns() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:insert");

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new Object[] { "project x", "ASF", "new project" });
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_COLUMNS, new int[] { 1 });
        exchange.getIn().setHeader("foo", "123");

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        // assertions of the response
        assertNotNull(out);
        assertNotNull(out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA));
        assertEquals("123", out.getMessage().getHeader("foo"));

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertNotNull(generatedKeys, "out body could not be converted to a List - was: " + out.getMessage().getBody());
        assertEquals(1, generatedKeys.get(0).size());

        Map<String, Object> row = generatedKeys.get(0);
        assertEquals(3, row.get("ID"), "auto increment value should be 3");

        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                "generated keys row count should be one");
    }

    @Test
    public void testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:insert");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body
        exchange.getIn().setBody(new Object[] { "project x", "ASF", "new project" });
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);

        // set wrong data type for generated columns
        exchange.getIn().setHeader(SqlConstants.SQL_GENERATED_COLUMNS, new Object[] {});

        // now we send the exchange to the endpoint, and receives the response from Camel
        template.send(endpoint, exchange);

        assertTrue(exchange.isFailed());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNoKeysForSelect() {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:select");

        Exchange exchange = endpoint.createExchange();
        // then we set the SQL on the in body
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);

        // now we send the exchange to the endpoint, and receives the response from Camel
        Exchange out = template.send(endpoint, exchange);

        List<Map<String, Object>> result = out.getMessage().getBody(List.class);
        assertEquals(3, result.size(), "We should get 3 projects");

        List<Map<String, Object>> generatedKeys = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
        assertEquals(0, generatedKeys.size(), "We should not get any keys");
        assertEquals(0, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT), "We should not get any keys");
    }
}
