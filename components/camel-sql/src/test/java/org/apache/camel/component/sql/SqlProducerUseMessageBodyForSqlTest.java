/**
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlProducerUseMessageBodyForSqlTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testUseMessageBodyForSqlAndHeaderParams() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start").routeId("foo")
                        .setBody(constant("select * from projects where license = :?lic order by id"))
                        .to("sql://query?useMessageBodyForSql=true")
                        .to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", null, "lic", "ASF");

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));

        row = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("AMQ", row.get("PROJECT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUseMessageBodyForSqlAndCamelSqlParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start").routeId("bar")
                        .setBody(constant("select * from projects where license = :?lic order by id"))
                        .to("sql://query?useMessageBodyForSql=true")
                        .to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Map<String, Object> row = new HashMap<String, Object>();
        row.put("lic", "ASF");
        template.sendBodyAndHeader("direct:start", null, SqlConstants.SQL_PARAMETERS, row);

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));

        row = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("AMQ", row.get("PROJECT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUseMessageBodyForSqlAndCamelSqlParametersBatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start").routeId("baz")
                        .setBody(constant("insert into projects(id, project, license) values(:?id,:?project,:?lic)"))
                        .to("sql://query?useMessageBodyForSql=true&batch=true")
                        .to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", 200);
        row.put("project", "MyProject1");
        row.put("lic", "OPEN1");
        rows.add(row);
        row = new HashMap<String, Object>();
        row.put("id", 201);
        row.put("project", "MyProject2");
        row.put("lic", "OPEN1");
        rows.add(row);
        template.sendBodyAndHeader("direct:start", null, SqlConstants.SQL_PARAMETERS, rows);

        String origSql = assertIsInstanceOf(String.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("insert into projects(id, project, license) values(:?id,:?project,:?lic)", origSql);

        assertEquals(null, mock.getReceivedExchanges().get(0).getOut().getBody());

        // Clear and then use route2 to verify result of above insert select
        context.removeRoute(context.getRoutes().get(0).getId());
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start2")
                        .setBody(constant("select * from projects where license = :?lic order by id"))
                        .to("sql://query2?useMessageBodyForSql=true")
                        .to("mock:result2");
            }
        });

        mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start2", null, "lic", "OPEN1");

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("MyProject1", row.get("PROJECT"));

        row = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("MyProject2", row.get("PROJECT"));
    }
}
