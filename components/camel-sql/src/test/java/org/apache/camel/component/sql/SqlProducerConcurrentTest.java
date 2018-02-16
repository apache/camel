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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
public class SqlProducerConcurrentTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockEndpoint;
    private EmbeddedDatabase db;

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        mockEndpoint.expectedMessageCount(files);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<List<?>>> responses = new HashMap<Integer, Future<List<?>>>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<List<?>> out = executor.submit(new Callable<List<?>>() {
                public List<?> call() throws Exception {
                    int id = (index % 3) + 1;
                    return template.requestBody("direct:simple", "" + id, List.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();
        assertEquals(files, responses.size());

        for (int i = 0; i < files; i++) {
            List<?> rows = responses.get(i).get();
            Map<?, ?> columns = assertIsInstanceOf(Map.class, rows.get(0));
            if (i % 3 == 0) {
                assertEquals("Camel", columns.get("PROJECT"));
            } else if (i % 3 == 1) {
                assertEquals("AMQ", columns.get("PROJECT"));
            } else {
                assertEquals("Linux", columns.get("PROJECT"));
            }
        }
        executor.shutdownNow();
    }

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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:simple").to("sql:select * from projects where id = # order by id").to("mock:result");
            }
        };
    }
}