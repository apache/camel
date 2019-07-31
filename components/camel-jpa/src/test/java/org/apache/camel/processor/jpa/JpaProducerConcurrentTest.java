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
package org.apache.camel.processor.jpa;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

public class JpaProducerConcurrentTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").assertNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<SendEmail>> responses = new HashMap<>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<SendEmail> out = executor.submit(new Callable<SendEmail>() {
                public SendEmail call() throws Exception {
                    return template.requestBody("direct:start", new SendEmail("user" + index + "@somewhere.org"), SendEmail.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);

        assertEquals(files, responses.size());

        // get them so they are complete
        for (Future<SendEmail> future : responses.values()) {
            SendEmail sendEmail = future.get();
            assertNotNull(sendEmail);
            log.info("Persisted the SendEmail entity with the id {} and the address {}", sendEmail.getId(), sendEmail.getAddress());
        }

        // assert in the database
        assertEntityInDB(files);
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start").to("jpa://" + SendEmail.class.getName() + "?usePersist=true").to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}