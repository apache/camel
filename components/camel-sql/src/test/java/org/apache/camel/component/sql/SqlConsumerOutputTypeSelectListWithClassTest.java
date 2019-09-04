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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 *
 */
public class SqlConsumerOutputTypeSelectListWithClassTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testOutputType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        assertTrue(exchanges.size() >= 3);

        ProjectModel row = assertIsInstanceOf(ProjectModel.class, exchanges.get(0).getIn().getBody());
        assertEquals(1, row.getId());
        assertEquals("Camel", row.getProject());
        assertEquals("ASF", row.getLicense());

        ProjectModel row2 = assertIsInstanceOf(ProjectModel.class, exchanges.get(1).getIn().getBody());
        assertEquals(2, row2.getId());
        assertEquals("AMQ", row2.getProject());
        assertEquals("ASF", row2.getLicense());

        ProjectModel row3 = assertIsInstanceOf(ProjectModel.class, exchanges.get(2).getIn().getBody());
        assertEquals(3, row3.getId());
        assertEquals("Linux", row3.getProject());
        assertEquals("XXX", row3.getLicense());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("sql:select * from projects order by id?outputType=SelectList&outputClass=org.apache.camel.component.sql.ProjectModel&initialDelay=0&delay=50")
                        .to("mock:result");
            }
        };
    }
}
