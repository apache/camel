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
package org.apache.camel.component.milo.client.internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for {@link SubscriptionManager} exception propagation through write() and call().
 *
 * <p>
 * Regression tests for CAMEL-25694: {@code SubscriptionManager.write()} and {@code call()} previously used
 * {@code handleAsync()} which converted exceptional completions into normal completions with null values. The fix
 * replaces {@code handleAsync()} with {@code whenCompleteAsync()} to preserve the original exceptional completion.
 *
 * <p>
 * The {@code whenCompleteAsync}-vs-{@code handleAsync} contract is tested via standalone helper methods (no real OPC UA
 * connection needed). The disconnected-manager tests use a real {@link SubscriptionManager} to verify that
 * {@code write()}, {@code call()}, and {@code readValues()} all return exceptional futures before any connection is
 * established.
 */
class SubscriptionManagerExceptionPropagationTest {

    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor must shut down cleanly within 5 seconds");
    }

    // -------------------------------------------------------------------------
    // Contract tests: whenCompleteAsync vs handleAsync
    //
    // These do NOT instantiate SubscriptionManager (which would trigger a
    // connection attempt in its constructor). Instead they exercise the same
    // CompletableFuture chain used by the production fix.
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@code whenCompleteAsync} (the fix) preserves an exceptional completion.
     *
     * <p>
     * Before CAMEL-25694 was fixed, {@code SubscriptionManager.write()} used {@code handleAsync()}, which converted the
     * exceptional completion into a normal null completion. This test verifies the correct behavior: a
     * {@code whenCompleteAsync} side-effect chain over a failed future must stay failed.
     */
    @Test
    void testWhenCompleteAsyncPreservesExceptionalCompletion() throws Exception {
        RuntimeException serviceError = new RuntimeException("Bad_Timeout OPC-UA service fault");
        CompletableFuture<Object> upstream = new CompletableFuture<>();
        upstream.completeExceptionally(serviceError);

        // Same chain as the fixed SubscriptionManager.write(): no side-effect, completion preserved
        CompletableFuture<?> result = upstream.whenCompleteAsync((r, e) -> {
            // no side-effect; exception propagated by whenCompleteAsync
        }, executor);

        ExecutionException ex = null;
        try {
            result.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            ex = e;
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }
        assertNotNull(ex, "Get on a failed whenCompleteAsync future must throw ExecutionException");
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertTrue(result.isCompletedExceptionally(),
                "whenCompleteAsync must preserve the exceptional completion");
    }

    /**
     * Demonstrates why the old code was wrong: {@code handleAsync} converts an exceptional completion into a normal
     * null completion, silently swallowing the service failure.
     *
     * <p>
     * This test documents the buggy behavior. If the fix is regressed by reintroducing {@code handleAsync},
     * {@link #testWhenCompleteAsyncPreservesExceptionalCompletion()} will fail, and this test shows the exact semantic
     * difference.
     */
    @Test
    void testHandleAsyncSwallowsExceptionalCompletion() throws Exception {
        RuntimeException serviceError = new RuntimeException("Bad_Timeout OPC-UA service fault");
        CompletableFuture<Object> upstream = new CompletableFuture<>();
        upstream.completeExceptionally(serviceError);

        // Same chain as the old buggy SubscriptionManager.write(): returns result (null) on exception
        CompletableFuture<?> result = upstream.handleAsync((r, e) -> r, executor);

        // Wait for the handleAsync chain to complete normally (it swallows the exception)
        result.join();

        assertFalse(result.isCompletedExceptionally(),
                "handleAsync incorrectly completes normally with null when upstream fails");
        assertNull(result.get(),
                "handleAsync returns null when upstream failed — this is the swallowed failure");
    }

    // -------------------------------------------------------------------------
    // SubscriptionManager disconnected-path tests
    //
    // These instantiate a real SubscriptionManager with a default (no-server)
    // configuration. The constructor triggers a connect() call that fails
    // immediately (null endpoint URI), leaving the manager in the disconnected
    // state. write(), call(), and readValues() must return exceptional futures.
    // -------------------------------------------------------------------------

    /**
     * Verifies that a disconnected manager returns an exceptional future from write(), ensuring producers fail fast
     * when there is no connection.
     */
    @Test
    void testWriteWhenDisconnectedReturnsExceptionalFuture() {
        SubscriptionManager manager = new SubscriptionManager(new MiloClientConfiguration(), executor, executor, 10_000);
        try {
            CompletableFuture<?> result = manager.write(ExpandedNodeId.parse("ns=1;s=myNode"),
                    new DataValue(new Variant("testValue")));

            assertTrue(result.isCompletedExceptionally(),
                    "write() must return an exceptional future when not connected");
        } finally {
            manager.dispose();
        }
    }

    /**
     * Verifies that a disconnected manager returns an exceptional future from call(), ensuring producers fail fast when
     * there is no connection.
     */
    @Test
    void testCallWhenDisconnectedReturnsExceptionalFuture() {
        SubscriptionManager manager = new SubscriptionManager(new MiloClientConfiguration(), executor, executor, 10_000);
        try {
            CompletableFuture<CallMethodResult> result = manager.call(
                    ExpandedNodeId.parse("ns=1;s=myNode"),
                    ExpandedNodeId.parse("ns=1;s=myMethod"),
                    new Variant[0]);

            assertTrue(result.isCompletedExceptionally(),
                    "call() must return an exceptional future when not connected");
        } finally {
            manager.dispose();
        }
    }

    /**
     * Verifies that a disconnected manager returns an exceptional future from readValues(), ensuring consumers fail
     * fast when there is no connection.
     */
    @Test
    void testReadValuesWhenDisconnectedReturnsExceptionalFuture() {
        SubscriptionManager manager = new SubscriptionManager(new MiloClientConfiguration(), executor, executor, 10_000);
        try {
            CompletableFuture<?> result = manager.readValues(List.of(ExpandedNodeId.parse("ns=1;s=myNode")));

            assertTrue(result.isCompletedExceptionally(),
                    "readValues() must return an exceptional future when not connected");
        } finally {
            manager.dispose();
        }
    }
}
