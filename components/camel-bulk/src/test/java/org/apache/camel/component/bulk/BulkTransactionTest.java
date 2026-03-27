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
package org.apache.camel.component.bulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkTransactionTest extends CamelTestSupport {

    private final List<Boolean> transactedFlags = new CopyOnWriteArrayList<>();
    private final List<Object> txContextDataValues = new CopyOnWriteArrayList<>();
    private final List<String> processingThreads = new CopyOnWriteArrayList<>();

    @Test
    void testTransactionContextPropagation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:txn-processed");
        mock.expectedMessageCount(5);

        transactedFlags.clear();
        txContextDataValues.clear();

        List<String> items = List.of("a", "b", "c", "d", "e");

        Exchange result = template.send("direct:txn-test", exchange -> {
            exchange.getIn().setBody(items);
            exchange.getExchangeExtension().setTransacted(true);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());

        // Verify all item exchanges had isTransacted() set to true
        assertEquals(5, transactedFlags.size());
        for (Boolean flag : transactedFlags) {
            assertTrue(flag, "Item exchange should be transacted");
        }
    }

    @Test
    void testTransactionContextDataShared() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:txn-processed");
        mock.expectedMessageCount(3);

        transactedFlags.clear();
        txContextDataValues.clear();

        List<String> items = List.of("x", "y", "z");

        Exchange result = template.send("direct:txn-test", exchange -> {
            exchange.getIn().setBody(items);
            exchange.getExchangeExtension().setTransacted(true);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());

        // All item exchanges should share the same TRANSACTION_CONTEXT_DATA map
        assertEquals(3, txContextDataValues.size());
        for (Object txCtx : txContextDataValues) {
            assertNotNull(txCtx, "TRANSACTION_CONTEXT_DATA should be set");
            assertInstanceOf(Map.class, txCtx);
        }
        // All should be the same map instance
        assertSame(txContextDataValues.get(0), txContextDataValues.get(1));
        assertSame(txContextDataValues.get(1), txContextDataValues.get(2));
    }

    @Test
    void testNonTransactedExchangeDoesNotSetTxData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:txn-processed");
        mock.expectedMessageCount(3);

        transactedFlags.clear();
        txContextDataValues.clear();

        List<String> items = List.of("a", "b", "c");

        Exchange result = template.send("direct:txn-test", exchange -> {
            exchange.getIn().setBody(items);
            // Do NOT set transacted
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());

        // Verify item exchanges are not transacted
        assertEquals(3, transactedFlags.size());
        for (Boolean flag : transactedFlags) {
            assertFalse(flag, "Item exchange should not be transacted");
        }

        // TRANSACTION_CONTEXT_DATA should not be set
        for (Object txCtx : txContextDataValues) {
            assertNull(txCtx, "TRANSACTION_CONTEXT_DATA should not be set for non-transacted exchanges");
        }
    }

    @Test
    void testParallelProcessingForcedSequentialWhenTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-txn-processed");
        mock.expectedMessageCount(10);

        processingThreads.clear();

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:parallel-txn-test", exchange -> {
            exchange.getIn().setBody(items);
            exchange.getExchangeExtension().setTransacted(true);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());

        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(10, bulkResult.getTotalItems());
        assertEquals(10, bulkResult.getSuccessCount());

        // Verify all items were processed on the same thread (sequential)
        assertEquals(10, processingThreads.size());
        String firstThread = processingThreads.get(0);
        for (String thread : processingThreads) {
            assertEquals(firstThread, thread, "All items should be processed on the same thread when transacted");
        }
    }

    @Test
    void testShareUnitOfWorkRollbackOnFailure() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:share-uow-test", exchange -> {
            exchange.getIn().setBody(items);
        });

        // With errorThreshold=1.0, bulk won't abort, but shareUnitOfWork should mark rollback
        assertNull(result.getException(), "Should not throw BulkException since threshold is 1.0");

        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(10, bulkResult.getTotalItems());
        // Items at index 0, 5 fail (every 5th)
        assertEquals(2, bulkResult.getFailureCount());
        assertEquals(8, bulkResult.getSuccessCount());
        assertFalse(bulkResult.isAborted());

        // Exchange should be marked for rollback because shareUnitOfWork is true and there were failures
        assertTrue(result.isRollbackOnly(),
                "Exchange should be marked rollback-only when shareUnitOfWork is enabled and there are failures");
    }

    @Test
    void testShareUnitOfWorkNoRollbackOnSuccess() throws Exception {
        List<String> items = List.of("a", "b", "c");

        Exchange result = template.send("direct:share-uow-success-test", exchange -> {
            exchange.getIn().setBody(items);
        });

        assertNull(result.getException());

        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(3, bulkResult.getTotalItems());
        assertEquals(3, bulkResult.getSuccessCount());
        assertEquals(0, bulkResult.getFailureCount());

        // Exchange should NOT be marked for rollback when there are no failures
        assertFalse(result.isRollbackOnly(), "Exchange should not be rollback-only when all items succeed");
    }

    @Test
    void testShareUnitOfWorkWithAbort() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:share-uow-abort-test", exchange -> {
            exchange.getIn().setBody(items);
        });

        // With errorThreshold=0.1 and shareUnitOfWork, bulk should abort via BulkException
        Exception exception = result.getException();
        assertNotNull(exception, "Expected BulkException to be thrown");
        assertInstanceOf(BulkException.class, exception);

        BulkResult bulkResult = ((BulkException) exception).getResult();
        assertTrue(bulkResult.isAborted());
    }

    @Test
    void testShareUnitOfWorkForcesSequentialWithParallel() throws Exception {
        processingThreads.clear();

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:share-uow-parallel-test", exchange -> {
            exchange.getIn().setBody(items);
        });

        assertNull(result.getException());

        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(10, bulkResult.getTotalItems());
        assertEquals(10, bulkResult.getSuccessCount());

        // Verify all items were processed on the same thread (forced sequential)
        assertEquals(10, processingThreads.size());
        String firstThread = processingThreads.get(0);
        for (String thread : processingThreads) {
            assertEquals(firstThread, thread,
                    "All items should be processed on the same thread when shareUnitOfWork is enabled");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Processor that captures transaction state from item exchanges
                from("direct:txn-capture")
                        .process(exchange -> {
                            transactedFlags.add(exchange.isTransacted());
                            txContextDataValues.add(exchange.getProperty(Exchange.TRANSACTION_CONTEXT_DATA));
                        })
                        .to("mock:txn-processed");

                from("direct:txn-test")
                        .to("bulk:txnJob?chunkSize=100&processorRef=direct:txn-capture");

                // Processor that records the processing thread name
                from("direct:parallel-txn-capture")
                        .process(exchange -> {
                            processingThreads.add(Thread.currentThread().getName());
                        })
                        .to("mock:parallel-txn-processed");

                from("direct:parallel-txn-test")
                        .to("bulk:parallelTxnJob?chunkSize=5&processorRef=direct:parallel-txn-capture&parallelProcessing=true");

                // Processor that fails every 5th item
                from("direct:share-uow-fail-some")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BulkConstants.BULK_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Simulated failure for item " + index);
                            }
                        });

                from("direct:share-uow-test")
                        .to("bulk:shareUowJob?chunkSize=100&processorRef=direct:share-uow-fail-some&shareUnitOfWork=true");

                // Processor that always succeeds
                from("direct:share-uow-success-processor")
                        .log("Processing ${body}");

                from("direct:share-uow-success-test")
                        .to("bulk:shareUowSuccessJob?chunkSize=100&processorRef=direct:share-uow-success-processor&shareUnitOfWork=true");

                from("direct:share-uow-abort-test")
                        .to("bulk:shareUowAbortJob?chunkSize=100&processorRef=direct:share-uow-fail-some&shareUnitOfWork=true&errorThreshold=0.1");

                // Processor that records thread names for parallel + shareUnitOfWork test
                from("direct:share-uow-parallel-capture")
                        .process(exchange -> {
                            processingThreads.add(Thread.currentThread().getName());
                        });

                from("direct:share-uow-parallel-test")
                        .to("bulk:shareUowParallelJob?chunkSize=5&processorRef=direct:share-uow-parallel-capture&parallelProcessing=true&shareUnitOfWork=true");
            }
        };
    }
}
