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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.apache.camel.component.milo.client.MonitorFilterConfiguration;
import org.eclipse.milo.opcua.sdk.client.DiscoveryClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.UaSession;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.CompositeProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.subscriptions.MonitoredItemServiceOperationResult;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.DiagnosticInfo;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.shaded.com.google.common.base.Strings;
import org.eclipse.milo.shaded.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.camel.component.milo.NodeIds.toNodeId;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class SubscriptionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManager.class);

    /**
     * The status codes of a request that failed because the connection or the session was lost, which the session
     * recovery of the client deals with: a closed connection, and the service faults on which the client leaves the
     * active session because its session or its secure channel is no longer valid. The faults are those of
     * {@code SESSION_ERROR} and {@code SECURE_CHANNEL_ERROR} in the session state machine of Milo 1.1
     * ({@code SessionFsmFactory}).
     */
    private static final Set<Long> SESSION_LOST = Set.of(
            StatusCodes.Bad_ConnectionClosed, StatusCodes.Bad_SessionClosed, StatusCodes.Bad_SessionIdInvalid,
            StatusCodes.Bad_SessionNotActivated, StatusCodes.Bad_SecureChannelIdInvalid,
            StatusCodes.Bad_SecurityChecksFailed, StatusCodes.Bad_TcpSecureChannelUnknown,
            StatusCodes.Bad_RequestTypeInvalid);

    /**
     * The status of a monitored node while communication with the server is lost: the source of the value is configured
     * but cannot be reached, and no last value is kept (OPC 10000-8 Table 61, OPC 10000-14 Table 80).
     */
    private static final StatusCode NO_COMMUNICATION = new StatusCode(StatusCodes.Bad_NoCommunication);

    private final AtomicLong clientHandleCounter = new AtomicLong();

    /**
     * The generation of the last connection built, zero before the first.
     */
    private final AtomicLong generationCounter = new AtomicLong();

    /**
     * Listens to the subscription of a connection and to the state of its session. It is registered on the client
     * before the client connects, so that it sees every change of the session state.
     */
    private final class SubscriptionListenerImpl
            implements OpcUaSubscription.SubscriptionListener, SessionActivityListener {

        /**
         * The generation of the connection, which orders its values and loss reports against those of the connection
         * that replaces it.
         */
        private final long generation;

        /**
         * Whether the session is active. The client connects before the connection is used, and Milo dispatches the
         * activity callbacks in the order of the transitions.
         */
        private volatile boolean sessionActive = true;

        /**
         * The subscription of the connection, once it is created.
         */
        private volatile OpcUaSubscription subscription;

        /**
         * Set once the connection has been disposed: the session the client closes then is no loss of communication.
         */
        private volatile boolean closed;

        /**
         * The session that the client lost last, while it has not been activated again.
         */
        private volatile NodeId lostSessionId;

        SubscriptionListenerImpl(final long generation) {
            this.generation = generation;
        }

        @Override
        public void onSessionActive(final UaSession session) {
            this.sessionActive = true;
            final NodeId lostSessionId = this.lostSessionId;
            this.lostSessionId = null;
            final OpcUaSubscription subscription = this.subscription;
            if (lostSessionId != null && subscription != null && !this.closed) {
                if (lostSessionId.equals(session.getSessionId())) {
                    // the same session again: the server sends only changes, so ask it for every value
                    resendData(subscription, this);
                } else {
                    LOG.debug("Session {} replaced by {}, the server sends the current values", lostSessionId,
                            session.getSessionId());
                }
            }
        }

        @Override
        public void onSessionInactive(final UaSession session) {
            this.sessionActive = false;
            final OpcUaSubscription subscription = this.subscription;
            if (subscription != null && !this.closed) {
                this.lostSessionId = session.getSessionId();
                LOG.info("Session {} lost, reporting the monitored nodes", session.getSessionId());
                reportLoss(subscription, this.generation, SubscriptionManager.this.subscriptions);
            }
        }

        /**
         * Whether a request failed because the connection or the session was lost, which the session recovery of the
         * client deals with. A request that fails while the session is not active counts too, whatever its status: one
         * that was sent after the loss waits for that recovery and fails with the error of a failed reconnect attempt.
         */
        boolean isSessionLost(final StatusCode status) {
            return !this.sessionActive || SESSION_LOST.contains(status.getValue());
        }

        @Override
        public void onStatusChanged(final OpcUaSubscription subscription, final StatusCode status) {
            if (status.getValue() == StatusCodes.Bad_Timeout) {
                LOG.warn("Subscription timed out on the server, recreating it");
                recoverSubscription(subscription, this);
            } else {
                LOG.info("Subscription status changed {} : {}", subscription.getSubscriptionId(), status);
            }
        }

        @Override
        public void onWatchdogTimerElapsed(final OpcUaSubscription subscription) {
            LOG.debug("Subscription {} stopped publishing, checking it on the server",
                    subscription.getSubscriptionId().orElse(null));
            checkSubscription(subscription, this);
        }

        @Override
        public void onTransferFailed(final OpcUaSubscription subscription, final StatusCode statusCode) {
            LOG.info("Transfer failed {} : {}", subscription.getSubscriptionId(), statusCode);

            // we simply tear it down and build it up again
            handleConnectionFailure(new RuntimeCamelException("Subscription failed to reconnect"));
        }

    }

    public interface Worker<T> {
        void work(T on) throws Exception;
    }

    private static class Subscription {
        private final ExpandedNodeId nodeId;
        private final Double samplingInterval;

        private final Consumer<DataValue> valueConsumer;
        private MonitorFilterConfiguration monitorFilterConfiguration;
        private final Integer queueSize;

        /**
         * The generation of the connection that delivered the last value, guarded by this
         */
        private long generation;

        /**
         * Whether the last delivery to the consumer was a loss report, guarded by this
         */
        private boolean reported;

        /**
         * Whether the server refused to monitor the node: its status is that refusal, whatever the communication, until
         * its item is created
         */
        private volatile boolean refused;

        Subscription(ExpandedNodeId nodeId, final Double samplingInterval, Integer queueSize,
                     final Consumer<DataValue> valueConsumer,
                     final MonitorFilterConfiguration monitorFilterConfiguration) {
            this.nodeId = nodeId;
            this.samplingInterval = samplingInterval;
            this.queueSize = queueSize;
            this.valueConsumer = valueConsumer;
            this.monitorFilterConfiguration = monitorFilterConfiguration;
        }

        public ExpandedNodeId getNodeId() {
            return nodeId;
        }

        public Double getSamplingInterval() {
            return this.samplingInterval;
        }

        public Integer getQueueSize() {
            return this.queueSize;
        }

        /**
         * Delivers a value of the node to the consumer, which ends a loss report.
         *
         * @param generation the generation of the connection that received the value
         */
        synchronized void deliver(final long generation, final DataValue value) {
            this.generation = Math.max(this.generation, generation);
            this.reported = false;
            this.valueConsumer.accept(value);
        }

        /**
         * Delivers the status with which the server refused to monitor the node.
         */
        void refuse(final long generation, final DataValue status) {
            this.refused = true;
            deliver(generation, status);
        }

        /**
         * Delivers a loss report to the consumer, unless the node is reported or refused already, or a later connection
         * has delivered a value. The report of a replaced connection is queued behind its last values, which may race
         * the first values of its replacement.
         *
         * @param generation the generation of the connection whose communication was lost
         */
        synchronized void report(final long generation, final DataValue value) {
            if (this.reported || this.refused || this.generation > generation) {
                return;
            }
            this.reported = true;
            this.valueConsumer.accept(value);
        }

        public MonitoringFilter createMonitoringFilter() {
            if (Objects.isNull(this.monitorFilterConfiguration)
                    || Objects.isNull(this.monitorFilterConfiguration.getMonitorFilterType())) {
                return null;
            }
            final MonitoringFilter monitorFilter = this.monitorFilterConfiguration.createMonitoringFilter();
            return monitorFilter;
        }
    }

    /**
     * A status value for a node that could not be monitored, delivered to its consumer outside of the lock
     */
    private record PendingValue(Subscription subscription, long generation, DataValue value) {
        void deliver() {
            subscription.refuse(generation, value);
        }
    }

    private class Connected {
        private OpcUaClient client;
        private final OpcUaSubscription manager;
        private final SubscriptionListenerImpl listener;
        private final long generation;

        private final Map<UInteger, OpcUaMonitoredItem> goodSubscriptions = new HashMap<>();

        private final Map<String, UShort> namespaceCache = new ConcurrentHashMap<>();

        Connected(final OpcUaClient client, final OpcUaSubscription manager, final SubscriptionListenerImpl listener) {
            this.client = client;
            this.manager = manager;
            this.listener = listener;
            this.generation = listener.generation;
        }

        /**
         * Creates the monitored items of the subscriptions on the server.
         *
         * @param  subscriptions the subscriptions to create, by client handle
         * @return               the bad status values of the nodes that could not be monitored, to be delivered to
         *                       their consumers after the lock of the manager has been released
         * @throws UaException   if the server rejected the CreateMonitoredItems service call, the connection has to be
         *                       rebuilt
         */
        public List<PendingValue> putSubscriptions(final Map<UInteger, Subscription> subscriptions) throws Exception {

            final List<PendingValue> pendingValues = new ArrayList<>();

            if (subscriptions.isEmpty()) {
                return pendingValues;
            }

            // convert to requests

            // Milo 1.0.5 API: create monitored items and set up listeners
            final List<OpcUaMonitoredItem> items = new ArrayList<>(subscriptions.size());
            final Map<OpcUaMonitoredItem, UInteger> itemToClientHandle = new HashMap<>();

            for (final Map.Entry<UInteger, Subscription> entry : subscriptions.entrySet()) {
                final Subscription s = entry.getValue();

                final NodeId node = lookupNamespace(s.getNodeId()).get();

                if (node == null) {
                    // reported once like a node the server rejects, the other nodes and the connection are not affected
                    final StatusCode status = new StatusCode(StatusCodes.Bad_NodeIdUnknown);
                    LOG.warn("Monitored item for node {} could not be created, the namespace URI is unknown to the server: {}",
                            s.getNodeId().toParseableString(), status);
                    pendingValues.add(new PendingValue(s, this.generation, new DataValue(status)));
                } else {
                    final ReadValueId itemId = new ReadValueId(node, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
                    final OpcUaMonitoredItem item = new OpcUaMonitoredItem(itemId, MonitoringMode.Reporting);
                    if (null != s.getSamplingInterval()) {
                        item.setSamplingInterval(s.getSamplingInterval());
                    }
                    if (null != s.getQueueSize()) {
                        item.setQueueSize(UInteger.valueOf(s.getQueueSize()));
                    }
                    MonitoringFilter filter = s.createMonitoringFilter();
                    if (filter != null) {
                        item.setFilter(filter);
                    }
                    // set the listener before the item is created, values without a listener are dropped
                    item.setDataValueListener((monitoredItem, dataValue) -> s.deliver(this.generation, dataValue));
                    items.add(item);
                    // Keep track of which subscription this item belongs to
                    itemToClientHandle.put(item, entry.getKey());
                }
            }

            if (!items.isEmpty()) {
                // Add monitored items to the subscription manager
                for (OpcUaMonitoredItem item : items) {
                    this.manager.addMonitoredItem(item);
                }

                // Create the monitored items of this call on the server
                final List<MonitoredItemServiceOperationResult> results
                        = this.manager.createMonitoredItems(itemToClientHandle::containsKey);

                // the service call is partitioned, a rejected partition reports its service result for each of its items
                StatusCode rejectedServiceResult = null;
                final List<OpcUaMonitoredItem> failedItems = new ArrayList<>();
                for (final MonitoredItemServiceOperationResult result : results) {
                    final OpcUaMonitoredItem item = result.monitoredItem();
                    final UInteger clientHandle = itemToClientHandle.get(item);
                    if (result.isGood()) {
                        this.goodSubscriptions.put(clientHandle, item);
                        subscriptions.get(clientHandle).refused = false;
                    } else if (result.serviceResult().isBad()) {
                        failedItems.add(item);
                        rejectedServiceResult = result.serviceResult();
                    } else {
                        // reported once, the item is created again when the consumer restarts or the connection is rebuilt
                        failedItems.add(item);
                        final Subscription s = subscriptions.get(clientHandle);
                        final StatusCode status = result.operationResult().orElse(result.serviceResult());
                        LOG.warn("Monitored item for node {} could not be created: {}", s.getNodeId().toParseableString(),
                                status);
                        pendingValues.add(new PendingValue(s, this.generation, new DataValue(status)));
                    }
                }

                if (!failedItems.isEmpty()) {
                    // remove the failed items from the subscription, they were not created on the server
                    this.manager.removeMonitoredItems(failedItems);
                    this.manager.deleteMonitoredItems();
                }

                if (rejectedServiceResult != null) {
                    // the subscription is unusable, the caller tears the connection down and builds it up again
                    throw new UaException(rejectedServiceResult);
                }
            }

            return pendingValues;
        }

        /**
         * Creates the subscription on the server again, with the monitored items it had, after the server lost it.
         *
         * @param  subscriptions the registered subscriptions, by client handle
         * @return               the bad status values of the nodes that could not be monitored again, to be delivered
         *                       to their consumers after the lock of the manager has been released
         * @throws Exception     if the subscription could not be created or the server rejected the
         *                       CreateMonitoredItems service call, the connection has to be rebuilt
         */
        public List<PendingValue> recreate(final Map<UInteger, Subscription> subscriptions) throws Exception {
            final Map<UInteger, Subscription> monitored = new HashMap<>();
            this.goodSubscriptions.keySet()
                    .forEach(clientHandle -> monitored.put(clientHandle, subscriptions.get(clientHandle)));
            this.goodSubscriptions.clear();

            // the monitored items were reset together with the subscription, so they are dropped without a service call
            this.manager.removeMonitoredItems(this.manager.getMonitoredItems());
            this.manager.deleteMonitoredItems();

            this.manager.create();
            return putSubscriptions(monitored);
        }

        public void deactivate(final List<UInteger> clientHandles) throws Exception {
            final List<OpcUaMonitoredItem> items = new ArrayList<>(clientHandles.size());
            for (final UInteger clientHandle : clientHandles) {
                final OpcUaMonitoredItem item = this.goodSubscriptions.remove(clientHandle);
                if (item != null) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                this.manager.removeMonitoredItems(items);
                this.manager.deleteMonitoredItems();
            }
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
            //
            //            final CompletableFuture<DataValue> future
            //                    = this.client.readValue(0, TimestampsToReturn.Neither, Identifiers.Server_NamespaceArray);
            CompletableFuture<DataValue> future = this.client
                    .readValuesAsync(0, TimestampsToReturn.Neither,
                            Collections.singletonList(NodeIds.Server_NamespaceArray))
                    .thenApply(r -> r.get(0));

            return future.thenApply(value -> {
                final Object rawValue = value.getValue().getValue();

                if (rawValue instanceof String[] namespaces) {
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
            // the session closed by the disconnect is no loss of communication
            this.listener.closed = true;
            if (this.client != null) {
                try {
                    this.client.disconnect();
                    this.client = null;
                } catch (final UaException e) {
                    LOG.warn("Failed to disconnect client", e);
                }

            }
        }

        /**
         * @return the node ID with the namespace index of the server, completed with null if its namespace URI is not
         *         in the namespace table of the server: such a node does not exist on the server
         */
        private CompletableFuture<NodeId> lookupNamespace(final ExpandedNodeId nodeId) {
            LOG.trace("Expanded Node Id: {}", nodeId);

            final String uri = nodeId.getNamespaceUri();

            if (uri != null) {
                LOG.trace("Looking up namespace: {}", uri);
                return lookupNamespaceIndex(uri).thenApply(index -> {
                    if (index == null) {
                        LOG.debug("The namespace URI of node {} is unknown to the server", nodeId.toParseableString());
                        return null;
                    }
                    return toNodeId(index, nodeId);
                });
            } else {
                final UShort index = nodeId.getNamespaceIndex();
                LOG.trace("Using provided index: {}", index);
                return completedFuture(toNodeId(index, nodeId));
            }

        }

        public CompletableFuture<StatusCode> write(final ExpandedNodeId nodeId, final DataValue value) {
            /*
            return lookupNamespace(nodeId).thenCompose(node -> {
                LOG.debug("Node - expanded: {}, full: {}", nodeId, node);
                return this.client.writeValue(node, value).whenComplete((status, error) -> {
                    if (status != null) {
                        LOG.debug("Write to node={} = {} -> {}", node, value, status);
                    } else {
                        LOG.debug("Failed to write", error);
                    }
                });
            });
            */

            return lookupNamespace(nodeId).thenCompose(node -> {

                LOG.debug("Node - expanded: {}, full: {}", nodeId, node);

                if (node == null) {
                    // answered like a node the server does not have
                    return completedFuture(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
                }

                return this.client.writeValuesAsync(List.of(node), List.of(value))
                        .thenApply(r -> r.get(0))
                        .whenComplete((status, error) -> {
                            if (status != null) {
                                LOG.debug("Write to node={} = {} -> {}", node, value, status);
                            } else {
                                LOG.debug("Failed to write", error);
                            }
                        });
            });
        }

        public CompletableFuture<CallMethodResult> call(
                final ExpandedNodeId nodeId, final ExpandedNodeId methodId, final Variant[] inputArguments) {

            return lookupNamespace(nodeId).thenCompose(node -> {

                LOG.debug("Node   - expanded: {}, full: {}", nodeId, node);

                return lookupNamespace(methodId).thenCompose(method -> {

                    LOG.debug("Method - expanded: {}, full: {}", methodId, method);

                    if (node == null || method == null) {
                        // answered like the server: an unknown object is Bad_NodeIdUnknown, an unknown method Bad_MethodInvalid
                        final StatusCode status
                                = new StatusCode(node == null ? StatusCodes.Bad_NodeIdUnknown : StatusCodes.Bad_MethodInvalid);
                        return completedFuture(
                                new CallMethodResult(status, new StatusCode[0], new DiagnosticInfo[0], new Variant[0]));
                    }

                    final CallMethodRequest cmr = new CallMethodRequest(node, method, inputArguments);

                    /*
                    return this.client.call(cmr).whenComplete((status, error) -> {
                        if (status != null) {
                            LOG.debug("Call to node={}, method={} = {}-> {}", nodeId, methodId, inputArguments, status);
                        } else {
                            LOG.debug("Failed to call", error);
                        }
                    });
                    */
                    return this.client.callAsync(List.of(cmr))
                            .thenApply(r -> r.getResults()[0])
                            .whenComplete((status, error) -> {
                                if (status != null) {
                                    LOG.debug("Call to node={}, method={} = {}-> {}", nodeId, methodId, inputArguments, status);
                                } else {
                                    LOG.debug("Failed to call", error);
                                }
                            });

                });

            });
        }

        public CompletableFuture<List<DataValue>> readValues(List<ExpandedNodeId> expandedNodeIds) {

            final CompletableFuture<NodeId>[] nodeIdFutures
                    = expandedNodeIds.stream().map(this::lookupNamespace).toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(nodeIdFutures).thenCompose(param -> {
                List<NodeId> nodeIds = Stream.of(nodeIdFutures).map(CompletableFuture::join).collect(Collectors.toList());
                final List<NodeId> known = nodeIds.stream().filter(Objects::nonNull).collect(Collectors.toList());
                if (known.size() == nodeIds.size()) {
                    return this.client.readValuesAsync(0, TimestampsToReturn.Server, nodeIds);
                }
                // a node the server does not have gets the value the server returns for such a node
                final CompletableFuture<List<DataValue>> read = known.isEmpty()
                        ? completedFuture(List.of()) : this.client.readValuesAsync(0, TimestampsToReturn.Server, known);
                return read.thenApply(values -> {
                    final Iterator<DataValue> knownValues = values.iterator();
                    final List<DataValue> result = new ArrayList<>(nodeIds.size());
                    for (final NodeId nodeId : nodeIds) {
                        result.add(nodeId == null
                                ? new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown))
                                : knownValues.next());
                    }
                    return result;
                });
            });
        }

        private BrowseResult filter(final BrowseResult browseResult, final Pattern pattern) {

            final ReferenceDescription[] references = browseResult.getReferences();

            if (null == references || null == pattern) {
                return browseResult;
            }

            final List<ReferenceDescription> filteredReferences = new ArrayList<>();
            for (final ReferenceDescription reference : references) {
                final String id = reference.getNodeId().toParseableString();
                if (!(pattern.matcher(id).matches())) {
                    LOG.trace("Node {} excluded by filter", id);
                    continue;
                }
                filteredReferences.add(reference);
            }

            return new BrowseResult(
                    browseResult.getStatusCode(), browseResult.getContinuationPoint(),
                    filteredReferences.toArray(new ReferenceDescription[0]));
        }

        private CompletableFuture<Map<ExpandedNodeId, BrowseResult>> flatten(
                List<CompletableFuture<Map<ExpandedNodeId, BrowseResult>>> browseResults) {
            return CompletableFuture.allOf(browseResults.toArray(new CompletableFuture[0]))
                    .thenApply(__ -> browseResults
                            .stream()
                            .map(CompletableFuture::join)
                            .map(Map::entrySet)
                            .flatMap(Set::stream)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2)));
        }

        // Browse at continuation point if any
        public CompletableFuture<BrowseResult> browse(BrowseResult previousBrowseResult) {

            final ByteString continuationPoint = previousBrowseResult.getContinuationPoint();

            if (previousBrowseResult.getStatusCode().isGood() && continuationPoint.isNotNull()) {

                //                return this.client.browseNext(false, continuationPoint)
                return this.client.browseNextAsync(false, List.of(continuationPoint))
                        .thenApply(r -> r.getResults()[0])

                        .thenCompose(browseResult -> {

                            final ReferenceDescription[] previousReferences = previousBrowseResult.getReferences();
                            final ReferenceDescription[] references = browseResult.getReferences();

                            if (null == references) {

                                LOG.info("Browse continuation point -> no references");
                                return completedFuture(previousBrowseResult);
                            } else if (null == previousReferences) {

                                LOG.info("Browse continuation point -> previous references not obtained");
                                return completedFuture(browseResult);
                            }

                            final ReferenceDescription[] combined
                                    = Arrays.copyOf(previousReferences, previousReferences.length + references.length);
                            System.arraycopy(references, 0, combined, previousReferences.length, references.length);

                            LOG.debug("Browse continuation point -> {}: {} reference(s); total: {} reference(s)",
                                    browseResult.getStatusCode(), references.length, combined.length);

                            return browse(new BrowseResult(
                                    browseResult.getStatusCode(), browseResult.getContinuationPoint(), combined));
                        });

            } else {

                return completedFuture(previousBrowseResult);
            }
        }

        // Browse a single node, retrieve additional results, filter node ids and eventually browse deeper into the tree
        public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
                BrowseDescription browseDescription, BrowseResult browseResult, int depth, int maxDepth, Pattern pattern,
                int maxNodesPerRequest) {

            return browse(browseResult)

                    .thenCompose(preliminary -> completedFuture(filter(preliminary, pattern)))

                    .thenCompose(filtered -> {

                        final ExpandedNodeId expandedNodeId = browseDescription.getNodeId().expanded();
                        final Map<ExpandedNodeId, BrowseResult> root = Collections.singletonMap(expandedNodeId, filtered);
                        final CompletableFuture<Map<ExpandedNodeId, BrowseResult>> finalFuture = completedFuture(root);
                        final ReferenceDescription[] references = filtered.getReferences();

                        if (depth >= maxDepth || null == references) {
                            return finalFuture;
                        }

                        final List<CompletableFuture<Map<ExpandedNodeId, BrowseResult>>> futures = new ArrayList<>();

                        // Save current node
                        futures.add(finalFuture);

                        final List<ExpandedNodeId> nodeIds = Stream.of(references)
                                .map(ReferenceDescription::getNodeId).collect(Collectors.toList());

                        final List<List<ExpandedNodeId>> lists = Lists.partition(nodeIds, maxNodesPerRequest);
                        for (final List<ExpandedNodeId> list : lists) {
                            futures.add(browse(list, browseDescription.getBrowseDirection(),
                                    browseDescription.getNodeClassMask().intValue(), depth + 1, maxDepth, pattern,
                                    browseDescription.getIncludeSubtypes(), maxNodesPerRequest));
                        }

                        return flatten(futures);
                    });
        }

        // Browse according to a list of browse descriptions
        public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
                List<BrowseDescription> browseDescriptions,
                int depth, int maxDepth, Pattern pattern, int maxNodesPerRequest) {

            return this.client.browseAsync(browseDescriptions)

                    .thenCompose(partials -> {

                        // Fail a bit more gracefully in case of missing results
                        if (partials.size() != browseDescriptions.size()) {

                            final CompletableFuture<Map<ExpandedNodeId, BrowseResult>> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(new IllegalArgumentException(
                                    format("Invalid number of browse results: %s, expected %s",
                                            partials.size(), browseDescriptions.size())));
                            return failedFuture;

                            /* @TODO: Replace with Java 9 functionality like follows
                            return CompletableFuture.failedFuture(new IllegalArgumentException(
                                    format("Invalid number of browse results: %s, expected %s", partials.size(),
                                            browseDescriptions.size()))); */
                        }

                        final List<CompletableFuture<Map<ExpandedNodeId, BrowseResult>>> futures = new ArrayList<>();

                        for (int i = 0; i < partials.size(); i++) {

                            futures.add(browse(browseDescriptions.get(i), partials.get(i), depth, maxDepth, pattern,
                                    maxNodesPerRequest));
                        }

                        return flatten(futures);
                    });
        }

        // Wrapper for looking up nodes and instantiating initial browse descriptions according to the configuration provided
        @SuppressWarnings("unchecked")
        public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
                List<ExpandedNodeId> expandedNodeIds, BrowseDirection direction, int nodeClasses, int depth, int maxDepth,
                Pattern pattern, boolean includeSubTypes, int maxNodesPerRequest) {

            final CompletableFuture<NodeId>[] futures = expandedNodeIds.stream().map(this::lookupNamespace)
                    .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(futures)

                    .thenCompose(__ -> {

                        // a node the server does not have gets the result the server returns for such a node
                        final Map<ExpandedNodeId, BrowseResult> unknown = new HashMap<>();
                        final List<BrowseDescription> descriptions = new ArrayList<>();
                        for (int i = 0; i < futures.length; i++) {
                            final NodeId nodeId = futures[i].join();
                            if (nodeId == null) {
                                unknown.put(expandedNodeIds.get(i), new BrowseResult(
                                        new StatusCode(StatusCodes.Bad_NodeIdUnknown), ByteString.NULL_VALUE,
                                        new ReferenceDescription[0]));
                            } else {
                                descriptions.add(new BrowseDescription(
                                        nodeId, direction, NodeIds.References, includeSubTypes, uint(nodeClasses),
                                        uint(BrowseResultMask.All.getValue())));
                            }
                        }
                        if (unknown.isEmpty()) {
                            return browse(descriptions, depth, maxDepth, pattern, maxNodesPerRequest);
                        }
                        final CompletableFuture<Map<ExpandedNodeId, BrowseResult>> knownResults = descriptions.isEmpty()
                                ? completedFuture(Map.of())
                                : browse(descriptions, depth, maxDepth, pattern, maxNodesPerRequest);
                        return knownResults.thenApply(results -> {
                            final Map<ExpandedNodeId, BrowseResult> all = new HashMap<>(results);
                            all.putAll(unknown);
                            return all;
                        });
                    });
        }
    }

    private final MiloClientConfiguration configuration;
    private final ScheduledExecutorService reconnectExecutor;
    private final Executor executor;
    private final long reconnectTimeout;
    private final ServerCertificateTrust serverCertificateTrust;
    private final Lock lock = new ReentrantLock();

    private Connected connected;
    private volatile Throwable lastConnectFailure;
    private volatile boolean disposed;
    private Future<?> reconnectJob;

    /**
     * The registered nodes by client handle, written under {@code lock}. A loss report reads them without the lock,
     * which a registration holds while the server creates its items.
     */
    private final Map<UInteger, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionManager(final MiloClientConfiguration configuration, final ScheduledExecutorService reconnectExecutor,
                               final Executor executor, final long reconnectTimeout) {

        this.configuration = configuration;
        this.reconnectExecutor = reconnectExecutor;
        this.executor = executor;
        this.reconnectTimeout = reconnectTimeout;
        this.serverCertificateTrust = new ServerCertificateTrust(configuration);

        connect();
    }

    /**
     * Returns the milo {@link OpcUaClient} of the currently established connection, if any.
     *
     * @return the active client, or {@code null} if there is no current connection
     */
    public OpcUaClient getOpcUaClient() {
        lock.lock();
        try {
            return this.connected != null ? this.connected.client : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks the subscription of a connection on the server after it stopped publishing, and recovers it unless the
     * server confirms that it still has it.
     * <p>
     * A subscription that the server still has stops publishing too while a route is slow: the client replaces each of
     * its outstanding publish requests only once the notifications of that request have been processed, so the server
     * has none left to answer. The check is a SetPublishingMode request that enables publishing; the subscription is
     * always created with publishing enabled, so the request does not change its settings.
     * <p>
     * If the client has reset the subscription in the meantime, it is already being recovered: the client resets it
     * when the server reports that it timed out, when it was deleted and when it could not be transferred, and each of
     * these recovers it. A check that fails because the connection or the session was lost is left to the session
     * recovery of the client, as a failed delete in
     * {@link #recoverSubscription(OpcUaSubscription, SubscriptionListenerImpl)} is. Any other answer goes on to
     * {@link #recoverSubscription(OpcUaSubscription, SubscriptionListenerImpl)}.
     */
    private void checkSubscription(final OpcUaSubscription subscription, final SubscriptionListenerImpl listener) {
        final UInteger subscriptionId = subscription.getSubscriptionId().orElse(null);
        subscription.setPublishingModeAsync(true).whenCompleteAsync((unit, e) -> {
            final StatusCode status = e == null
                    ? StatusCode.GOOD
                    : UaException.extractStatusCode(e).orElse(new StatusCode(StatusCodes.Bad_UnexpectedError));
            if (status.isGood()) {
                LOG.info("Subscription {} stopped publishing but still exists on the server, keeping it",
                        subscriptionId);
            } else if (status.getValue() == StatusCodes.Bad_InvalidState) {
                LOG.debug("Subscription {} was reset while it was checked, it is already being recovered",
                        subscriptionId);
            } else if (listener.isSessionLost(status)) {
                LOG.info("Session lost while checking subscription {}, leaving it to the session recovery: {}",
                        subscriptionId, status);
            } else {
                LOG.warn("Subscription {} stopped publishing and was not confirmed by the server, recreating it: {}",
                        subscriptionId, status);
                recoverSubscription(subscription, listener);
            }
        }, clientExecutor(subscription));
    }

    /**
     * Recovers the subscription of a connection after the server did not confirm it or reported that it timed out.
     * <p>
     * The subscription is deleted first, and it is only created again, inside the existing session, once the server has
     * confirmed that it no longer exists; one that the client has already reset because it timed out is deleted without
     * a service call. A delete that fails because the connection or the session was lost, or that fails while the
     * session is not active, is left to the session recovery of the client: it resumes the subscription, whose watchdog
     * is armed again once publishing resumes, or reports that the subscription could not be transferred, which rebuilds
     * the connection. Any other failure rebuilds the connection, which closes the session together with its
     * subscriptions.
     * <p>
     * The delete neither blocks nor holds {@code lock}: a request sent before the connection was lost only fails once
     * the client notices the loss, and holding {@code lock} until then would hold up the producers and
     * {@link #getOpcUaClient()} as well.
     */
    private void recoverSubscription(final OpcUaSubscription subscription, final SubscriptionListenerImpl listener) {
        subscription.deleteAsync().whenCompleteAsync((unit, e) -> {
            final StatusCode status = e == null
                    ? StatusCode.GOOD
                    : UaException.extractStatusCode(e).orElse(new StatusCode(StatusCodes.Bad_UnexpectedError));
            if (status.isGood() || status.getValue() == StatusCodes.Bad_SubscriptionIdInvalid) {
                recreateSubscription(subscription);
            } else if (listener.isSessionLost(status)) {
                LOG.info("Session lost while deleting subscription {}, leaving it to the session recovery: {}",
                        subscription.getSubscriptionId().orElse(null), status);
            } else {
                lock.lock();
                try {
                    if (isCurrent(subscription)) {
                        handleConnectionFailure(e);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }, clientExecutor(subscription));
    }

    /**
     * The executor that the client of a subscription delivers its values on, which handles the outcome of a check or a
     * delete. Creating the subscription again and rebuilding the connection block until the server answers, and a
     * request sent while the connection is lost waits for the channel until a reconnect attempt restores it or fails.
     * The client schedules those attempts on the shared scheduled executor of Milo, which has a single thread on a
     * machine with one processor, so the wait must not hold one of its threads.
     * <p>
     * Milo asks that blocking calls are not made on the threads of this executor either, because they complete the
     * responses. The executor of the transport is not set here, so this is the shared executor of Milo, a cached pool
     * that starts another thread for a response while one of its threads waits, as it does while a route processes a
     * value.
     */
    private static Executor clientExecutor(final OpcUaSubscription subscription) {
        return subscription.getClient().getTransport().getConfig().getExecutor();
    }

    /**
     * Creates the subscription of the current connection again, inside the existing session, together with the
     * monitored items it had. Does nothing if the subscription no longer belongs to the current connection or has been
     * created again already. A node that cannot be monitored again is reported to its consumer, as when the consumer
     * starts; if the subscription itself cannot be created again, the connection is rebuilt.
     */
    private void recreateSubscription(final OpcUaSubscription subscription) {
        final List<PendingValue> pendingValues = new ArrayList<>();
        lock.lock();
        try {
            if (!isCurrent(subscription) || subscription.getSubscriptionId().isPresent()) {
                LOG.debug("Subscription is stale or already recreated");
                return;
            }
            pendingValues.addAll(this.connected.recreate(this.subscriptions));
            LOG.info("Subscription recreated as {}", subscription.getSubscriptionId().orElse(null));
        } catch (final Exception e) {
            handleConnectionFailure(e);
        } finally {
            lock.unlock();
        }

        // deliver outside of the lock, the routes may use the connection
        pendingValues.forEach(PendingValue::deliver);
    }

    private boolean isCurrent(final OpcUaSubscription subscription) {
        return !this.disposed && this.connected != null && this.connected.manager == subscription;
    }

    /**
     * Asks the server to send the current value of every monitored item of the subscription again, after the session
     * was lost and activated again. The server sends only changes through a reactivated session, so a node whose value
     * did not change would keep its loss report. If the server does not resend, the subscription is deleted and created
     * again in the session, and its new items deliver their first values.
     */
    private void resendData(final OpcUaSubscription subscription, final SubscriptionListenerImpl listener) {
        final UInteger subscriptionId = subscription.getSubscriptionId().orElse(null);
        if (subscriptionId == null) {
            LOG.debug("Subscription has no ID, it is being recovered");
            return;
        }
        final CallMethodRequest request = new CallMethodRequest(
                NodeIds.Server, NodeIds.Server_ResendData, new Variant[] { new Variant(subscriptionId) });
        subscription.getClient().callAsync(List.of(request)).whenCompleteAsync((response, e) -> {
            final StatusCode status;
            if (e != null) {
                status = UaException.extractStatusCode(e).orElse(new StatusCode(StatusCodes.Bad_UnexpectedError));
            } else if (response.getResults() == null || response.getResults().length == 0) {
                status = new StatusCode(StatusCodes.Bad_UnexpectedError);
            } else {
                status = response.getResults()[0].getStatusCode();
            }
            if (status.isGood()) {
                LOG.info("Session activated again, subscription {} resends the values of its nodes", subscriptionId);
            } else if (listener.isSessionLost(status)) {
                LOG.info("Session lost while subscription {} was asked to resend its values: {}", subscriptionId,
                        status);
            } else {
                LOG.warn("Server does not resend the values of subscription {} ({}), recreating it", subscriptionId,
                        status);
                recoverSubscription(subscription, listener);
            }
        }, clientExecutor(subscription));
    }

    /**
     * Queues the loss report of the given nodes on the delivery queue of the subscription, behind the values it already
     * holds. The queue keeps running after the client has disconnected, so a replaced connection delivers the report
     * after its last values.
     *
     * @param generation the generation of the connection whose communication was lost
     */
    private void reportLoss(
            final OpcUaSubscription subscription, final long generation, final Map<UInteger, Subscription> nodes) {
        final DateTime time = DateTime.now();
        subscription.getDeliveryQueue().execute(() -> deliverLoss(nodes, generation, time));
    }

    /**
     * Delivers the loss report to the given nodes that are still registered.
     */
    private void deliverLoss(final Map<UInteger, Subscription> nodes, final long generation, final DateTime time) {
        final DataValue value = new DataValue(Variant.NULL_VALUE, NO_COMMUNICATION, time, time);
        for (final Map.Entry<UInteger, Subscription> node : nodes.entrySet()) {
            if (this.disposed) {
                return;
            }
            if (this.subscriptions.get(node.getKey()) == node.getValue()) {
                node.getValue().report(generation, value);
            }
        }
    }

    private void handleConnectionFailure(final Throwable e) {
        lock.lock();
        try {
            if (this.connected != null) {
                // the nodes lose their communication with the connection, after the values it still delivers
                reportLoss(this.connected.manager, this.connected.generation, this.subscriptions);
                this.connected.dispose();
                this.connected = null;
            }
            this.lastConnectFailure = e;

            // log

            LOG.info("Connection failed", e);

            // always trigger re-connect

            triggerReconnect(true);
        } finally {
            lock.unlock();
        }
    }

    private void connect() {
        LOG.info("Starting connect");

        lock.lock();
        try {
            this.reconnectJob = null;

            if (this.disposed) {
                // we woke up disposed
                return;
            }
        } finally {
            lock.unlock();
        }

        performAndEvalConnect();
    }

    private void performAndEvalConnect() {
        final List<PendingValue> pendingValues = new ArrayList<>();
        try {
            final Connected connected = performConnect();
            LOG.debug("Connect call done");
            lock.lock();
            try {
                if (this.disposed) {
                    // we got disposed during connect
                    return;
                }

                try {
                    LOG.debug("Setting subscriptions: {}", this.subscriptions.size());
                    pendingValues.addAll(connected.putSubscriptions(this.subscriptions));

                    LOG.debug("Update state : {} -> {}", this.connected, connected);
                    final Connected oldConnected = this.connected;
                    this.connected = connected;
                    this.lastConnectFailure = null;

                    if (oldConnected != null) {
                        LOG.debug("Dispose old state");
                        oldConnected.dispose();
                    }

                } catch (final Exception e) {
                    LOG.info("Failed to set subscriptions", e);
                    connected.dispose();
                    throw e;
                }
            } finally {
                lock.unlock();
            }
        } catch (final Exception e) {
            this.lastConnectFailure = e;
            logConnectFailure(e);
            triggerReconnect(false);
        }

        // deliver outside of the lock, the routes may use the connection
        pendingValues.forEach(PendingValue::deliver);
    }

    private static void logConnectFailure(final Exception e) {
        final Throwable certificateFailure = ServerCertificateTrust.findCertificateFailure(e);
        if (certificateFailure instanceof ServerCertificateTrust.NotConfiguredException) {
            LOG.warn("Failed to connect: {}", certificateFailure.getMessage());
            LOG.debug("Failed to connect", e);
        } else if (certificateFailure != null) {
            // a certificate rejected by the trust list can be trusted later, so the client keeps retrying
            // passed as text: SLF4J would log a trailing Throwable as the exception, with its stack trace
            LOG.warn("Failed to connect, because a certificate or security check failed: {}",
                    String.valueOf(certificateFailure));
            LOG.debug("Failed to connect", e);
        } else {
            LOG.info("Failed to connect", e);
        }
    }

    private Connected performConnect() throws Exception {

        // eval endpoint
        String discoveryUri = getEndpointDiscoveryUri();

        final URI uri = URI.create(getEndpointDiscoveryUri());

        // Extracting/removing user:password string from the full URL is error-prone with special characters,
        // because of that the discovery URL is rebuilt from URI parts.
        discoveryUri
                = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
                        .toString();
        final String user = uri.getUserInfo();

        LOG.debug("Discovering endpoints from: {}", discoveryUri);

        final EndpointDescription endpoint = DiscoveryClient.getEndpoints(discoveryUri).thenApply(endpoints -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found endpoints:");
                for (final EndpointDescription ep : endpoints) {
                    LOG.debug("\t{}", ep);
                }
            }

            try {
                return findEndpoint(endpoints);
            } catch (final URISyntaxException e) {
                throw new RuntimeException("Failed to find endpoints", e);
            }
        }).get();

        LOG.debug("Selected endpoint: {}", endpoint);

        // the same validator checks the server certificate for the secure channel, the session and the identity token
        final CertificateValidator certificateValidator = this.serverCertificateTrust.validatorFor(endpoint);

        // set identity providers
        final List<IdentityProvider> providers = new LinkedList<>();

        // prefer explicit username/password parameters over URI-embedded credentials
        // to avoid issues with special characters in the URI
        final String explicitUsername = this.configuration.getUsername();
        final String explicitPassword = this.configuration.getPassword();
        if (explicitUsername != null && !explicitUsername.isEmpty()) {
            LOG.debug("Enable username/password provider (explicit parameter): {}", explicitUsername);
            providers.add(new UsernameProvider(
                    explicitUsername, explicitPassword != null ? explicitPassword : "", certificateValidator));
        } else if (user != null && !user.isEmpty()) {
            final String[] creds = user.split(":", 2);
            if (creds != null) {
                if (creds.length == 2) {
                    LOG.debug("Enable username/password provider: {}", creds[0]);
                }

                providers.add(new UsernameProvider(creds[0], creds.length == 2 ? creds[1] : "", certificateValidator));
            }
        }

        providers.add(AnonymousProvider.INSTANCE);

        final OpcUaClientConfigBuilder cfg = this.configuration.newBuilder();
        cfg.setIdentityProvider(new CompositeProvider(providers));
        cfg.setEndpoint(endpoint);
        cfg.setCertificateValidator(certificateValidator);

        // create client
        final OpcUaClient client = OpcUaClient.create(
                cfg.build(),
                // have been in MiloclientConfiguration before
                transportBuilder -> {
                    if (configuration.getChannelLifetime() != null) {
                        transportBuilder.setChannelLifetime(Unsigned.uint(configuration.getChannelLifetime()));
                    }
                });

        final SubscriptionListenerImpl listener = new SubscriptionListenerImpl(this.generationCounter.incrementAndGet());
        client.addSessionActivityListener(listener);

        try {

            // connect is called inside this try block to ensure that disconnect is called on exceptions
            client.connect();

            // Create subscription synchronously - the create() method blocks until complete
            OpcUaSubscription subscription = new OpcUaSubscription(client);
            subscription.setPublishingInterval(this.configuration.getRequestedPublishingInterval());
            subscription.setSubscriptionListener(listener);
            subscription.create();
            listener.subscription = subscription;

            return new Connected(client, subscription, listener);

            //            final OpcUaSubscription manager = client.getSubscriptionManager()
            //                    .createSubscription(this.configuration.getRequestedPublishingInterval()).get();
            //            client.getSubscriptionManager().addSubscriptionListener(new SubscriptionListenerImpl());
            //
            //            return new Connected(client, manager);
        } catch (final Exception e) {
            // clean up
            client.disconnect();
            throw e;
        }
    }

    private String getEndpointDiscoveryUri() {

        if (!Strings.isNullOrEmpty(this.configuration.getDiscoveryEndpointUri())) {
            return this.configuration.getDiscoveryEndpointUri();
        }

        if (!Strings.isNullOrEmpty(this.configuration.getDiscoveryEndpointSuffix())) {
            return this.configuration.getEndpointUri() + this.configuration.getDiscoveryEndpointSuffix();
        }

        return this.configuration.getEndpointUri();
    }

    public void dispose() {
        Connected connected;

        lock.lock();
        try {
            if (this.disposed) {
                return;
            }
            this.disposed = true;
            connected = this.connected;
            if (this.reconnectJob != null) {
                // a waiting reconnect would keep the thread of the reconnect executor until it is due
                this.reconnectJob.cancel(false);
                this.reconnectJob = null;
            }
        } finally {
            lock.unlock();
        }

        if (connected != null) {
            // dispose outside of lock
            connected.dispose();
        }

        try {
            this.serverCertificateTrust.close();
        } catch (final IOException e) {
            LOG.debug("Failed to close the trust list. This exception is ignored.", e);
        }
    }

    private void triggerReconnect(final boolean immediate) {
        lock.lock();
        try {
            LOG.info("Trigger re-connect (immediate: {})", immediate);

            if (this.reconnectJob != null) {
                LOG.info("Re-connect already scheduled");
                return;
            }

            if (immediate) {
                this.reconnectJob = this.reconnectExecutor.submit(this::connect);
            } else {
                this.reconnectJob
                        = this.reconnectExecutor.schedule(this::connect, this.reconnectTimeout, TimeUnit.MILLISECONDS);
            }
        } finally {
            lock.unlock();
        }
    }

    private EndpointDescription findEndpoint(final List<EndpointDescription> endpoints) throws URISyntaxException {

        final Predicate<String> allowed;
        final Set<String> uris = this.configuration.getAllowedSecurityPolicies();

        if (this.configuration.getAllowedSecurityPolicies() == null
                || this.configuration.getAllowedSecurityPolicies().isEmpty()) {
            allowed = uri -> true;
        } else {
            allowed = uris::contains;
        }

        EndpointDescription best = null;
        for (final EndpointDescription ep : endpoints) {

            if (!allowed.test(ep.getSecurityPolicyUri())) {
                continue;
            }

            if (best == null || ep.getSecurityLevel().compareTo(best.getSecurityLevel()) > 0) {
                best = ep;
            }
        }

        // return result, might override the host part

        return overrideHostOrPort(best);
    }

    /**
     * Optionally override the host or port of the endpoint URL with the configured one. <br>
     * The method will call {@link #overrideHostOrPort(String)} if the endpoint is not {@code null} and
     * ({@link MiloClientConfiguration#isOverrideHost()} returns {@code true} or
     * {@link MiloClientConfiguration#isOverridePort()} returns {@code true}).
     *
     * @param  desc               The endpoint descriptor to work on
     * @return                    Either the provided or updated endpoint descriptor. Only returns {@code null} when the
     *                            input was {@code null}.
     * @throws URISyntaxException on case the URI is malformed
     */
    private EndpointDescription overrideHostOrPort(final EndpointDescription desc) throws URISyntaxException {
        if (desc == null) {
            return null;
        }

        if (!this.configuration.isOverrideHost() && !this.configuration.isOverridePort()) {
            return desc;
        }

        return new EndpointDescription(
                overrideHostOrPort(desc.getEndpointUrl()), desc.getServer(), desc.getServerCertificate(),
                desc.getSecurityMode(),
                desc.getSecurityPolicyUri(),
                desc.getUserIdentityTokens(), desc.getTransportProfileUri(), desc.getSecurityLevel());
    }

    /**
     * Override host part of the endpoint URL with the configured one.
     *
     * @param  endpointUrl        the server provided endpoint URL
     * @return                    A new endpoint URL with the host part or port part exchanged by the configured host or
     *                            configured port, respectively. Will be {@code null} when the input is {@code null}.
     * @throws URISyntaxException on case the URI is malformed
     */
    private String overrideHostOrPort(final String endpointUrl) throws URISyntaxException {

        if (endpointUrl == null) {
            return null;
        }

        final URI uri = URI.create(endpointUrl);
        final URI originalUri = URI.create(configuration.getEndpointUri());
        final String host = configuration.isOverrideHost() ? originalUri.getHost() : uri.getHost();
        final int port = configuration.isOverridePort() ? originalUri.getPort() : uri.getPort();

        return new URI(uri.getScheme(), uri.getUserInfo(), host, port, uri.getPath(), uri.getQuery(), uri.getFragment())
                .toString();
    }

    protected void whenConnected(final Worker<Connected> worker) {
        lock.lock();
        try {
            if (this.connected != null) {
                try {
                    worker.work(this.connected);
                } catch (final Exception e) {
                    handleConnectionFailure(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private <T> CompletableFuture<T> newNotConnectedResult() {
        final CompletableFuture<T> result = new CompletableFuture<>();
        // the reason of the last failed connection attempt, if any, tells the caller why there is no connection
        result.completeExceptionally(new IllegalStateException("No connected", this.lastConnectFailure));
        return result;
    }

    public List<UInteger> registerItems(
            final Map<ExpandedNodeId, Consumer<DataValue>> valueConsumers, final Double samplingInterval,
            final Integer queueSize, final MonitorFilterConfiguration monitorFilterConfiguration) {

        final Map<UInteger, Subscription> subscriptions = new LinkedHashMap<>();
        valueConsumers.forEach((nodeId, valueConsumer) -> subscriptions.put(
                Unsigned.uint(this.clientHandleCounter.incrementAndGet()),
                new Subscription(nodeId, samplingInterval, queueSize, valueConsumer, monitorFilterConfiguration)));

        final List<PendingValue> pendingValues = new ArrayList<>();
        lock.lock();
        try {
            this.subscriptions.putAll(subscriptions);

            if (this.connected == null) {
                // no communication with the server: the nodes are reported off the thread that starts the consumer,
                // with a generation below every connection's, so the report is dropped once any connection has
                // delivered their values, also one that was being built while they were registered
                final DateTime time = DateTime.now();
                this.executor.execute(() -> deliverLoss(subscriptions, 0L, time));
            } else if (!this.connected.listener.sessionActive) {
                // the session is lost; the nodes are reported before the values of their items, on the same queue
                reportLoss(this.connected.manager, this.connected.generation, subscriptions);
            }

            whenConnected(connected -> pendingValues.addAll(connected.putSubscriptions(subscriptions)));
        } finally {
            lock.unlock();
        }

        // deliver outside of the lock, the routes may use the connection
        pendingValues.forEach(PendingValue::deliver);

        return new ArrayList<>(subscriptions.keySet());
    }

    public void unregisterItems(final List<UInteger> clientHandles) {
        lock.lock();
        try {
            final List<UInteger> removed = new ArrayList<>(clientHandles.size());
            for (final UInteger clientHandle : clientHandles) {
                if (this.subscriptions.remove(clientHandle) != null) {
                    removed.add(clientHandle);
                }
            }
            if (!removed.isEmpty()) {
                whenConnected(connected -> {
                    connected.deactivate(removed);
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<CallMethodResult> call(
            final ExpandedNodeId nodeId, final ExpandedNodeId methodId, final Variant[] inputArguments) {
        lock.lock();
        try {
            if (this.connected == null) {
                return newNotConnectedResult();
            }

            return this.connected.call(nodeId, methodId, inputArguments).handleAsync((status, e) -> {
                // handle outside the lock, running using
                // handleAsync
                if (e != null) {
                    handleConnectionFailure(e);
                }
                return null;
            }, this.executor);
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<?> write(final ExpandedNodeId nodeId, final DataValue value) {
        lock.lock();
        try {
            if (this.connected == null) {
                return newNotConnectedResult();
            }

            return this.connected.write(nodeId, value).handleAsync((status, e) -> {
                // handle outside the lock, running using
                // handleAsync
                if (e != null) {
                    handleConnectionFailure(e);
                }
                return status;
            }, this.executor);
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<?> readValues(final List<ExpandedNodeId> nodeIds) {
        lock.lock();
        try {
            if (this.connected == null) {
                return newNotConnectedResult();
            }

            return this.connected.readValues(nodeIds).handleAsync((nodes, e) -> {
                // handle outside the lock, running using
                // handleAsync
                if (e != null) {
                    handleConnectionFailure(e);
                }
                return nodes;
            }, this.executor);
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
            List<ExpandedNodeId> expandedNodeIds, BrowseDirection direction, int nodeClasses, int maxDepth, String filter,
            boolean includeSubTypes, int maxNodesPerRequest) {
        lock.lock();
        try {
            if (this.connected == null) {
                return newNotConnectedResult();
            }

            return this.connected.browse(expandedNodeIds, direction, nodeClasses, 1, maxDepth,
                    null != filter ? Pattern.compile(filter) : null, includeSubTypes, maxNodesPerRequest)
                    .handleAsync((browseResult, e) -> {
                        // handle outside the lock, running using
                        // handleAsync
                        if (e != null) {
                            handleConnectionFailure(e);
                        }
                        return browseResult;
                    }, this.executor);
        } finally {
            lock.unlock();
        }
    }
}
