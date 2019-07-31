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
package org.apache.camel.tests.component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;

public class PerformanceTestComponent extends DefaultComponent {
    public static final String HEADER_THREADS = "CamelPerfThreads";
    public static final String HEADER_ITERATIONS = "CamelPerfIterations";

    private static final int DEFAULT_THREADS = 8;
    private static final int DEFAULT_ITERATIONS = 100;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new PerformanceTestEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public static int getHeaderValue(Exchange exchange, String header) {
        Integer value = exchange.getContext().getTypeConverter().convertTo(Integer.class, exchange, exchange.getIn().getHeader(header));
        return value != null ? value : header.equals(HEADER_THREADS) ? DEFAULT_THREADS : header.equals(HEADER_ITERATIONS) ? DEFAULT_ITERATIONS : 0;
    }

    private static final class PerformanceTestEndpoint extends DefaultEndpoint {
        private PerformanceTestConsumer consumer;

        protected PerformanceTestEndpoint(String uri, Component component) {
            super(uri, component);
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            synchronized (this) {
                if (consumer != null && processor != consumer.getProcessor()) {
                    throw new Exception("PerformanceTestEndpoint doesn not support multiple consumers per Endpoint");
                }
                consumer = new PerformanceTestConsumer(this, processor);
            }
            return consumer;
        }

        @Override
        public Producer createProducer() throws Exception {
            return new PerformanceTestProducer(this);
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        public Consumer getConsumer() {
            return consumer;
        }
    }

    private static final class PerformanceTestConsumer extends DefaultConsumer {
        protected PerformanceTestConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }
    }

    private static final class PerformanceTestProducer extends DefaultProducer implements AsyncProcessor {
        protected PerformanceTestProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(final Exchange exchange) throws Exception {
            final int count = getHeaderValue(exchange, HEADER_ITERATIONS);
            final int threads = getHeaderValue(exchange, HEADER_THREADS);
            PerformanceTestEndpoint endpoint = (PerformanceTestEndpoint)getEndpoint();
            if (endpoint != null) {
                final DefaultConsumer consumer = (DefaultConsumer)endpoint.getConsumer();
                ExecutorService executor = exchange.getContext().getExecutorServiceManager().newFixedThreadPool(this, "perf", threads);
                CompletionService<Exchange> tasks = new ExecutorCompletionService<>(executor);

                // StopWatch watch = new StopWatch(); // if we want to clock how
                // long it takes
                for (int i = 0; i < count; i++) {
                    tasks.submit(new Callable<Exchange>() {
                        @Override
                        public Exchange call() throws Exception {
                            Exchange exch = ExchangeHelper.createCopy(exchange, false);
                            try {
                                consumer.getProcessor().process(exch);
                            } catch (final Exception e) {
                                exch.setException(e);
                            }
                            return exch;
                        }
                    });
                }

                for (int i = 0; i < count; i++) {
                    // Future<Exchange> result = tasks.take();
                    tasks.take(); // wait for all exchanges to complete
                }
            }
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            try {
                this.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
