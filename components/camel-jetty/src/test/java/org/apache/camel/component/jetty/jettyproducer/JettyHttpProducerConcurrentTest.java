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
package org.apache.camel.component.jetty.jettyproducer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 * Jetty HTTP producer concurrent test.
 *
 * @version
 */
public class JettyHttpProducerConcurrentTest extends BaseJettyTest {

    @Test
    public void testNoConcurrentProducers() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        // give Jetty time to startup properly
        Thread.sleep(2000);

        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        // these tests does not run well on Windows
        if (isPlatform("windows")) {
            return;
        }

        // give Jetty time to startup properly
        Thread.sleep(2000);

        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").assertNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        Map<Integer, Future> responses = new ConcurrentHashMap<Integer, Future>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future out = executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    return template.requestBody("jetty://http://localhost:{{port}}/echo", "" + index, String.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();

        assertEquals(files, responses.size());

        // get all responses
        Set<Object> unique = new HashSet<Object>();
        for (Future future : responses.values()) {
            unique.add(future.get());
        }

        // should be 10 unique responses
        assertEquals("Should be " + files + " unique responses", files, unique.size());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // expose a echo service
                from("jetty:http://localhost:{{port}}/echo")
                        .convertBodyTo(String.class)
                        .to("log:input")
                        .transform(body().append(body())).to("mock:result");
            }
        };
    }

}
