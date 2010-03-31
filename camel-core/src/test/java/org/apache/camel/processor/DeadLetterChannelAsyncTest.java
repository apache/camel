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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.LoggingLevel;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * Test to ensure that async processing inside the DLC endpoint does not break message handling
 * -> https://issues.apache.org/activemq/browse/CAMEL-2605
 *
 * @version $Revision: 761633 $
 */
public class DeadLetterChannelAsyncTest extends ContextTestSupport {

    private static final int COUNT = 1;

    private final CountDownLatch sent = new CountDownLatch(COUNT);
    private final CountDownLatch handled = new CountDownLatch(COUNT);

    private final Executor executor = Executors.newFixedThreadPool(2);

    public void testAsyncDlcHandling() throws Exception {
        executor.execute(new Runnable() {
            public void run() {
                sendBody("direct:start", "Desperately need processing...");
                sent.countDown();
            }
        });

        assertTrue("Call to sendBody should have ended successfully",
                   sent.await(2, TimeUnit.SECONDS));
        assertTrue("Error should have been handled asynchronously",
                   handled.await(2, TimeUnit.SECONDS));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("direct:dlc").maximumRedeliveries(0));

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new RuntimeException("Let's see what the DLC can do about me...");
                    }
                });

                from("direct:dlc").process(new AsyncProcessor() {

                    public boolean process(Exchange exchange, final AsyncCallback callback) {
                        executor.execute(new Runnable() {

                            public void run() {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    // ignoring this, we don't mind if we get woken up early
                                }
                                handled.countDown();
                                callback.done(false);
                            }

                        });

                        return false;
                    }

                    public void process(Exchange exchange) throws Exception {
                        AsyncProcessorHelper.process(this, exchange);
                    }
                });
            }
        };
    }
}
