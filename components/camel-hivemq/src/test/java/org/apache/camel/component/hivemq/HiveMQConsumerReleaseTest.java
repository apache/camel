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
package org.apache.camel.component.hivemq;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HiveMQConsumerReleaseTest {

    private DefaultCamelContext camelContext;
    private HiveMQComponent component;
    private HiveMQEndpoint endpoint;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        component = new HiveMQComponent();
        component.setCamelContext(camelContext);
        endpoint = new HiveMQEndpoint("hivemq:test", component, new HiveMQConfiguration(), "test");
        endpoint.setCamelContext(camelContext);
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    @DisplayName("Synchronous processing releases the exchange exactly once")
    void syncProcessingReleasesOnce() throws Exception {
        CountingConsumer consumer = new CountingConsumer(endpoint, exchange -> {
        });

        invokeProcessExchange(consumer, new DefaultExchange(camelContext));

        assertThat(consumer.releases.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Asynchronous processing releases the exchange exactly once")
    void asyncProcessingReleasesOnce() throws Exception {
        CountDownLatch processed = new CountDownLatch(1);
        CountingConsumer consumer = new CountingConsumer(endpoint, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                CompletableFuture.runAsync(() -> {
                    callback.done(false);
                    processed.countDown();
                });
                return false;
            }
        });

        invokeProcessExchange(consumer, new DefaultExchange(camelContext));
        assertThat(processed.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(consumer.releases.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Callback plus thrown exception still releases the exchange exactly once")
    void callbackThenThrowReleasesOnce() throws Exception {
        CountingConsumer consumer = new CountingConsumer(endpoint, new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                callback.done(true);
                throw new RuntimeException("after callback");
            }
        });

        invokeProcessExchange(consumer, new DefaultExchange(camelContext));

        assertThat(consumer.releases.get()).isEqualTo(1);
    }

    private static void invokeProcessExchange(HiveMQConsumer consumer, Exchange exchange) throws Exception {
        Method method = HiveMQConsumer.class.getDeclaredMethod("processExchange", Exchange.class);
        method.setAccessible(true);
        method.invoke(consumer, exchange);
    }

    private static final class CountingConsumer extends HiveMQConsumer {

        private final AtomicInteger releases = new AtomicInteger();

        CountingConsumer(HiveMQEndpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public void releaseExchange(Exchange exchange, boolean autoRelease) {
            releases.incrementAndGet();
            super.releaseExchange(exchange, autoRelease);
        }
    }
}
