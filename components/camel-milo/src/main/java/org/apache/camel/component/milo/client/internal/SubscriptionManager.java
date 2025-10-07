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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.CompositeProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
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

    private final AtomicLong clientHandleCounter = new AtomicLong();

    private final class SubscriptionListenerImpl implements OpcUaSubscription.SubscriptionListener {

        @Override
        public void onStatusChanged(final OpcUaSubscription subscription, final StatusCode status) {
            LOG.info("Subscription status changed {} : {}", subscription.getSubscriptionId(), status);
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

        Subscription(ExpandedNodeId nodeId, final Double samplingInterval, final Consumer<DataValue> valueConsumer,
                     final MonitorFilterConfiguration monitorFilterConfiguration) {
            this.nodeId = nodeId;
            this.samplingInterval = samplingInterval;
            this.valueConsumer = valueConsumer;
            this.monitorFilterConfiguration = monitorFilterConfiguration;
        }

        public ExpandedNodeId getNodeId() {
            return nodeId;
        }

        public Double getSamplingInterval() {
            return this.samplingInterval;
        }

        public Consumer<DataValue> getValueConsumer() {
            return this.valueConsumer;
        }

        public ExtensionObject createMonitoringFilter(OpcUaClient client) {
            if (Objects.isNull(this.monitorFilterConfiguration)
                    || Objects.isNull(this.monitorFilterConfiguration.getMonitorFilterType())) {
                return null;
            }
            final MonitoringFilter monitorFilter = this.monitorFilterConfiguration.createMonitoringFilter();
            return ExtensionObject.encode(client.getStaticEncodingContext(), monitorFilter);
        }
    }

    private class Connected {
        private OpcUaClient client;
        private final OpcUaSubscription manager;

        private final Map<UInteger, Subscription> badSubscriptions = new HashMap<>();

        private final Map<UInteger, OpcUaMonitoredItem> goodSubscriptions = new HashMap<>();

        private final Map<String, UShort> namespaceCache = new ConcurrentHashMap<>();

        Connected(final OpcUaClient client, final OpcUaSubscription manager) {
            this.client = client;
            this.manager = manager;
        }

        public void putSubscriptions(final Map<UInteger, Subscription> subscriptions) throws Exception {

            if (subscriptions.isEmpty()) {
                return;
            }

            // convert to requests

            // Milo 1.0.5 API: create monitored items and set up listeners
            final List<OpcUaMonitoredItem> items = new ArrayList<>(subscriptions.size());
            final Map<OpcUaMonitoredItem, UInteger> itemToClientHandle = new HashMap<>();

            for (final Map.Entry<UInteger, Subscription> entry : subscriptions.entrySet()) {
                final Subscription s = entry.getValue();

                final NodeId node = lookupNamespace(s.getNodeId()).get();

                if (node == null) {
                    handleSubscriptionError(new StatusCode(StatusCodes.Bad_InvalidArgument), entry.getKey(), s);
                } else {
                    final ReadValueId itemId = new ReadValueId(node, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
                    final OpcUaMonitoredItem item = new OpcUaMonitoredItem(itemId, MonitoringMode.Reporting);
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

                // Create monitored items on the server - this assigns client handles
                this.manager.createMonitoredItems();

                // Now set up data value listeners after items are created and have client handles
                for (OpcUaMonitoredItem item : items) {
                    UInteger originalClientHandle = itemToClientHandle.get(item);
                    final Subscription s = subscriptions.get(originalClientHandle);
                    if (s != null) {
                        item.setDataValueListener((monitoredItem, dataValue) -> s.getValueConsumer().accept(dataValue));
                    }
                }
            }

            if (!this.badSubscriptions.isEmpty()) {
                SubscriptionManager.this.executor.schedule(this::resubscribe, SubscriptionManager.this.reconnectTimeout,
                        TimeUnit.MILLISECONDS);
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
                handleConnectionFailure(e);
            }
        }

        public void activate(final UInteger clientHandle, final Subscription subscription) throws Exception {
            putSubscriptions(Collections.singletonMap(clientHandle, subscription));
        }

        public void deactivate(final UInteger clientHandle) throws Exception {
            final OpcUaMonitoredItem item = this.goodSubscriptions.remove(clientHandle);
            if (item != null) {
                // TODO is this migrated correctly?
                this.manager.removeMonitoredItem(item);
                //                this.manager.synchronizeMonitoredItems();
                this.manager.deleteMonitoredItems();
                //                this.manager.deleteMonitoredItems(Collections.singletonList(item)).get();
            } else {
                this.badSubscriptions.remove(clientHandle);
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
                            Collections.singletonList(Identifiers.Server_NamespaceArray))
                    .thenApply(r -> r.get(0));

            return future.thenApply(value -> {
                final Object rawValue = value.getValue().getValue();

                if (rawValue instanceof String[]) {
                    final String[] namespaces = (String[]) rawValue;
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
                try {
                    this.client.disconnect();
                    this.client = null;
                } catch (final UaException e) {
                    LOG.warn("Failed to disconnect client", e);
                }

            }
        }

        private CompletableFuture<NodeId> lookupNamespace(final ExpandedNodeId nodeId) {
            LOG.trace("Expanded Node Id: {}", nodeId);

            final String uri = nodeId.getNamespaceUri();

            if (uri != null) {
                LOG.trace("Looking up namespace: {}", uri);
                return lookupNamespaceIndex(uri).thenApply(index -> toNodeId(index, nodeId));
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
                return this.client.readValuesAsync(0, TimestampsToReturn.Server, nodeIds);
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

                        final List<NodeId> nodeIds = Stream.of(futures).map(CompletableFuture::join)
                                .collect(Collectors.toList());

                        return completedFuture(nodeIds.stream().map(nodeId -> new BrowseDescription(
                                nodeId, direction, Identifiers.References, includeSubTypes, uint(nodeClasses),
                                uint(BrowseResultMask.All.getValue()))).collect(Collectors.toList()));
                    })

                    .thenCompose(descriptions -> browse(descriptions, depth, maxDepth, pattern, maxNodesPerRequest));
        }
    }

    private final MiloClientConfiguration configuration;
    private final ScheduledExecutorService executor;
    private final long reconnectTimeout;

    private Connected connected;
    private boolean disposed;
    private Future<?> reconnectJob;
    private final Map<UInteger, Subscription> subscriptions = new HashMap<>();

    public SubscriptionManager(final MiloClientConfiguration configuration, final ScheduledExecutorService executor,
                               final long reconnectTimeout) {

        this.configuration = configuration;
        this.executor = executor;
        this.reconnectTimeout = reconnectTimeout;

        connect();
    }

    private synchronized void handleConnectionFailure(final Throwable e) {
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

        // eval endpoint

        String discoveryUri = getEndpointDiscoveryUri();

        final URI uri = URI.create(getEndpointDiscoveryUri());

        // milo library doesn't allow user info as a part of the uri, it has to
        // be
        // removed before sending to milo
        final String user = uri.getUserInfo();
        if (user != null && !user.isEmpty()) {
            discoveryUri = discoveryUri.replaceFirst(user + "@", "");
        }
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

        // set identity providers
        final List<IdentityProvider> providers = new LinkedList<>();

        if (user != null && !user.isEmpty()) {
            final String[] creds = user.split(":", 2);
            if (creds != null) {
                if (creds.length == 2) {
                    LOG.debug("Enable username/password provider: {}", creds[0]);
                }

                providers.add(new UsernameProvider(creds[0], creds[1]));
            }
        }

        providers.add(AnonymousProvider.INSTANCE);

        final OpcUaClientConfigBuilder cfg = this.configuration.newBuilder();
        cfg.setIdentityProvider(new CompositeProvider(providers));
        cfg.setEndpoint(endpoint);

        // create client
        final OpcUaClient client = OpcUaClient.create(
                cfg.build(),
                // have been in MiloclientConfiguration before
                transportBuilder -> {
                    if (configuration.getChannelLifetime() != null) {
                        transportBuilder.setChannelLifetime(Unsigned.uint(configuration.getChannelLifetime()));
                    }
                });

        client.connect();

        try {
            // Create subscription synchronously - the create() method blocks until complete
            OpcUaSubscription subscription = new OpcUaSubscription(client);
            subscription.setSubscriptionListener(new SubscriptionListenerImpl());
            subscription.create();

            return new Connected(client, subscription);

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

        return overrideHost(best);
    }

    /**
     * Optionally override the host of the endpoint URL with the configured one. <br>
     * The method will call {@link #overrideHost(String)} if the endpoint is not {@code null} and
     * {@link MiloClientConfiguration#isOverrideHost()} returns {@code true}.
     *
     * @param  desc               The endpoint descriptor to work on
     * @return                    Either the provided or updated endpoint descriptor. Only returns {@code null} when the
     *                            input was {@code null}.
     * @throws URISyntaxException on case the URI is malformed
     */
    private EndpointDescription overrideHost(final EndpointDescription desc) throws URISyntaxException {
        if (desc == null) {
            return null;
        }

        if (!this.configuration.isOverrideHost()) {
            return desc;
        }

        return new EndpointDescription(
                overrideHost(desc.getEndpointUrl()), desc.getServer(), desc.getServerCertificate(), desc.getSecurityMode(),
                desc.getSecurityPolicyUri(),
                desc.getUserIdentityTokens(), desc.getTransportProfileUri(), desc.getSecurityLevel());
    }

    /**
     * Override host part of the endpoint URL with the configured one.
     *
     * @param  endpointUrl        the server provided endpoint URL
     * @return                    A new endpoint URL with the host part exchanged by the configured host. Will be
     *                            {@code null} when the input is {@code null}.
     * @throws URISyntaxException on case the URI is malformed
     */
    private String overrideHost(final String endpointUrl) throws URISyntaxException {

        if (endpointUrl == null) {
            return null;
        }

        final URI uri = URI.create(endpointUrl);
        final URI originalUri = URI.create(configuration.getEndpointUri());

        return new URI(
                uri.getScheme(), uri.getUserInfo(), originalUri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(),
                uri.getFragment()).toString();
    }

    protected synchronized void whenConnected(final Worker<Connected> worker) {
        if (this.connected != null) {
            try {
                worker.work(this.connected);
            } catch (final Exception e) {
                handleConnectionFailure(e);
            }
        }
    }

    private static <T> CompletableFuture<T> newNotConnectedResult() {
        final CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalStateException("No connected"));
        return result;
    }

    public UInteger registerItem(
            final ExpandedNodeId nodeId, final Double samplingInterval, final Consumer<DataValue> valueConsumer,
            final MonitorFilterConfiguration monitorFilterConfiguration) {

        final UInteger clientHandle = Unsigned.uint(this.clientHandleCounter.incrementAndGet());
        final Subscription subscription = new Subscription(nodeId, samplingInterval, valueConsumer, monitorFilterConfiguration);

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

    public CompletableFuture<CallMethodResult> call(
            final ExpandedNodeId nodeId, final ExpandedNodeId methodId, final Variant[] inputArguments) {
        synchronized (this) {
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
        }
    }

    public CompletableFuture<?> write(final ExpandedNodeId nodeId, final DataValue value) {
        synchronized (this) {
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
        }
    }

    public CompletableFuture<?> readValues(final List<ExpandedNodeId> nodeIds) {
        synchronized (this) {
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
        }
    }

    public CompletableFuture<Map<ExpandedNodeId, BrowseResult>> browse(
            List<ExpandedNodeId> expandedNodeIds, BrowseDirection direction, int nodeClasses, int maxDepth, String filter,
            boolean includeSubTypes, int maxNodesPerRequest) {
        synchronized (this) {
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
        }
    }
}
