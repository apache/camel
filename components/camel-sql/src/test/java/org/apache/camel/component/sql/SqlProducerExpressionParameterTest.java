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
import org.apache.camel.EndpointInject;
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
 * @version
 */
public class SqlProducerExpressionParameterTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint result;

    @EndpointInject(uri = "mock:result-simple")
    MockEndpoint resultSimple;

    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase.sql").build();

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testNamedParameterFromExpression() throws Exception {
        result.expectedMessageCount(1);

        template.sendBodyAndProperty("direct:start", "This is a dummy body", "license", "ASF");

        result.assertIsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, result.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(2, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Camel", row.get("PROJECT"));
        row = assertIsInstanceOf(Map.class, received.get(1));
        assertEquals("AMQ", row.get("PROJECT"));
    }

    @Test
    public void testNamedParameterFromSimpleExpression() throws Exception {
        resultSimple.expectedMessageCount(1);

        template.sendBodyAndProperty("direct:start-simple", "This is a dummy body", "license", "XXX");

        resultSimple.assertIsSatisfied();

        List<?> received = assertIsInstanceOf(List.class, resultSimple.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(1, received.size());
        Map<?, ?> row = assertIsInstanceOf(Map.class, received.get(0));
        assertEquals("Linux", row.get("PROJECT"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:start").to("sql:select * from projects where license = :#${property.license} order by id").to("mock:result");

                from("direct:start-simple").to("sql:select * from projects where license = :#$simple{property.license} order by id").to("mock:result-simple");
            }
        };
    }
}
