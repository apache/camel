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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.view.ViewOptions;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With {@code consumerProcessedStrategy=delete} a document must be removed only once its exchange has been processed
 * successfully: a failed exchange, or a row that is never handed to the route, keeps its document for the next poll.
 * <p/>
 * The consumer runs in a real route, so the exchange goes through a unit of work, but its scheduler is not started and
 * the test calls {@code poll()} itself against a mocked cluster.
 */
class CouchbaseConsumerProcessedStrategyTest {

    private static final String URI = "couchbase:http://localhost:8091?bucket=bucket&username=user&password=secret"
                                      + "&consumerProcessedStrategy=delete&startScheduler=false";

    private DefaultCamelContext context;
    private MockedStatic<Cluster> clusters;
    private Bucket bucket;
    private Scope scope;
    private Collection collection;

    @BeforeEach
    void setUp() {
        bucket = mock(Bucket.class);
        scope = mock(Scope.class);
        collection = mock(Collection.class);
        when(bucket.defaultScope()).thenReturn(scope);
        when(bucket.defaultCollection()).thenReturn(collection);
        Cluster cluster = mock(Cluster.class);
        when(cluster.bucket(anyString())).thenReturn(bucket);
        clusters = mockStatic(Cluster.class);
        clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        clusters.close();
    }

    private CouchbaseConsumer startRoute(String uri, Processor processor) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("couchbase").process(processor);
            }
        });
        context.start();
        return (CouchbaseConsumer) context.getRoute("couchbase").getConsumer();
    }

    private void queryReturns(String... ids) {
        List<JsonObject> rows = new ArrayList<>();
        for (String id : ids) {
            rows.add(JsonObject.create().put(CouchbaseConsumer.SQL_DOCUMENT_ID_ALIAS, id).put("name", id));
        }
        QueryResult result = mock(QueryResult.class);
        when(result.rowsAsObject()).thenReturn(rows);
        when(scope.query(anyString(), any(QueryOptions.class))).thenReturn(result);
    }

    private void verifyRemoved(String id) {
        verify(collection).remove(eq(id), any(RemoveOptions.class));
    }

    private void verifyNotRemoved(String id) {
        verify(collection, never()).remove(eq(id), any(RemoveOptions.class));
    }

    private boolean removedYet() {
        return mockingDetails(collection).getInvocations().stream()
                .anyMatch(invocation -> "remove".equals(invocation.getMethod().getName()));
    }

    @Test
    void aProcessedDocumentIsRemovedAfterTheRouteHasRun() throws Exception {
        queryReturns("doc-1");
        List<Boolean> removedWhileRouting = new ArrayList<>();
        CouchbaseConsumer consumer = startRoute(URI, exchange -> removedWhileRouting.add(removedYet()));

        assertEquals(1, consumer.poll());

        assertEquals(List.of(false), removedWhileRouting, "the document must still exist while the route processes it");
        verifyRemoved("doc-1");
    }

    @Test
    void aFailedExchangeKeepsItsDocument() throws Exception {
        queryReturns("doc-1", "doc-2");
        CouchbaseConsumer consumer = startRoute(URI, exchange -> {
            if ("doc-1".equals(exchange.getMessage().getHeader(CouchbaseConstants.HEADER_ID))) {
                throw new IllegalStateException("the route blew up");
            }
        });

        consumer.poll();

        verifyNotRemoved("doc-1");
        verifyRemoved("doc-2");
    }

    @Test
    void rowsBeyondMaxMessagesPerPollKeepTheirDocuments() throws Exception {
        queryReturns("doc-1", "doc-2", "doc-3");
        CouchbaseConsumer consumer = startRoute(URI, exchange -> {
        });
        consumer.setMaxMessagesPerPoll(1);

        consumer.poll();

        verifyRemoved("doc-1");
        verifyNotRemoved("doc-2");
        verifyNotRemoved("doc-3");
    }

    @Test
    void rowsLeftWhenTheConsumerStopsMidBatchKeepTheirDocuments() throws Exception {
        queryReturns("doc-1", "doc-2", "doc-3");
        AtomicReference<CouchbaseConsumer> consumerRef = new AtomicReference<>();
        // what the shutdown strategy does to a consumer that should only complete its current task
        CouchbaseConsumer consumer = startRoute(URI,
                exchange -> consumerRef.get().deferShutdown(ShutdownRunningTask.CompleteCurrentTaskOnly));
        consumerRef.set(consumer);

        consumer.poll();

        verifyRemoved("doc-1");
        verifyNotRemoved("doc-2");
        verifyNotRemoved("doc-3");
    }

    @Test
    @SuppressWarnings("deprecation")
    void aFailedExchangeKeepsItsDocumentWhenConsumingAView() throws Exception {
        ViewRow failing = viewRow("doc-1");
        ViewRow succeeding = viewRow("doc-2");
        ViewResult result = mock(ViewResult.class);
        when(result.rows()).thenReturn(List.of(failing, succeeding));
        when(bucket.viewQuery(anyString(), anyString(), any(ViewOptions.class))).thenReturn(result);

        CouchbaseConsumer consumer = startRoute(URI + "&useView=true&designDocumentName=dd&viewName=view", exchange -> {
            if ("doc-1".equals(exchange.getMessage().getHeader(CouchbaseConstants.HEADER_ID))) {
                throw new IllegalStateException("the route blew up");
            }
        });

        consumer.poll();

        verifyNotRemoved("doc-1");
        verifyRemoved("doc-2");
    }

    @Test
    void aFailedRemovalIsReportedToTheExceptionHandler() throws Exception {
        queryReturns("doc-1");
        RuntimeException failure = new IllegalStateException("cannot remove");
        when(collection.remove(eq("doc-1"), any(RemoveOptions.class))).thenThrow(failure);
        CouchbaseConsumer consumer = startRoute(URI, exchange -> {
        });
        AtomicReference<Throwable> reported = new AtomicReference<>();
        consumer.setExceptionHandler(new CapturingExceptionHandler(reported));

        consumer.poll();

        verify(collection, times(1)).remove(eq("doc-1"), any(RemoveOptions.class));
        assertSame(failure, reported.get());
    }

    @Test
    void otherStrategiesDoNotRemoveDocuments() throws Exception {
        queryReturns("doc-1");
        CouchbaseConsumer consumer = startRoute(URI.replace("=delete", "=none"), exchange -> {
        });

        consumer.poll();

        assertFalse(removedYet());
    }

    private static ViewRow viewRow(String id) {
        ViewRow row = mock(ViewRow.class);
        when(row.id()).thenReturn(Optional.of(id));
        when(row.valueAs(Object.class)).thenReturn(Optional.of(id));
        when(row.keyAs(String.class)).thenReturn(Optional.of(id));
        return row;
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final AtomicReference<Throwable> captured;

        private CapturingExceptionHandler(AtomicReference<Throwable> captured) {
            this.captured = captured;
        }

        @Override
        public void handleException(Throwable exception) {
            captured.set(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            captured.set(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            captured.set(exception);
        }
    }
}
