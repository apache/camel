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

public class SqlConsumerMaxMessagesPerPollTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.DERBY)
            .addScript("sql/createAndPopulateDatabase4.sql")
            .build();

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void maxMessagesPerPoll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        assertBodyMapValue(1, "ID", exchanges.get(0));
        assertBodyMapValue("Camel", "PROJECT", exchanges.get(0));
        assertProperty(0, "CamelBatchIndex", exchanges.get(0));
        assertProperty(2, "CamelBatchSize", exchanges.get(0));
        assertProperty(Boolean.FALSE, "CamelBatchComplete", exchanges.get(0));

        assertBodyMapValue(2, "ID", exchanges.get(1));
        assertBodyMapValue("AMQ", "PROJECT", exchanges.get(1));
        assertProperty(1, "CamelBatchIndex", exchanges.get(1));
        assertProperty(2, "CamelBatchSize", exchanges.get(1));
        assertProperty(Boolean.TRUE, "CamelBatchComplete", exchanges.get(1)); // end of the first batch

        assertBodyMapValue(3, "ID", exchanges.get(2));
        assertBodyMapValue("Linux", "PROJECT", exchanges.get(2));
        assertProperty(0, "CamelBatchIndex", exchanges.get(2)); // the second batch
        assertProperty(1, "CamelBatchSize", exchanges.get(2)); // only one entry in this batch
        assertProperty(Boolean.TRUE, "CamelBatchComplete", exchanges.get(2)); // there are no more entries yet
    }

    private void assertProperty(Object value, String propertyName, Exchange exchange) {
        assertEquals(value, exchange.getProperty(propertyName));
    }

    private void assertBodyMapValue(Object value, String key, Exchange exchange) {
        assertEquals(value, exchange.getIn().getBody(Map.class).get(key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                getContext().setTracing(true);
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("sql:select * from projects where processed = false order by id?maxMessagesPerPoll=2&initialDelay=0&delay=50")
                    .to("mock:result")
                    .to("sql:update projects set processed = true where id = :#id");
            }
        };
    }
}
