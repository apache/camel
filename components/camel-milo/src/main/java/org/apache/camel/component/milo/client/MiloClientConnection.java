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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.camel.component.milo.client.internal.SubscriptionManager;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;

import static java.util.Objects.requireNonNull;

public class MiloClientConnection implements AutoCloseable {

    private final MiloClientConfiguration configuration;

    private SubscriptionManager manager;

    private boolean initialized;

    public MiloClientConnection(final MiloClientConfiguration configuration) {
        requireNonNull(configuration);

        // make a copy since the configuration is mutable
        this.configuration = configuration.clone();
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    protected void init() throws Exception {
        this.manager = new SubscriptionManager(this.configuration, Stack.sharedScheduledExecutor(), 10_000);
    }

    @Override
    public void close() throws Exception {
        if (this.manager != null) {
            this.manager.dispose();
            this.manager = null;
        }
    }

    protected synchronized void checkInit() {
        if (this.initialized) {
            return;
        }

        try {
            init();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        this.initialized = true;
    }

    @FunctionalInterface
    public interface MonitorHandle {
        void unregister();
    }

    public MonitorHandle monitorValue(final ExpandedNodeId nodeId, Double samplingInterval, final Consumer<DataValue> valueConsumer) {

        requireNonNull(configuration);
        requireNonNull(valueConsumer);

        checkInit();

        final UInteger handle = this.manager.registerItem(nodeId, samplingInterval, valueConsumer);

        return () -> MiloClientConnection.this.manager.unregisterItem(handle);
    }

    public String getConnectionId() {
        return this.configuration.toCacheId();
    }

    public CompletableFuture<?> writeValue(final ExpandedNodeId nodeId, final Object value) {
        checkInit();

        return this.manager.write(nodeId, mapWriteValue(value));
    }

    public CompletableFuture<CallMethodResult> call(final ExpandedNodeId nodeId, final ExpandedNodeId methodId, final Object value) {
        checkInit();

        return this.manager.call(nodeId, methodId, mapCallValue(value));
    }

    /**
     * Map the incoming value to some value callable to the milo client
     *
     * @param value the incoming value
     * @return the outgoing call request
     */
    private Variant[] mapCallValue(final Object value) {

        if (value == null) {
            return new Variant[0];
        }

        if (value instanceof Variant[]) {
            return (Variant[])value;
        }
        if (value instanceof Variant) {
            return new Variant[] {(Variant)value};
        }

        return new Variant[] {new Variant(value)};
    }

    /**
     * Map the incoming value to some value writable to the milo client
     *
     * @param value the incoming value
     * @return the outgoing value
     */
    private DataValue mapWriteValue(final Object value) {
        if (value instanceof DataValue) {
            return (DataValue)value;
        }
        if (value instanceof Variant) {
            return new DataValue((Variant)value, StatusCode.GOOD, null, null);
        }
        return new DataValue(new Variant(value), StatusCode.GOOD, null, null);
    }

}
