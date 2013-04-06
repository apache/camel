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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SplitterParallelNoStopOnExceptionTest extends ContextTestSupport {

    private ExecutorService service; 
    
    protected void setUp() throws Exception {
        // use a pool with 2 concurrent tasks so we cannot run too fast
        service = Executors.newFixedThreadPool(2);
        super.setUp();
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        service.shutdownNow();
    }

    public void testSplitParallelNoStopOnExceptionOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World", "Hi World");

        template.sendBody("direct:start", "Hello World,Bye World,Hi World");

        assertMockEndpointsSatisfied();
    }

    public void testSplitParallelNoStopOnExceptionStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMinimumMessageCount(0);
        // we do NOT stop so we receive all messages except the one that goes kaboom
        mock.allMessages().body().isNotEqualTo("Kaboom");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Goodday World", "Bye World", "Hi World");

        try {
            template.sendBody("direct:start", "Hello World,Goodday World,Kaboom,Bye World,Hi World");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start")
                        .split(body().tokenize(",")).parallelProcessing().executorService(service)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Kaboom".equals(body)) {
                                    throw new IllegalArgumentException("Forced");
                                }
                            }
                        }).to("mock:split");
            }
        };
    }

}