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
package org.apache.camel.component.restlet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Restlet producer concurrent test
 * 
 * @version 
 */
public class RestletProducerConcurrentTest extends RestletTestSupport {

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

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<String>> responses = new HashMap<Integer, Future<String>>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<String> out = executor.submit(new Callable<String>() {
                public String call() throws Exception {
                    Map<String, Object> headers = new HashMap<String, Object>();
                    headers.put("username", "davsclaus");
                    headers.put("id", index);
                    return template
                        .requestBodyAndHeaders("restlet:http://localhost:" + portNum + "/users/davsclaus/" + index + "?restletMethod=GET",
                                               null, headers, String.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();
        assertEquals(files, responses.size());

        // get all responses
        Set<String> unique = new HashSet<String>();
        for (Future<String> future : responses.values()) {
            unique.add(future.get());
        }

        // should be 'files' unique responses
        assertEquals("Should be " + files + " unique responses", files, unique.size());
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:http://localhost:" + portNum + "/users/{username}/{id}?restletMethod=GET")
                    .to("log:inbox").process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String index = exchange.getIn().getHeader("id", String.class);
                            exchange.getOut().setBody(index);
                        }
                    }).to("mock:result");
            }
        };
    }

}
