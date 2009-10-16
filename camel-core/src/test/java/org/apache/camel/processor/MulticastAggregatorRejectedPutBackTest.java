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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MulticastAggregatorRejectedPutBackTest extends MulticastAnotherAggregatorTest {

    private static final Log LOG = LogFactory.getLog(MulticastAggregatorRejectedPutBackTest.class);

    public void testMulticastLoadParallelly() throws Exception {
        sendLoad(true);
    }

    public void testMulticastLoadSequentially() throws Exception {
        sendLoad(false);
    }

    public void sendLoad(boolean isParallel) throws Exception {
        final int numMsgs = 10;
        final int numThreads = 10;
        final AtomicLong total = new AtomicLong(0);
        final String url = isParallel ? "direct:parallel" : "direct:sequential";

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(numThreads * numMsgs);

        Runnable runner = new Runnable() {
            public void run() {
                try {
                    Processor processor = new Processor() {
                        public void process(Exchange exchange) {
                            Message in = exchange.getIn();
                            in.setBody("input");
                            in.setHeader("foo", "bar");
                        }
                    };

                    for (int count = 0; count < numMsgs; count++) {
                        template.request(url, processor);
                    }

                    LOG.debug("Runner completed: " + total.incrementAndGet());
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        };
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int count = 0; count < numThreads; count++) {
            executor.execute(runner);
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertMockEndpointsSatisfied();
    }

    private class WorkQueuePolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(runnable);
            } catch (InterruptedException e) {
                // should not happen
                throw new RejectedExecutionException(e);
            }
        }
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                ThreadPoolExecutor tpExecutor = new ThreadPoolExecutor(1, 10, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
                tpExecutor.setRejectedExecutionHandler(new WorkQueuePolicy());

                // START SNIPPET: example
                // The message will be sent parallelly to the endpoints
                from("direct:parallel")
                    .multicast(new BodyOutAggregatingStrategy(), true).executorService(tpExecutor)
                        .to("direct:x", "direct:y", "direct:z");
                // Multicast the message in a sequential way
                from("direct:sequential").multicast(new BodyOutAggregatingStrategy()).to("direct:x", "direct:y", "direct:z");

                from("direct:x").process(new AppendingProcessor("x")).to("direct:aggregator");
                from("direct:y").process(new AppendingProcessor("y")).to("direct:aggregator");
                from("direct:z").process(new AppendingProcessor("z")).to("direct:aggregator");

                from("direct:aggregator").aggregate(header("cheese"), new BodyInAggregatingStrategy()).
                        completionPredicate(property(Exchange.AGGREGATED_SIZE).isEqualTo(3)).to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}