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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version 
 */
public class MulticastParallelStressTest extends ContextTestSupport {

    public void testTwoMulticast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("ABCD", "ABCD");
        mock.expectsAscending().header("id");

        template.sendBodyAndHeader("direct:start", "", "id", 1);
        template.sendBodyAndHeader("direct:start", "", "id", 2);

        assertMockEndpointsSatisfied();
    }

    public void testMoreMulticast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.expectsAscending().header("id");

        for (int i = 0; i < 20; i++) {
            template.sendBodyAndHeader("direct:start", "", "id", i);
        }

        assertMockEndpointsSatisfied();
    }

    public void testConcurrencyParallelMulticast() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        // this time we cannot expect in order but there should be no duplicates
        mock.expectsNoDuplicates(header("id"));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 20; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader("direct:start", "", "id", index);
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();
        executor.shutdownNow();
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast(new AggregationStrategy() {
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                if (oldExchange == null) {
                                    return newExchange;
                                }

                                String body = oldExchange.getIn().getBody(String.class);
                                oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
                                return oldExchange;
                            }
                        }).parallelProcessing()
                            .to("direct:a", "direct:b", "direct:c", "direct:d")
                    // use end to indicate end of multicast route
                    .end()
                    .to("mock:result");

                from("direct:a").delay(20).setBody(body().append("A"));

                from("direct:b").setBody(body().append("B"));

                from("direct:c").delay(50).setBody(body().append("C"));

                from("direct:d").delay(10).setBody(body().append("D"));
            }
        };
    }

}
