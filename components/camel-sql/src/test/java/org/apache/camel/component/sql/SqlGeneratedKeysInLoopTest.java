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

public class SqlGeneratedKeysInLoopTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private Exchange[] results = new Exchange[2];

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
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);
                from("direct:batchinloop").loop(2)
                        .to("sql:insert into projects (project, license, description) values (#, #, #)?batch=true")
                        .process(out -> {
                            results[(Integer) out.getProperty(Exchange.LOOP_INDEX)] = out.copy();
                        })
                        .end();
            }
        };
    }

    @Test
    public void testRetrieveGeneratedKeysForBatchInLoop() throws Exception {
        // first we create our exchange using the endpoint
        Endpoint endpoint = context.getEndpoint("direct:batchinloop");

        Exchange exchange = endpoint.createExchange();
        List<Object[]> payload = new ArrayList<>(4);
        payload.add(new Object[] { "project 1", "ASF", "new project 1" });
        payload.add(new Object[] { "project 2", "ASF", "new project 2" });
        exchange.getIn().setBody(payload);
        exchange.getIn().setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);

        template.send(endpoint, exchange);
        checkResults();
    }

    private void checkResults() {
        for (int i = 0; i < results.length; i++) {
            Exchange out = results[i];
            int id = (Integer) out.getProperty(Exchange.LOOP_INDEX) * 2 + 3;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> generatedKeys
                    = out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_DATA, List.class);
            assertNotNull(generatedKeys,
                    "out body could not be converted to a List - was: " + out.getMessage().getBody());
            assertEquals(2, generatedKeys.size());
            for (Map<String, Object> row : generatedKeys) {
                assertEquals(id++, row.get("ID"), "auto increment value should be " + (id - 1));
            }
            assertEquals(2, out.getMessage().getHeader(SqlConstants.SQL_GENERATED_KEYS_ROW_COUNT),
                    "generated keys row count should be two");
        }
    }

}
