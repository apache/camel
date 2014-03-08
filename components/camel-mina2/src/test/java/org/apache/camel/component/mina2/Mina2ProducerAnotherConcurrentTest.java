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
package org.apache.camel.component.mina2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class Mina2ProducerAnotherConcurrentTest extends BaseMina2Test {

    @Test
    public void testSimple() throws Exception {
        String out = template.requestBody("direct:start", "A", String.class);
        assertEquals("Bye A", out);
    }

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(200, 5);
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
                    return template.requestBody("direct:start", index, String.class);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();
        assertEquals(files, responses.size());

        for (int i = 0; i < files; i++) {
            String out = responses.get(i).get();
            assertEquals("Bye " + i, out);
        }
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from("direct:start").to(String.format("mina2:tcp://localhost:%1$s?sync=true", getPort()));

                from(String.format("mina2:tcp://localhost:%1$s?sync=true", getPort())).process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body);
                    }
                }).to("mock:result");
            }
        };
    }
}
