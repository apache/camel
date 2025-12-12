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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlProducerFetchSizeTest extends CamelTestSupport {

    static EmbeddedDatabase db;

    @BeforeAll
    public static void setupDatabase() {
        db = new EmbeddedDatabaseBuilder()
                .setName(SqlProducerFetchSizeTest.class.getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();
    }

    @AfterAll
    public static void cleanupDatabase() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testFetchSize() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        template.requestBody("direct:query", new String[] { "Camel", "AMQ" });

        MockEndpoint.assertIsSatisfied(context);

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, list.size());
        Map row = (Map) list.get(0);
        assertEquals("Camel", row.get("PROJECT"));
        row = (Map) list.get(1);
        assertEquals("AMQ", row.get("PROJECT"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // required for the sql component
                SqlComponent sql = getContext().getComponent("sql", SqlComponent.class);
                sql.setDataSource(db);
                sql.setFetchSize(10);

                from("direct:query")
                        .to("sql:classpath:sql/selectProjectsInBody.sql")
                        .to("log:query")
                        .to("mock:query");
            }
        };
    }
}
