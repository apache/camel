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

import java.util.Arrays;

import org.apache.camel.builder.AggregationStrategies;
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

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlAggregateBatchTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", Arrays.asList(6, "abc", "def"));
        template.sendBody("direct:start", Arrays.asList(7, "ghi", "jkl"));
        template.sendBody("direct:start", Arrays.asList(8, "mno", "pqr"));

        mock.assertIsSatisfied();

        Number received = assertIsInstanceOf(Number.class,
                mock.getReceivedExchanges().get(0).getIn().getHeader(SqlConstants.SQL_UPDATE_COUNT));

        assertEquals(3, received.intValue());

        assertEquals("abc", jdbcTemplate.queryForObject("select project from projects where id = 6", String.class));
        assertEquals("def", jdbcTemplate.queryForObject("select license from projects where id = 6", String.class));
        assertEquals("ghi", jdbcTemplate.queryForObject("select project from projects where id = 7", String.class));
        assertEquals("jkl", jdbcTemplate.queryForObject("select license from projects where id = 7", String.class));
        assertEquals("mno", jdbcTemplate.queryForObject("select project from projects where id = 8", String.class));
        assertEquals("pqr", jdbcTemplate.queryForObject("select license from projects where id = 8", String.class));
    }

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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                errorHandler(noErrorHandler());

                from("direct:start")
                        .aggregate(constant(true)).completionSize(3).aggregationStrategy(AggregationStrategies.groupedBody())
                        .to("direct:batch");

                from("direct:batch")
                        .to("sql:insert into projects values (#, #, #)?batch=true")
                        .to("mock:result");
            }
        };
    }

}
