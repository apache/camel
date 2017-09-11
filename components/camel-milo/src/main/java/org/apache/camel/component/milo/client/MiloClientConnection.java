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
package org.apache.camel.component.milo.client;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

import org.apache.camel.component.milo.NamespaceId;
import org.apache.camel.component.milo.PartialNodeId;
import org.apache.camel.component.milo.client.internal.SubscriptionManager;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public class MiloClientConnection implements AutoCloseable {

    private final MiloClientConfiguration configuration;

    private SubscriptionManager manager;

    private boolean initialized;

    private final OpcUaClientConfigBuilder clientConfiguration;

    public MiloClientConnection(final MiloClientConfiguration configuration, final OpcUaClientConfigBuilder clientConfiguration) {
        requireNonNull(configuration);

        // make a copy since the configuration is mutable
        this.configuration = configuration.clone();
        this.clientConfiguration = clientConfiguration;
    }

    protected void init() throws Exception {
        this.manager = new SubscriptionManager(this.configuration, this.clientConfiguration, Stack.sharedScheduledExecutor(), 10_000);
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

    public MonitorHandle monitorValue(final MiloClientItemConfiguration configuration, final Consumer<DataValue> valueConsumer) {

        requireNonNull(configuration);
        requireNonNull(valueConsumer);

        checkInit();

        final NamespaceId namespaceId = configuration.makeNamespaceId();
        final PartialNodeId partialNodeId = configuration.makePartialNodeId();

        final UInteger handle = this.manager.registerItem(namespaceId, partialNodeId, configuration.getSamplingInterval(), valueConsumer);

        return () -> MiloClientConnection.this.manager.unregisterItem(handle);
    }

    public String getConnectionId() {
        return this.configuration.toCacheId();
    }

    public void writeValue(final NamespaceId namespaceId, final PartialNodeId partialNodeId, final Object value, final boolean await) {
        checkInit();

        this.manager.write(namespaceId, partialNodeId, mapValue(value), await);
    }

    /**
     * Map the incoming value to some value writable to the milo client
     *
     * @param value the incoming value
     * @return the outgoing value
     */
    private DataValue mapValue(final Object value) {
        if (value instanceof DataValue) {
            return (DataValue)value;
        }
        if (value instanceof Variant) {
            return new DataValue((Variant)value, StatusCode.GOOD, null, null);
        }
        return new DataValue(new Variant(value), StatusCode.GOOD, null, null);
    }

}