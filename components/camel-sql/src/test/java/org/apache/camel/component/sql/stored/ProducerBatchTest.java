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
package org.apache.camel.component.sql.stored;

import java.util.ArrayList;
import java.util.HashMap;
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

public class ProducerBatchTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Before
    public void setUp() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.DERBY).addScript("sql/storedProcedureTest.sql").build();
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        db.shutdown();
    }

    @Test
    public void shouldExecuteBatch() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);


        List<Map<String, Object>> batchParams = new ArrayList<>();

        Map<String, Object> batch1 = new HashMap<>();
        batchParams.add(batch1);

        batch1.put("num", "1");


        Map<String, Object> batch2 = new HashMap<>();

        batch2.put("num", "3");
        batchParams.add(batch2);

        final long batchfnCallsBefore = TestStoredProcedure.BATCHFN_CALL_COUNTER.get();

        template.requestBody("direct:query", batchParams);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);

        assertNotNull(exchange.getIn().getHeader(SqlStoredConstants.SQL_STORED_UPDATE_COUNT));
        assertEquals(batchfnCallsBefore + 2, TestStoredProcedure.BATCHFN_CALL_COUNTER.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // required for the sql component
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:query").to("sql-stored:BATCHFN(INTEGER :#num)?batch=true").to("mock:query");
            }
        };
    }
}
