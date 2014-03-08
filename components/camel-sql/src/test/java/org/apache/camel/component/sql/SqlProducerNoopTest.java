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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlProducerNoopTest extends CamelTestSupport {

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
    public void testInsertNoop() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:insert");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SqlConstants.SQL_UPDATE_COUNT, 1);
        mock.message(0).body().isEqualTo("Hi there!");

        template.requestBody("direct:insert", "Hi there!");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testQueryNoop() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SqlConstants.SQL_ROW_COUNT, 3);
        mock.message(0).body().isEqualTo("Hi there!");

        template.requestBody("direct:query", "Hi there!");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUpdateNoop() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:update");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SqlConstants.SQL_UPDATE_COUNT, 1);
        mock.message(0).body().isEqualTo("Hi there!");

        template.requestBody("direct:update", "Hi there!");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDeleteNoop() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:delete");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SqlConstants.SQL_UPDATE_COUNT, 1);
        mock.message(0).body().isEqualTo("Hi there!");

        template.requestBody("direct:delete", "Hi there!");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:query").to("sql:select * from projects?noop=true").to("mock:query");
                from("direct:update").to("sql:update projects set license='MIT' where id=3?noop=true").to("mock:update");
                from("direct:insert").to("sql:insert into projects values (4, 'Zookeeper', 'ASF')?noop=true").to("mock:insert");
                from("direct:delete").to("sql:delete from projects where id=1?noop=true").to("mock:delete");
            }
        };
    }
}
