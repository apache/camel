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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class SqlConsumerDeleteTransformTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();

        jdbcTemplate = new JdbcTemplate(db);

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

    @Test
    public void testConsume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("The project is Camel", "The project is AMQ", "The project is Linux");

        MockEndpoint.assertIsSatisfied(context);

        // some servers may be a bit slow for this
        for (int i = 0; i < 5; i++) {
            // give it a little time to delete
            Thread.sleep(200);
            int rows = jdbcTemplate.queryForObject("select count(*) from projects", Integer.class);
            if (rows == 0) {
                break;
            }
        }
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject("select count(*) from projects", Integer.class),
                "Should have deleted all 3 rows");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                // even if we transform the exchange we can still do onConsume as we have the original data at
                // the point when onConsume is executed
                from("sql:select * from projects order by id?initialDelay=0&delay=50&consumer.onConsume=delete from projects where id = :#id")
                        .transform().simple("The project is ${body[project]}")
                        .to("mock:result");
            }
        };
    }
}
