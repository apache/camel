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
package org.apache.camel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.support.SynchronizationAdapter;

/**
 *
 */
public class UnitOfWorkSyncProcessTest extends ContextTestSupport {

    private static String consumerThread;
    private static String afterThread;
    private static String taskThread;
    private static String doneThread;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void tearDown() throws Exception {
        executorService.shutdownNow();
        super.tearDown();
    }

    public void testUnitOfWorkSync() throws Exception {
        // skip test on AIX
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        // should be same thread
        assertEquals(taskThread, afterThread);
        // should not be same
        assertNotSame(doneThread, afterThread);
        assertNotSame(doneThread, consumerThread);
        // should be same thread
        assertEquals(consumerThread, doneThread);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(new MyEndpoint())
                    .process(new AsyncProcessor() {
                        @Override
                        public boolean process(final Exchange exchange, final AsyncCallback callback) {
                            executorService.submit(new Runnable() {
                                @Override
                                public void run() {
                                    taskThread = Thread.currentThread().getName();
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        // ignore
                                    }
                                    exchange.getIn().setHeader("foo", 123);
                                    callback.done(false);
                                }
                            });

                            return false;
                        }

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // noop
                        }
                    })
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            afterThread = Thread.currentThread().getName();
                        }
                    })
                    .to("mock:result");
            }
        };
    }

    private final class MyEndpoint extends DefaultEndpoint {

        @Override
        public Producer createProducer() throws Exception {
            // not supported
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new MyConsumer(this, processor);
        }

        @Override
        protected String createEndpointUri() {
            return "myEndpoint://foo";
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

    }

    private final class MyConsumer implements Consumer {
        private Processor processor;
        private Endpoint endpoint;

        private MyConsumer(Endpoint endpoint, Processor processor) {
            this.endpoint = endpoint;
            this.processor = processor;
        }

        @Override
        public Endpoint getEndpoint() {
            return endpoint;
        }

        @Override
        public void start() throws Exception {
            consumerThread = Thread.currentThread().getName();

            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(Exchange.UNIT_OF_WORK_PROCESS_SYNC, true);
            exchange.addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    doneThread = Thread.currentThread().getName();
                }
            });

            // just fire the exchange when started
            processor.process(exchange);
        }

        @Override
        public void stop() throws Exception {
            // noop
        }
    }
}
