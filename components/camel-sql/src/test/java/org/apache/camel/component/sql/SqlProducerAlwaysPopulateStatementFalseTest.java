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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

public class SqlProducerAlwaysPopulateStatementFalseTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    
    @BindToRegistry("myStrategy")
    private SqlPrepareStatementStrategy strategy;
    private volatile boolean invoked;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        strategy = new DefaultSqlPrepareStatementStrategy() {
            @Override
            public void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
                invoked = true;
                super.populateStatement(ps, iterator, expectedParams);
            }
        };
        
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        
        db.shutdown();
    }

    @Test
    public void testAlwaysPopulateFalse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", null);

        mock.assertIsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));

        row = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("AMQ", row.get("PROJECT"));

        assertFalse("Should not populate", invoked);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start")
                    .to("sql:select * from projects where license = 'ASF' order by id?alwaysPopulateStatement=false&prepareStatementStrategy=#myStrategy&initialDelay=0&delay=50")
                    .to("mock:result");
            }
        };
    }
}
