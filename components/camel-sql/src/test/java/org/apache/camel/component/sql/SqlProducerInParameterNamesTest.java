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

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each :#in: parameter is expanded to the number of its own values, also when its name is the start of the name of a
 * later :#in: parameter, or when it is an expression with characters that are special in a regular expression.
 */
class SqlProducerInParameterNamesTest extends CamelTestSupport {

    EmbeddedDatabase db;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase6.sql").build();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    void testNameIsStartOfLaterName() {
        Map<String, Object> headers = Map.of("project", List.of("Camel", "AMQ"), "projectLicense", List.of("ASF", "XXX"));
        List<?> rows = template.requestBodyAndHeaders("direct:prefix", null, headers, List.class);
        assertEquals(4, rows.size());
    }

    @Test
    void testExpressionWithBrackets() {
        List<?> rows = template.requestBody("direct:expression", Map.of("names", List.of("Camel", "AMQ")), List.class);
        assertEquals(8, rows.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:prefix")
                        .to("sql:select * from projects where project in (:#in:project)"
                            + " and license in (:#in:projectLicense) order by id");
                from("direct:expression")
                        .to("sql:select * from projects where project in (:#in:${body[names]}) order by id");
            }
        };
    }
}
