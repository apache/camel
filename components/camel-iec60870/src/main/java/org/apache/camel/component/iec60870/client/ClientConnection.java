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
package org.apache.camel.component.iec60870.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.channel.ChannelHandlerContext;
import org.apache.camel.component.iec60870.DiscardAckModule;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.ASDUHeader;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.ReadCommand;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.ASDUAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.CauseOfTransmission;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.QualifierOfInterrogation;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.StandardCause;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.Value;
import org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient;
import org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient.ModulesFactory;
import org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient.State;
import org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient.StateListener;
import org.eclipse.neoscada.protocol.iec60870.client.data.AbstractDataProcessor;
import org.eclipse.neoscada.protocol.iec60870.client.data.DataHandler;
import org.eclipse.neoscada.protocol.iec60870.client.data.DataModule;
import org.eclipse.neoscada.protocol.iec60870.client.data.DataModuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientConnection {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConnection.class);

    /**
     * Listener for value updates from the IEC 60870 server.
     */
    @FunctionalInterface
    public interface ValueListener {
        void update(ObjectAddress address, Value<?> value);
    }

    /**
     * Listener for connection state changes.
     */
    @FunctionalInterface
    public interface ConnectionStateListener {
        /**
         * Called when the connection state changes.
         *
         * @param state the new connection state
         * @param error an optional error if the state change was due to an error, or null otherwise
         */
        void stateChanged(State state, Throwable error);
    }

    // Connection state tracking
    private final AtomicReference<State> currentState = new AtomicReference<>(State.DISCONNECTED);
    private final AtomicLong connectedTimestamp = new AtomicLong(0);
    private final AtomicReference<DataModuleContext> dataModuleContextRef = new AtomicReference<>();
    private final List<ConnectionStateListener> connectionStateListeners = new CopyOnWriteArrayList<>();

    private final DataHandler dataHandler = new AbstractDataProcessor() {

        /**
         * Called when the connection was established
         */
        @Override
        public void activated(final DataModuleContext dataModuleContext, final ChannelHandlerContext ctx) {
            // Store the context for later use (interrogation, read commands)
            dataModuleContextRef.set(dataModuleContext);
            dataModuleContext.requestStartData();
            dataModuleContext.startInterrogation(ASDUAddress.BROADCAST, QualifierOfInterrogation.GLOBAL);
        }

        /**
         * Called when the start data was accepted
         */
        @Override
        public void started() {
        }

        /**
         * Called when the connection broke
         */
        @Override
        public void disconnected() {
            // Clear the context reference as it's no longer valid
            dataModuleContextRef.set(null);
        }

        @Override
        protected void fireEntry(final ASDUAddress asduAddress, final InformationObjectAddress address, final Value<?> value) {
            ClientConnection.this.handleData(ObjectAddress.valueOf(asduAddress, address), value);
        }
    };

    private final Lock lock = new ReentrantLock();
    private final Map<ObjectAddress, Value<?>> lastValue = new HashMap<>();
    private final Map<ObjectAddress, ValueListener> listeners = new HashMap<>();

    private final String host;
    private final int port;
    private final ClientOptions options;

    private AutoConnectClient client;

    public ClientConnection(final String host, final int port, final ClientOptions options) {
        this.host = host;
        this.port = port;
        this.options = options;
    }

    public void start() {
        final DataModule dataModule = new DataModule(this.dataHandler, this.options.getDataModuleOptions());
        final ModulesFactory factory = () -> Arrays.asList(dataModule, new DiscardAckModule());
        final CountDownLatch latch = new CountDownLatch(1);

        StateListener stateListener = (final State state, final Throwable error) -> {
            State previousState = currentState.getAndSet(state);

            // Track connection time
            if (state == State.CONNECTED && previousState != State.CONNECTED) {
                connectedTimestamp.set(System.currentTimeMillis());
                latch.countDown();
            } else if (state != State.CONNECTED && previousState == State.CONNECTED) {
                // Reset connected timestamp when disconnected
                connectedTimestamp.set(0);
            }

            // Notify all registered connection state listeners
            notifyConnectionStateListeners(state, error);
        };

        this.client
                = new AutoConnectClient(this.host, this.port, this.options.getProtocolOptions(), factory, stateListener);
        try {
            latch.await(this.options.getConnectionTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void notifyConnectionStateListeners(State state, Throwable error) {
        for (ConnectionStateListener listener : connectionStateListeners) {
            try {
                listener.stateChanged(state, error);
            } catch (Exception e) {
                LOG.warn("Error notifying connection state listener", e);
            }
        }
    }

    public void stop() {
        this.client.close();
    }

    protected void handleData(final ObjectAddress address, final Value<?> value) {
        lock.lock();
        try {
            this.lastValue.put(address, value);
            final ValueListener listener = this.listeners.get(address);
            if (listener != null) {
                listener.update(address, value);
            }
        } finally {
            lock.unlock();
        }
    }

    public void setListener(final ObjectAddress address, final ValueListener listener) {
        lock.lock();
        try {
            if (listener != null) {
                this.listeners.put(address, listener);
                final Value<?> last = this.lastValue.get(address);
                if (last != null) {
                    listener.update(address, last);
                }
            } else {
                this.listeners.remove(address);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean executeCommand(final Object command) {
        return this.client.writeCommand(command);
    }

    /**
     * Adds a connection state listener to receive notifications about connection state changes.
     *
     * @param listener the listener to add
     */
    public void addConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            connectionStateListeners.add(listener);
            // Notify immediately with current state
            listener.stateChanged(currentState.get(), null);
        }
    }

    /**
     * Removes a connection state listener.
     *
     * @param listener the listener to remove
     */
    public void removeConnectionStateListener(ConnectionStateListener listener) {
        if (listener != null) {
            connectionStateListeners.remove(listener);
        }
    }

    /**
     * Gets the current connection state.
     *
     * @return the current connection state
     */
    public State getConnectionState() {
        return currentState.get();
    }

    /**
     * Checks if the client is currently connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return currentState.get() == State.CONNECTED;
    }

    /**
     * Gets the connection uptime in milliseconds since the client was last connected. Returns 0 if not currently
     * connected.
     *
     * @return the uptime in milliseconds, or 0 if not connected
     */
    public long getConnectionUptime() {
        long timestamp = connectedTimestamp.get();
        if (timestamp == 0 || currentState.get() != State.CONNECTED) {
            return 0;
        }
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Starts a global interrogation command (C_IC_NA_1) to the broadcast address. This requests all data points from
     * the server.
     *
     * @return true if the command was sent successfully, false otherwise
     */
    public boolean startInterrogation() {
        return startInterrogation(ASDUAddress.BROADCAST, QualifierOfInterrogation.GLOBAL);
    }

    public boolean startInterrogation(ASDUAddress asduAddress) {
        return startInterrogation(asduAddress, QualifierOfInterrogation.GLOBAL);
    }

    public boolean startInterrogation(ASDUAddress asduAddress, short qoi) {
        DataModuleContext context = dataModuleContextRef.get();
        if (context == null) {
            LOG.warn("Cannot start interrogation: not connected or data module not initialized");
            return false;
        }
        try {
            context.startInterrogation(asduAddress, qoi);
            LOG.debug("Started interrogation for ASDU address {} with QOI {}", asduAddress, qoi);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to start interrogation", e);
            return false;
        }
    }

    public boolean startGroupInterrogation(ASDUAddress asduAddress, int group) {
        if (group < 1 || group > 16) {
            throw new IllegalArgumentException("Group must be between 1 and 16, was: " + group);
        }
        // Group 1 = QOI 21, Group 2 = QOI 22, etc.
        short qoiValue = (short) (20 + group);
        return startInterrogation(asduAddress, qoiValue);
    }

    public boolean readValue(ASDUAddress asduAddress, InformationObjectAddress ioa) {
        DataModuleContext context = dataModuleContextRef.get();
        if (context == null) {
            LOG.warn("Cannot send read command: not connected or data module not initialized");
            return false;
        }
        try {
            ASDUHeader header = new ASDUHeader(
                    new CauseOfTransmission(StandardCause.REQUEST),
                    asduAddress);
            ReadCommand readCommand = new ReadCommand(header, ioa);

            client.writeCommand(readCommand);

            LOG.debug("Sent read command for ASDU address {} IOA {}", asduAddress, ioa);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to send read command", e);
            return false;
        }
    }

    public boolean readValue(ObjectAddress address) {
        return readValue(address.getASDUAddress(), address.getInformationObjectAddress());
    }
}
