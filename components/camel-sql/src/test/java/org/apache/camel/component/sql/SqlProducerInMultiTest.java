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

import java.util.Arrays;
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

public class SqlProducerInMultiTest extends CamelTestSupport {

    EmbeddedDatabase db;

    @Override
    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/createAndPopulateDatabase6.sql").build();

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        db.shutdown();
    }

    @Test
    public void testQueryInArray() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("names", new String[]{"Camel", "AMQ"});
        headers.put("licenses", new String[]{"ASF", "XXX", "YYY"});
        template.requestBodyAndHeaders("direct:query", "Hi there!", headers);

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(6, list.size());
        Map row = (Map) list.get(0);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(1);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(2);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
        row = (Map) list.get(3);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(4);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(5);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
    }

    @Test
    public void testQueryInList() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("names", Arrays.asList("Camel", "AMQ"));
        headers.put("licenses", Arrays.asList("ASF", "XXX", "YYY"));

        template.requestBodyAndHeaders("direct:query", "Hi there!", headers);

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(6, list.size());
        Map row = (Map) list.get(0);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(1);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(2);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
        row = (Map) list.get(3);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(4);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(5);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
    }

    @Test
    public void testQueryInString() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        HashMap<String, Object> headers = new HashMap<>();
        headers.put("names", "Camel,AMQ");
        headers.put("licenses", "ASF,XXX,YYY");
        template.requestBodyAndHeaders("direct:query", "Hi there!", headers);

        assertMockEndpointsSatisfied();

        List list = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(6, list.size());
        Map row = (Map) list.get(0);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(1);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(2);
        assertEquals("Camel", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
        row = (Map) list.get(3);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("ASF", row.get("license"));
        row = (Map) list.get(4);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("XXX", row.get("license"));
        row = (Map) list.get(5);
        assertEquals("AMQ", row.get("PROJECT"));
        assertEquals("YYY", row.get("license"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:query")
                    .to("sql:classpath:sql/selectProjectsInMulti.sql")
                    .to("log:query")
                    .to("mock:query");
            }
        };
    }
}
