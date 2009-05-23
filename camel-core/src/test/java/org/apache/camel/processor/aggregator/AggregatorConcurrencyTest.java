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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class AggregatorConcurrencyTest extends ContextTestSupport {

    private static final transient Log LOG = LogFactory.getLog(AggregatorConcurrencyTest.class);

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final AtomicInteger SUM = new AtomicInteger(0);

    private final int size = 100;
    private final String uri = "direct:start";

    public void testAggregateConcurrency() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        int total = 0;
        ExecutorService service = Executors.newFixedThreadPool(20);
        for (int i = 0; i < size; i++) {
            final int count = i;
            total += i;
            service.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader(uri, "Hello World", "index", count);
                    return null;
                }
            });
        }
        mock.expectedBodiesReceived(total);
        mock.expectedHeaderReceived("total", total);
        mock.expectedPropertyReceived(Exchange.AGGREGATED_SIZE, size);

        assertMockEndpointsSatisfied();

        assertEquals(100, COUNTER.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(uri)
                    .aggregate(constant(true), new AggregationStrategy() {
                        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                            Exchange answer = oldExchange != null ? oldExchange : newExchange;
                            COUNTER.getAndIncrement();

                            Integer newIndex = newExchange.getIn().getHeader("index", Integer.class);
                            int total = SUM.addAndGet(newIndex);
                            answer.getIn().setHeader("total", total);

                            LOG.debug("Index: " + newIndex + ". Total so far: " + total);
                            return answer;
                        }
                    }).batchTimeout(60000).completionPredicate(property(Exchange.AGGREGATED_SIZE).isEqualTo(100))
                    .to("direct:foo");

                from("direct:foo").setBody().header("total").to("mock:result");
            }
        };
    }
}
