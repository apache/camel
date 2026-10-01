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
package org.apache.camel.component.couchbase;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CouchbaseConsumerTest {

    private DefaultCamelContext context;
    private CouchbaseEndpoint endpoint;
    private CouchbaseConsumer consumer;
    private MockedStatic<Cluster> clusters;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        endpoint = new CouchbaseEndpoint(
                "couchbase:http://localhost:8091", "http://localhost:8091",
                new CouchbaseComponent(context));
        endpoint.setBucket("bucket");
        endpoint.setUsername("user");
        endpoint.setPassword("secret");
        context.start();

        // the consumer takes its handles from the endpoint on every start, so the endpoint has to hand out a
        // bucket without a server behind it
        Bucket bucket = mock(Bucket.class);
        Scope scope = mock(Scope.class);
        when(bucket.defaultScope()).thenReturn(scope);
        when(bucket.defaultCollection()).thenReturn(mock(Collection.class));
        Cluster cluster = mock(Cluster.class);
        when(cluster.bucket(anyString())).thenReturn(bucket);
        clusters = mockStatic(Cluster.class);
        clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
        if (clusters != null) {
            clusters.close();
        }
    }

    /**
     * {@code isBatchAllowed()} is false until the consumer is started, so the batch has to run against a started
     * consumer to exercise anything at all. The initial delay keeps the scheduler from ever polling the mocked bucket.
     */
    private CouchbaseConsumer startedConsumer(Processor processor) throws Exception {
        consumer = new CouchbaseConsumer(endpoint, endpoint.createClient(), processor);
        consumer.setInitialDelay(Long.MAX_VALUE / 2);
        consumer.start();
        return consumer;
    }

    /**
     * A failing route does not throw out of {@code process()} - the failure is left on the exchange - so a consumer
     * that only catches never learns about the common case. With {@code consumerProcessedStrategy=delete} the document
     * is already gone by then, so an unreported failure loses the message outright.
     */
    @Test
    void aRouteFailureIsReportedToTheExceptionHandler() throws Exception {
        Exception failure = new IllegalStateException("the route blew up");
        CouchbaseConsumer consumer = startedConsumer(ex -> ex.setException(failure));

        AtomicReference<Exception> reported = new AtomicReference<>();
        consumer.setExceptionHandler(new CapturingExceptionHandler(reported));

        Queue<Object> exchanges = new ArrayDeque<>();
        exchanges.add(endpoint.createExchange());

        consumer.processBatch(exchanges);

        assertNotNull(reported.get(), "the route failure should have been handed to the exception handler");
        assertEquals(failure, reported.get());
    }

    /**
     * A poll that produces more exchanges than the batch hands to the route must not simply drop the remainder: nothing
     * else releases them, and a pooled exchange that is never released never returns to the pool.
     */
    @Test
    void exchangesBeyondTheBatchAreReleasedRatherThanDropped() throws Exception {
        CouchbaseConsumer consumer = startedConsumer(ex -> {
        });
        consumer.setMaxMessagesPerPoll(1);

        Queue<Object> exchanges = new ArrayDeque<>();
        for (int i = 0; i < 3; i++) {
            exchanges.add(endpoint.createExchange());
        }

        consumer.processBatch(exchanges);

        assertTrue(exchanges.isEmpty(), "the exchanges not handed to the route should have been released, not left behind");
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final AtomicReference<Exception> captured;

        private CapturingExceptionHandler(AtomicReference<Exception> captured) {
            this.captured = captured;
        }

        @Override
        public void handleException(Throwable exception) {
            captured.set((Exception) exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            captured.set((Exception) exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            captured.set((Exception) exception);
        }
    }
}
