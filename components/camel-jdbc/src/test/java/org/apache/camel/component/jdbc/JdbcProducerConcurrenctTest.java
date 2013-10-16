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
package org.apache.camel.component.jdbc;

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
import org.junit.Test;

public class JdbcProducerConcurrenctTest extends AbstractJdbcTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;
    
    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    @SuppressWarnings("rawtypes")
    private void doSendMessages(int files, int poolSize) throws Exception {
        mock.expectedMessageCount(files);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<List<?>>> responses = new HashMap<Integer, Future<List<?>>>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<List<?>> out = executor.submit(new Callable<List<?>>() {
                public List<?> call() throws Exception {
                    int id = (index % 2) + 1;
                    return template.requestBody("direct:start", "select * from customer where id = 'cust" + id + "'", List.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();
        assertEquals(files, responses.size());

        for (int i = 0; i < files; i++) {
            List<?> rows = responses.get(i).get();
            Map columns = (Map) rows.get(0);
            if (i % 2 == 0) {
                assertEquals("jstrachan", columns.get("NAME"));
            } else {
                assertEquals("nsandhu", columns.get("NAME"));
            }
        }
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:testdb").to("mock:result");
            }
        };
    }
}