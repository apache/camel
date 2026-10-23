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
package org.apache.camel.component.milo.client;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying that OPC-UA service failures from awaited write and call operations are propagated as exchange
 * exceptions rather than being silently swallowed and reported as success.
 *
 * <p>
 * Regression test for CAMEL-25694: when {@code SubscriptionManager.write()} or {@code call()} received an exceptional
 * completion from the underlying future (e.g. a Bad_Timeout service fault) and used {@code handleAsync()}, it converted
 * the failure into a normal completion with a null value. {@code MiloClientProducer} then reported success with a null
 * body. The fix replaces {@code handleAsync()} with {@code whenCompleteAsync()} to preserve the original exceptional
 * completion while still triggering the reconnection side-effect.
 */
class MiloClientProducerWriteFailureTest {

    /**
     * Stub connection that returns a pre-arranged future from {@code writeValue()} and {@code call()}, allowing tests
     * to simulate service failures without a real OPC UA server.
     */
    static class StubMiloClientConnection extends MiloClientConnection {

        private final CompletableFuture<?> writeResult;
        private final CompletableFuture<CallMethodResult> callResult;

        StubMiloClientConnection(CompletableFuture<?> writeResult, CompletableFuture<CallMethodResult> callResult) {
            super(new MiloClientConfiguration());
            this.writeResult = writeResult;
            this.callResult = callResult;
        }

        @Override
        public CompletableFuture<?> writeValue(ExpandedNodeId nodeId, Object value) {
            return writeResult;
        }

        @Override
        public CompletableFuture<CallMethodResult> call(ExpandedNodeId nodeId, ExpandedNodeId methodId, Object value) {
            return callResult;
        }

        @Override
        public CompletableFuture<?> readValues(List<ExpandedNodeId> nodeIds) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
                List<ExpandedNodeId> expandedNodeIds, BrowseDirection direction, int nodeClasses, int maxDepth,
                String filter, boolean includeSubTypes, int maxNodesPerRequest) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    /**
     * Verifies that when the awaited write future completes exceptionally, the exchange receives the exception rather
     * than reporting success with a null body.
     */
    @Test
    void testAwaitedWriteServiceFailurePropagatesAsException() throws Exception {
        RuntimeException serviceError = new RuntimeException("Bad_Timeout OPC-UA service fault");
        CompletableFuture<StatusCode> failingWrite = new CompletableFuture<>();
        failingWrite.completeExceptionally(serviceError);

        Exchange exchange = runProducerWithStubConnection(
                new StubMiloClientConnection(failingWrite, CompletableFuture.completedFuture(null)),
                null, true);

        assertNotNull(exchange.getException(), "Exchange must carry the service exception on failed awaited write");
        assertInstanceOf(RuntimeException.class, exchange.getException());
    }

    /**
     * Verifies that when the awaited call future completes exceptionally, the exchange receives the exception rather
     * than reporting success with a null body.
     */
    @Test
    void testAwaitedCallServiceFailurePropagatesAsException() throws Exception {
        RuntimeException serviceError = new RuntimeException("Bad_Timeout OPC-UA service fault");
        CompletableFuture<CallMethodResult> failingCall = new CompletableFuture<>();
        failingCall.completeExceptionally(serviceError);

        Exchange exchange = runProducerWithStubConnection(
                new StubMiloClientConnection(CompletableFuture.completedFuture(null), failingCall),
                "ns=1;s=myMethod", true);

        assertNotNull(exchange.getException(), "Exchange must carry the service exception on failed awaited call");
        assertInstanceOf(RuntimeException.class, exchange.getException());
    }

    /**
     * Verifies that a successful awaited write does not set an exception on the exchange.
     */
    @Test
    void testAwaitedWriteSuccessDoesNotSetException() throws Exception {
        CompletableFuture<StatusCode> successWrite = CompletableFuture.completedFuture(StatusCode.GOOD);

        Exchange exchange = runProducerWithStubConnection(
                new StubMiloClientConnection(successWrite, CompletableFuture.completedFuture(null)),
                null, true);

        assertNull(exchange.getException(), "Exchange must not carry an exception on successful awaited write");
    }

    /**
     * Verifies that non-awaited writes (fire-and-forget mode) do not propagate the exception to the exchange.
     */
    @Test
    void testNonAwaitedWriteServiceFailureDoesNotSetException() throws Exception {
        RuntimeException serviceError = new RuntimeException("Bad_Timeout OPC-UA service fault");
        CompletableFuture<StatusCode> failingWrite = new CompletableFuture<>();
        failingWrite.completeExceptionally(serviceError);

        Exchange exchange = runProducerWithStubConnection(
                new StubMiloClientConnection(failingWrite, CompletableFuture.completedFuture(null)),
                null, false);

        assertNull(exchange.getException(), "Non-awaited write must not propagate exceptions to the exchange");
    }

    /**
     * Creates a {@link MiloClientProducer} backed by the given stub connection, runs process() synchronously, and
     * returns the resulting exchange.
     *
     * @param stubConnection the stub connection returning pre-arranged futures
     * @param methodIdStr    if non-null, treated as a method-call producer; otherwise a write producer
     * @param await          whether to set {@code CamelMiloAwait=true} on the exchange
     */
    private Exchange runProducerWithStubConnection(
            StubMiloClientConnection stubConnection,
            String methodIdStr,
            boolean await)
            throws Exception {

        MiloClientEndpoint endpoint = new MiloClientEndpoint(
                "milo-client:opc.tcp://localhost:4840", new MiloClientComponent(), "opc.tcp://localhost:4840",
                new MiloClientCachingConnectionManager());
        endpoint.setCamelContext(camelContext);
        endpoint.setNode("ns=1;s=myNode");
        if (methodIdStr != null) {
            endpoint.setMethod(methodIdStr);
        }

        MiloClientProducer producer = new MiloClientProducer(endpoint, await);

        // Inject the stub connection via reflection to bypass doStart() which requires a real server
        Field connectionField = MiloClientProducer.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(producer, stubConnection);

        DefaultExchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody("testValue");
        exchange.getIn().setHeader(MiloConstants.HEADER_AWAIT, await);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exchange> resultRef = new AtomicReference<>();

        producer.process(exchange, done -> {
            resultRef.set(exchange);
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(latch.getCount() == 0, "Producer callback must complete within 5 seconds");
        return resultRef.get();
    }
}
