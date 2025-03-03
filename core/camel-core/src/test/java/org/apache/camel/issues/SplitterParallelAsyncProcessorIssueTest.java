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
package org.apache.camel.issues;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SplitterParallelAsyncProcessorIssueTest extends ContextTestSupport {

    private final Set<String> threads = new HashSet<>();

    @Test
    public void testSplitParallelProcessingMaxThreads() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(10);

        String xmlBody = "<employees>" +
                         "<employee><id>1</id><name>John</name></employee>" +
                         "<employee><id>2</id><name>Jane</name></employee>" +
                         "<employee><id>3</id><name>Jim</name></employee>" +
                         "<employee><id>4</id><name>Jack</name></employee>" +
                         "<employee><id>5</id><name>Jill</name></employee>" +
                         "<employee><id>6</id><name>opi</name></employee>" +
                         "<employee><id>7</id><name>ds</name></employee>" +
                         "<employee><id>8</id><name>hhh</name></employee>" +
                         "<employee><id>9</id><name>fki</name></employee>" +
                         "<employee><id>10</id><name>abc</name></employee>" +
                         "</employees> ";

        template.sendBody("direct:start", xmlBody);

        assertMockEndpointsSatisfied();

        log.info("{} Threads in use: {}", threads.size(), threads);

        // 2 from split EIP and 2 from the async processor that uses the JDK ForJoinPool
        Assertions.assertTrue(threads.size() <= 4, "Should not use more than 4 threads, was: " + threads.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // only use 2 threads at-most
                ThreadPoolProfile myThreadPoolProfile = new ThreadPoolProfile("threadPoolProfile");
                myThreadPoolProfile.setMaxPoolSize(2);
                myThreadPoolProfile.setPoolSize(2);
                myThreadPoolProfile.setMaxQueueSize(2);
                myThreadPoolProfile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);

                getContext().getExecutorServiceManager().setDefaultThreadPoolProfile(myThreadPoolProfile);

                AsyncProcessor asyncProcessor = new AsyncProcessor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                    }

                    @Override
                    public boolean process(Exchange exchange, AsyncCallback callback) {
                        // Run the processing in a separate thread
                        CompletableFuture.runAsync(() -> {
                            threads.add(Thread.currentThread().getName());
                            try {
                                // Simulate some asynchronous processing
                                Thread.sleep(250);
                                exchange.getIn().setBody("Processed asynchronously");
                            } catch (InterruptedException e) {
                                exchange.setException(e);
                            } finally {
                                // Signal completion
                                callback.done(false);
                            }
                        });

                        // Return false to indicate that processing is not complete yet
                        return false;
                    }

                    @Override
                    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
                        return null;
                    }
                };

                from("direct:start")
                    .split()
                    .xpath("/employees/employee")
                    .parallelProcessing().stopOnException().timeout(10000).executorService("threadPoolProfile").synchronous()
                        .process(e -> threads.add(Thread.currentThread().getName()))
                        .process(asyncProcessor)
                        .process(e -> threads.add(Thread.currentThread().getName()))
                        .delay(250)
                    .end()
                    .to("mock:split");
            }
        };
    }

}
