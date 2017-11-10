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

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 *
 */
public class SqlConsumerDeleteFailedTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        jdbcTemplate = new JdbcTemplate(db);

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testConsume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        assertEquals(2, exchanges.size());

        assertEquals(1, exchanges.get(0).getIn().getBody(Map.class).get("ID"));
        assertEquals("Camel", exchanges.get(0).getIn().getBody(Map.class).get("PROJECT"));
        assertEquals(3, exchanges.get(1).getIn().getBody(Map.class).get("ID"));
        assertEquals("Linux", exchanges.get(1).getIn().getBody(Map.class).get("PROJECT"));

        // give it a little tine to delete
        Thread.sleep(500);

        assertEquals("Should have deleted 2 rows", new Integer(1), jdbcTemplate.queryForObject("select count(*) from projects", Integer.class));
        assertEquals("Should be AMQ project that is BAD", "AMQ", jdbcTemplate.queryForObject("select PROJECT from projects where license = 'BAD'", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("sql:select * from projects where license <> 'BAD' order by id"
                        + "?consumer.initialDelay=0&consumer.delay=50"
                        + "&consumer.onConsume=delete from projects where id = :#id"
                        + "&consumer.onConsumeFailed=update projects set license = 'BAD' where id = :#id")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Object project = exchange.getIn().getBody(Map.class).get("PROJECT");
                            if ("AMQ".equals(project)) {
                                throw new IllegalArgumentException("Cannot handled AMQ");
                            }
                        }
                    })
                    .to("mock:result");
            }
        };
    }
}
