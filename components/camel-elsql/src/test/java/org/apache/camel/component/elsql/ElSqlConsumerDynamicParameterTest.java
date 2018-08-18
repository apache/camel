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
package org.apache.camel.component.elsql;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 *
 */
public class ElSqlConsumerDynamicParameterTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private MyIdGenerator idGenerator = new MyIdGenerator();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myIdGenerator", idGenerator);

        // this is the database we create with some initial data for our unit test
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        jndi.bind("dataSource", db);

        return jndi;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testDynamicConsume() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();

        assertEquals(1, exchanges.get(0).getIn().getBody(Map.class).get("ID"));
        assertEquals("Camel", exchanges.get(0).getIn().getBody(Map.class).get("PROJECT"));
        assertEquals(2, exchanges.get(1).getIn().getBody(Map.class).get("ID"));
        assertEquals("AMQ", exchanges.get(1).getIn().getBody(Map.class).get("PROJECT"));
        assertEquals(3, exchanges.get(2).getIn().getBody(Map.class).get("ID"));
        assertEquals("Linux", exchanges.get(2).getIn().getBody(Map.class).get("PROJECT"));

        // and the bean id should be > 1
        assertTrue("Id counter should be > 1", idGenerator.getId() > 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("elsql:projectsByIdBean:elsql/projects.elsql?dataSource=#dataSource&consumer.initialDelay=0&consumer.delay=50")
                    .routeId("foo").noAutoStartup()
                    .to("mock:result");
            }
        };
    }

    public static class MyIdGenerator {

        private int id = 1;

        public int nextId() {
            // spring will call this twice, one for initializing query and 2nd for actual value
            id++;
            return id / 2;
        }

        public int getId() {
            return id;
        }
    }
}
