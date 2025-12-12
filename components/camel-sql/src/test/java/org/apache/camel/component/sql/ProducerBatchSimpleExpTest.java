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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProducerBatchSimpleExpTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    public record MyData(int id, String project, String license) {
    }

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();

        jdbcTemplate = new JdbcTemplate(db);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void shouldExecuteBatch() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        List<MyData> data = new ArrayList<>();
        data.add(new MyData(4, "Donald", "DIS"));
        data.add(new MyData(5, "Goofy", "DIS"));
        template.requestBody("direct:query", data);

        MockEndpoint.assertIsSatisfied(context);

        assertEquals("Donald", jdbcTemplate.queryForObject("select project from projects where id = 4", String.class));
        assertEquals("Goofy", jdbcTemplate.queryForObject("select project from projects where id = 5", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:query")
                        .to("sql:insert into projects values " +
                            "(:#$simple{body.id},:#$simple{body.project},:#$simple{body.license})" +
                            "?batch=true")
                        .to("mock:query");
            }
        };
    }
}
