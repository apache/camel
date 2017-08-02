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
package org.apache.camel.component.milo.client.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.camel.component.milo.NamespaceId;
import org.apache.camel.component.milo.PartialNodeId;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.CompositeProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager.SubscriptionListener;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManager.class);

    private final AtomicLong clientHandleCounter = new AtomicLong(0);

    private final class SubscriptionListenerImpl implements SubscriptionListener {
        @Override
        public void onSubscriptionTransferFailed(final UaSubscription subscription, final StatusCode statusCode) {
            LOG.info("Transfer failed {} : {}", subscription.getSubscriptionId(), statusCode);

            // we simply tear it down and build it up again
            handleConnectionFailue(new RuntimeException("Subscription failed to reconnect"));
        }

        @Override
        public void onStatusChanged(final UaSubscription subscription, final StatusCode status) {
            LOG.info("Subscription status changed {} : {}", subscription.getSubscriptionId(), status);
        }

        @Override
        public void onPublishFailure(final UaException exception) {
        }

        @Override
        public void onNotificationDataLost(final UaSubscription subscription) {
        }

        @Override
        public void onKeepAlive(final UaSubscription subscription, final DateTime publishTime) {
        }
    }

    public interface Worker<T> {
        void work(T on) throws Exception;
    }

    private static class Subscription {
        private final NamespaceId namespaceId;
        private final PartialNodeId partialNodeId;
        private final Double samplingInterval;

        private final Consumer<DataValue> valueConsumer;

        Subscription(final NamespaceId namespaceId, final PartialNodeId partialNodeId, final Double samplingInterval, final Consumer<DataValue> valueConsumer) {
            this.namespaceId = namespaceId;
            this.partialNodeId = partialNodeId;
            this.samplingInterval = samplingInterval;
            this.valueConsumer = valueConsumer;
        }

        public NamespaceId getNamespaceId() {
            return this.namespaceId;
        }

        public PartialNodeId getPartialNodeId() {
            return this.partialNodeId;
        }

        public Double getSamplingInterval() {
            return this.samplingInterval;
        }

        public Consumer<DataValue> getValueConsumer() {
            return this.valueConsumer;
        }
    }

    private class Connected {
        private OpcUaClient client;
        private final UaSubscription manager;

        private final Map<UInteger, Subscription> badSubscriptions = new HashMap<>();

        private final Map<UInteger, UaMonitoredItem> goodSubscriptions = new HashMap<>();

        private final Map<String, UShort> namespaceCache = new ConcurrentHashMap<>();

        Connected(final OpcUaClient client, final UaSubscription manager) {
            this.client = client;
            this.manager = manager;
        }

        public void putSubscriptions(final Map<UInteger, Subscription> subscriptions) throws Exception {

            if (subscriptions.isEmpty()) {
                return;
            }

            // convert to requests

            final List<MonitoredItemCreateRequest> items = new ArrayList<>(subscriptions.size());

            for (final Map.Entry<UInteger, Subscription> entry : subscriptions.entrySet()) {
                final Subscription s = entry.getValue();

                UShort namespaceIndex;
                if (s.getNamespaceId().isNumeric()) {
                    namespaceIndex = s.getNamespaceId().getNumeric();
                } else {
                    namespaceIndex = lookupNamespace(s.getNamespaceId().getUri());
                }

                if (namespaceIndex == null) {
                    handleSubscriptionError(new StatusCode(StatusCodes.Bad_InvalidArgument), entry.getKey(), s);
                } else {
                    final NodeId nodeId = s.getPartialNodeId().toNodeId(namespaceIndex);
                    final ReadValueId itemId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
                    Double samplingInterval = s.getSamplingInterval();
                    if (samplingInterval == null) {
                        // work around a bug (NPE) in Eclipse Milo 0.1.3
                        samplingInterval = 0.0;
                    }
                    final MonitoringParameters parameters = new MonitoringParameters(entry.getKey(), samplingInterval, null, null, null);
                    items.add(new MonitoredItemCreateRequest(itemId, MonitoringMode.Reporting, parameters));
                }
            }

            if (!items.isEmpty()) {

                // create monitors

                this.manager.createMonitoredItems(TimestampsToReturn.Both, items, (item, idx) -> {

                    // set value listener

                    final Subscription s = subscriptions.get(item.getClientHandle());

                    if (item.getStatusCode().isBad()) {
                        handleSubscriptionError(item.getStatusCode(), item.getClientHandle(), s);
                    } else {
                        this.goodSubscriptions.put(item.getClientHandle(), item);
                        item.setValueConsumer(s.getValueConsumer());
                    }

                }).get();
            }

            if (!this.badSubscriptions.isEmpty()) {
                SubscriptionManager.this.executor.schedule(this::resubscribe, SubscriptionManager.this.reconnectTimeout, TimeUnit.MILLISECONDS);
            }
        }

        private void handleSubscriptionError(final StatusCode statusCode, final UInteger clientHandle, final Subscription s) {
            this.badSubscriptions.put(clientHandle, s);
            s.getValueConsumer().accept(new DataValue(statusCode));
        }

        private void resubscribe() {
            final Map<UInteger, Subscription> subscriptions = new HashMap<>(this.badSubscriptions);
            this.badSubscriptions.clear();
            try {
                putSubscriptions(subscriptions);
            } catch (final Exception e) {
                handleConnectionFailue(e);
            }
        }

        public void activate(final UInteger clientHandle, final Subscription subscription) throws Exception {
            putSubscriptions(Collections.singletonMap(clientHandle, subscription));
        }

        public void deactivate(final UInteger clientHandle) throws Exception {
            final UaMonitoredItem item = this.goodSubscriptions.remove(clientHandle);
            if (item != null) {
                this.manager.deleteMonitoredItems(Collections.singletonList(item)).get();
            } else {
                this.badSubscriptions.remove(clientHandle);
            }
        }

        private UShort lookupNamespace(final String namespaceUri) throws Exception {
            return lookupNamespaceIndex(namespaceUri).get();
        }

        private CompletableFuture<UShort> lookupNamespaceIndex(final String namespaceUri) {

            LOG.trace("Looking up namespace: {}", namespaceUri);

            // check cache
            {
                final UShort result = this.namespaceCache.get(namespaceUri);
                if (result != null) {
                    LOG.trace("Found namespace in cache: {} -> {}", namespaceUri, result);
                    return CompletableFuture.completedFuture(result);
                }
            }

            /*
             * We always read the server side table since the cache did not help
             * us and the namespace might have been added to the server at a
             * later time.
             */

            LOG.debug("Looking up namespace on server: {}", namespaceUri);

            final CompletableFuture<DataValue> future = this.client.readValue(0, TimestampsToReturn.Neither, Identifiers.Server_NamespaceArray);

            return future.thenApply(value -> {
                final Object rawValue = value.getValue().getValue();

                if (rawValue instanceof String[]) {
                    final String[] namespaces = (String[])rawValue;
                    for (int i = 0; i < namespaces.length; i++) {
                        if (namespaces[i].equals(namespaceUri)) {
                            final UShort result = Unsigned.ushort(i);
                            this.namespaceCache.putIfAbsent(namespaceUri, result);
                            return result;
                        }
                    }
                }
                return null;
            });
        }

        public void dispose() {
            if (this.client != null) {
                this.client.disconnect();
                this.client = null;
            }
        }

        public CompletableFuture<StatusCode> write(final NamespaceId namespaceId, final PartialNodeId partialNodeId, final DataValue value) {

            final CompletableFuture<UShort> future;

            LOG.trace("Namespace: {}", namespaceId);
            if (namespaceId.isNumeric()) {
                LOG.trace("Using provided index: {}", namespaceId.getNumeric());
                future = CompletableFuture.completedFuture(namespaceId.getNumeric());
            } else {
                LOG.trace("Looking up namespace: {}", namespaceId.getUri());
                future = lookupNamespaceIndex(namespaceId.getUri());
            }

            return future.thenCompose(index -> {

                final NodeId nodeId = partialNodeId.toNodeId(index);
                LOG.debug("Node - partial: {}, full: {}", partialNodeId, nodeId);

                return this.client.writeValue(nodeId, value).whenComplete((status, error) -> {
                    if (status != null) {
                        LOG.debug("Write to ns={}/{}, id={} = {} -> {}", namespaceId, index, nodeId, value, status);
                    } else {
                        LOG.debug("Failed to write", error);
                    }
                });

            });
        }

    }

    private final MiloClientConfiguration configuration;
    private final OpcUaClientConfigBuilder clientBuilder;
    private final ScheduledExecutorService executor;
    private final long reconnectTimeout;

    private Connected connected;
    private boolean disposed;
    private Future<?> reconnectJob;
    private final Map<UInteger, Subscription> subscriptions = new HashMap<>();

    public SubscriptionManager(final MiloClientConfiguration configuration, final OpcUaClientConfigBuilder clientBuilder, final ScheduledExecutorService executor,
                               final long reconnectTimeout) {

        this.configuration = configuration;
        this.clientBuilder = clientBuilder;
        this.executor = executor;
        this.reconnectTimeout = reconnectTimeout;

        connect();
    }

    private synchronized void handleConnectionFailue(final Throwable e) {
        if (this.connected != null) {
            this.connected.dispose();
            this.connected = null;
        }

        // log

        LOG.info("Connection failed", e);

        // always trigger re-connect

        triggerReconnect(true);
    }

    private void connect() {
        LOG.info("Starting connect");

        synchronized (this) {
            this.reconnectJob = null;

            if (this.disposed) {
                // we woke up disposed
                return;
            }
        }

        performAndEvalConnect();
    }

    private void performAndEvalConnect() {
        try {
            final Connected connected = performConnect();
            LOG.debug("Connect call done");
            synchronized (this) {
                if (this.disposed) {
                    // we got disposed during connect
                    return;
                }

                try {
                    LOG.debug("Setting subscriptions: {}", this.subscriptions.size());
                    connected.putSubscriptions(this.subscriptions);

                    LOG.debug("Update state : {} -> {}", this.connected, connected);
                    final Connected oldConnected = this.connected;
                    this.connected = connected;

                    if (oldConnected != null) {
                        LOG.debug("Dispose old state");
                        oldConnected.dispose();
                    }

                } catch (final Exception e) {
                    LOG.info("Failed to set subscriptions", e);
                    connected.dispose();
                    throw e;
                }
            }
        } catch (final Exception e) {
            LOG.info("Failed to connect", e);
            triggerReconnect(false);
        }
    }

    private Connected performConnect() throws Exception {
        final EndpointDescription endpoint = UaTcpStackClient.getEndpoints(this.configuration.getEndpointUri()).thenApply(endpoints -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found enpoints:");
                for (final EndpointDescription ep : endpoints) {
                    LOG.debug("\t{}", ep);
                }
            }

            return findEndpoint(endpoints);
        }).get();

        LOG.debug("Selected endpoint: {}", endpoint);

        final URI uri = URI.create(this.configuration.getEndpointUri());

        // set identity providers

        final List<IdentityProvider> providers = new LinkedList<>();

        final String user = uri.getUserInfo();
        if (user != null && !user.isEmpty()) {
            final String[] creds = user.split(":", 2);
            if (creds != null && creds.length == 2) {
                LOG.debug("Enable username/password provider: {}", creds[0]);
            }
            providers.add(new UsernameProvider(creds[0], creds[1]));
        }

        // FIXME: need a way to clone
        final OpcUaClientConfigBuilder cfg = this.clientBuilder;

        providers.add(new AnonymousProvider());
        cfg.setIdentityProvider(new CompositeProvider(providers));

        // set endpoint

        cfg.setEndpoint(endpoint);

        final OpcUaClient client = new OpcUaClient(cfg.build());

        try {
            final UaSubscription manager = client.getSubscriptionManager().createSubscription(1_000.0).get();
            client.getSubscriptionManager().addSubscriptionListener(new SubscriptionListenerImpl());

            return new Connected(client, manager);
        } catch (final Throwable e) {
            if (client != null) {
                // clean up
                client.disconnect();
            }
            throw e;
        }
    }

    public void dispose() {
        Connected connected;

        synchronized (this) {
            if (this.disposed) {
                return;
            }
            this.disposed = true;
            connected = this.connected;
        }

        if (connected != null) {
            // dispose outside of lock
            connected.dispose();
        }
    }

    private synchronized void triggerReconnect(final boolean immediate) {
        LOG.info("Trigger re-connect (immediate: {})", immediate);

        if (this.reconnectJob != null) {
            LOG.info("Re-connect already scheduled");
            return;
        }

        if (immediate) {
            this.reconnectJob = this.executor.submit(this::connect);
        } else {
            this.reconnectJob = this.executor.schedule(this::connect, this.reconnectTimeout, TimeUnit.MILLISECONDS);
        }
    }

    private EndpointDescription findEndpoint(final EndpointDescription[] endpoints) {
        EndpointDescription best = null;
        for (final EndpointDescription ep : endpoints) {
            if (best == null || ep.getSecurityLevel().compareTo(best.getSecurityLevel()) > 0) {
                best = ep;
            }
        }
        return best;
    }

    protected synchronized void whenConnected(final Worker<Connected> worker) {
        if (this.connected != null) {
            try {
                worker.work(this.connected);
            } catch (final Exception e) {
                handleConnectionFailue(e);
            }
        }
    }

    public UInteger registerItem(final NamespaceId namespaceId, final PartialNodeId partialNodeId, final Double samplingInterval, final Consumer<DataValue> valueConsumer) {

        final UInteger clientHandle = Unsigned.uint(this.clientHandleCounter.incrementAndGet());
        final Subscription subscription = new Subscription(namespaceId, partialNodeId, samplingInterval, valueConsumer);

        synchronized (this) {
            this.subscriptions.put(clientHandle, subscription);

            whenConnected(connected -> {
                connected.activate(clientHandle, subscription);
            });
        }

        return clientHandle;
    }

    public synchronized void unregisterItem(final UInteger clientHandle) {
        if (this.subscriptions.remove(clientHandle) != null) {
            whenConnected(connected -> {
                connected.deactivate(clientHandle);
            });
        }
    }

    public void write(final NamespaceId namespaceId, final PartialNodeId partialNodeId, final DataValue value, final boolean await) {
        CompletableFuture<Object> future = null;

        synchronized (this) {
            if (this.connected != null) {
                future = this.connected.write(namespaceId, partialNodeId, value).handleAsync((status, e) -> {
                    // handle outside the lock, running using
                    // handleAsync
                    if (e != null) {
                        handleConnectionFailue(e);
                    }
                    return null;
                }, this.executor);
            }
        }

        if (await && future != null) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                // should never happen since our previous handler should not
                // fail
                LOG.warn("Failed to wait for completion", e);
            }
        }
    }

}
