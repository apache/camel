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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.milo.client.internal.SubscriptionManager;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;

import static java.util.Objects.requireNonNull;

public class MiloClientConnection implements AutoCloseable, CamelContextAware {

    private final Lock lock = new ReentrantLock();
    private final MiloClientConfiguration configuration;
    private volatile CamelContext camelContext;
    private ExecutorService executor;
    private ScheduledExecutorService reconnectExecutor;
    private SubscriptionManager manager;
    private volatile boolean initialized;

    public MiloClientConnection(final MiloClientConfiguration configuration) {
        requireNonNull(configuration);

        // make a copy since the configuration is mutable
        this.configuration = configuration.clone();
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(final CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Returns the underlying Eclipse Milo {@link OpcUaClient} backing this connection.
     * <p>
     * This is intended for advanced scenarios that the component does not cover directly, most notably encoding and
     * decoding of custom (server-defined) OPC UA data types: the returned client exposes the
     * {@link OpcUaClient#getStaticEncodingContext() static} and {@link OpcUaClient#getDynamicEncodingContext() dynamic}
     * encoding contexts required to encode/decode {@code ExtensionObject} values.
     * <p>
     * Notes:
     * <ul>
     * <li>The connection is established lazily and asynchronously, so this method may return {@code null} until the
     * client is connected, and again briefly while a connection is being re-established.</li>
     * <li>The client is owned and managed by Camel. Callers must not connect, disconnect or otherwise alter its
     * lifecycle. A reconnect replaces the instance, so fetch it on demand rather than caching the reference.</li>
     * </ul>
     *
     * @return the active milo client, or {@code null} if the connection is not currently established
     */
    public OpcUaClient getOpcUaClient() {
        checkInit();
        return this.manager.getOpcUaClient();
    }

    protected void init() {
        ObjectHelper.notNull(this.camelContext, "camelContext");

        // A failed request disconnects the client, which can wait for a reconnect attempt that Milo schedules on its shared
        // scheduled executor, so this connection handles its requests and reconnects on executors of its own.
        final ExecutorServiceManager executorServiceManager = this.camelContext.getExecutorServiceManager();
        final ThreadPoolProfile profile = new ThreadPoolProfile("MiloClient");
        // as many threads as that executor, but they end when they are idle; with virtual threads and more than one
        // processor, a virtual thread for each task. Not more platform threads, because when the connection is lost,
        // every request in flight fails and waits for the disconnect of the first failure. With one processor this is
        // one thread, so a route that continues on it after an awaited request must not wait for another request of
        // this connection.
        final int threads = Runtime.getRuntime().availableProcessors();
        profile.setPoolSize(threads);
        profile.setMaxPoolSize(threads);
        // not bounded, so that no request outcome is rejected while the connection is open; a size of -1 would give a
        // SynchronousQueue, which rejects a task while all the threads are busy
        profile.setMaxQueueSize(Integer.MAX_VALUE);
        // a request that completes after the connection is closed fails instead of being dropped
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        this.executor = executorServiceManager.newThreadPool(this, "MiloClient", profile);
        // the reconnects of the connection run one after the other, on a thread that starts with the first one and ends
        // when the connection is closed
        this.reconnectExecutor = executorServiceManager.newSingleThreadScheduledExecutor(this, "MiloClientReconnect");

        this.manager = new SubscriptionManager(this.configuration, this.reconnectExecutor, this.executor, 10_000);
    }

    @Override
    public void close() throws Exception {
        lock.lock();
        try {
            try {
                if (this.manager != null) {
                    this.manager.dispose();
                    this.manager = null;
                }
            } finally {
                try {
                    if (this.reconnectExecutor != null) {
                        this.camelContext.getExecutorServiceManager().shutdown(this.reconnectExecutor);
                        this.reconnectExecutor = null;
                    }
                } finally {
                    if (this.executor != null) {
                        this.camelContext.getExecutorServiceManager().shutdown(this.executor);
                        this.executor = null;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    protected void checkInit() {
        lock.lock();
        try {
            if (this.initialized) {
                return;
            }

            try {
                init();
            } catch (final Exception e) {
                throw new RuntimeCamelException(e);
            }
            this.initialized = true;
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    public interface MonitorHandle {
        void unregister();
    }

    public MonitorHandle monitorValue(
            final ExpandedNodeId nodeId, Double samplingInterval, Integer queueSize,
            MonitorFilterConfiguration monitorFilterConfiguration, Consumer<DataValue> valueConsumer) {

        requireNonNull(valueConsumer);

        return monitorValues(Collections.singletonMap(nodeId, valueConsumer), samplingInterval, queueSize,
                monitorFilterConfiguration);
    }

    /**
     * Monitors several nodes, each with its own value consumer. The monitored items of all nodes are created together,
     * and the returned handle deletes them together.
     */
    public MonitorHandle monitorValues(
            final Map<ExpandedNodeId, Consumer<DataValue>> valueConsumers, final Double samplingInterval,
            final Integer queueSize, final MonitorFilterConfiguration monitorFilterConfiguration) {

        requireNonNull(valueConsumers);

        checkInit();

        final List<UInteger> handles
                = this.manager.registerItems(valueConsumers, samplingInterval, queueSize, monitorFilterConfiguration);

        return () -> MiloClientConnection.this.manager.unregisterItems(handles);
    }

    public String getConnectionId() {
        return this.configuration.toCacheId();
    }

    public CompletableFuture<?> writeValue(final ExpandedNodeId nodeId, final Object value) {
        checkInit();

        return this.manager.write(nodeId, mapWriteValue(value));
    }

    public CompletableFuture<?> readValues(final List<ExpandedNodeId> nodeIds) {
        checkInit();

        return this.manager.readValues(nodeIds);
    }

    public CompletableFuture<CallMethodResult> call(
            final ExpandedNodeId nodeId, final ExpandedNodeId methodId, final Object value) {
        checkInit();

        return this.manager.call(nodeId, methodId, mapCallValue(value));
    }

    /**
     * Map the incoming value to some value callable to the milo client
     *
     * @param  value the incoming value
     * @return       the outgoing call request
     */
    private Variant[] mapCallValue(final Object value) {
        if (value == null) {
            return new Variant[0];
        }

        if (value instanceof Variant[] variants) {
            return variants;
        }
        if (value instanceof Variant variant) {
            return new Variant[] { variant };
        }

        return new Variant[] { new Variant(value) };
    }

    /**
     * Map the incoming value to some value writable to the milo client
     *
     * @param  value the incoming value
     * @return       the outgoing value
     */
    private DataValue mapWriteValue(final Object value) {
        if (value instanceof DataValue dataValue) {
            return dataValue;
        }
        if (value instanceof Variant variant) {
            return new DataValue(variant, StatusCode.GOOD, null, null);
        }
        return new DataValue(new Variant(value), StatusCode.GOOD, null, null);
    }

    public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
            final List<ExpandedNodeId> expandedNodeIds, final BrowseDirection direction, final int nodeClasses,
            final int maxDepth, String filter, boolean includeSubTypes, int maxNodesPerRequest) {
        checkInit();

        return this.manager.browse(expandedNodeIds, direction, nodeClasses, maxDepth, filter, includeSubTypes,
                maxNodesPerRequest);
    }
}
