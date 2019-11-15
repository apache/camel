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
package org.apache.camel.component.sql.stored;

import java.util.HashMap;
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

public class ProducerUseMessageBodyForTemplateTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        db.shutdown();
    }

    @Test
    public void shouldUseMessageBodyAsQuery() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);


        Map<String, Object> batch1 = new HashMap<>();
        batch1.put("num1", 3);
        batch1.put("num2", 1);


        template.requestBodyAndHeader("direct:query", "SUBNUMBERS(INTEGER :#num1,INTEGER :#num2,OUT INTEGER resultofsum)", SqlStoredConstants.SQL_STORED_PARAMETERS, batch1);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);


        assertEquals(Integer.valueOf(2), exchange.getIn().getBody(Map.class).get("resultofsum"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:query").to("sql-stored:query?useMessageBodyForTemplate=true").to("mock:query");
            }
        };
    }
}
