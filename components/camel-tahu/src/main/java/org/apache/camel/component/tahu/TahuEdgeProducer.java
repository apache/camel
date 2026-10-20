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
package org.apache.camel.component.tahu;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.tahu.handlers.CamelBdSeqManager;
import org.apache.camel.component.tahu.handlers.TahuEdgeClient;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.model.MqttServerDefinition;

public abstract class TahuEdgeProducer extends DefaultProducer {

    protected static final ConcurrentMap<EdgeNodeDescriptor, TahuEdgeClient> descriptorClients = new ConcurrentHashMap<>();
    protected static final ConcurrentMap<EdgeNodeDescriptor, Future<?>> descriptorFutures = new ConcurrentHashMap<>();
    // the metric maps of the started Edge Node and Device producers, added again to a client that replaces a stopped
    // one (a producer adds its map when it starts and removes it when it stops)
    private static final ConcurrentMap<EdgeNodeDescriptor, SparkplugBPayloadMap> descriptorMetricMaps
            = new ConcurrentHashMap<>();
    // the started producers that use the metric map of a descriptor (several producers to one endpoint share its map),
    // changed together with descriptorMetricMaps while holding its lock
    private static final ConcurrentMap<EdgeNodeDescriptor, Integer> descriptorMetricMapUsers = new ConcurrentHashMap<>();

    private final CamelContext camelContext;

    // the client of the Edge Node: a stopped Edge Node producer shuts its client down, and the next start creates a
    // new one, as a Tahu EdgeClient cannot be started again
    protected volatile TahuEdgeClient tahuEdgeClient;
    protected ExecutorService clientExecutorService;
    protected final EdgeNodeDescriptor edgeNodeDescriptor;
    private final EdgeNodeDescriptor clientDescriptor;
    private final TahuEdgeEndpoint endpoint;

    private TahuEdgeProducer(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {
        super(endpoint);

        camelContext = endpoint.getCamelContext();

        this.edgeNodeDescriptor = edgeNodeDescriptor;
        this.endpoint = endpoint;

        // Only create clients for Edge Nodes, not Devices. If passed a DeviceDescriptor, create/lookup based on its
        // EdgeNodeDescriptor, i.e. minus its deviceId.
        clientDescriptor = edgeNodeDescriptor.isDeviceDescriptor()
                ? ((DeviceDescriptor) edgeNodeDescriptor).getEdgeNodeDescriptor() : edgeNodeDescriptor;

        tahuEdgeClient = createClient(endpoint, edgeNodeDescriptor);
    }

    /**
     * The client of the Edge Node. When the Edge Node producer was stopped and started since this producer took its
     * client, that client is shut down and replaced by a new one (which has the metric maps of this producer): take the
     * new one.
     */
    protected TahuEdgeClient currentClient() {
        TahuEdgeClient client = tahuEdgeClient;
        TahuEdgeClient current = descriptorClients.get(clientDescriptor);
        if (current != null && current != client) {
            client = current;
            tahuEdgeClient = client;
        }
        return client;
    }

    /**
     * The client to start: the client of this producer, or a new one when a stop shut it down.
     */
    protected TahuEdgeClient clientForStart() {
        TahuEdgeClient client = createClient(endpoint, edgeNodeDescriptor);
        tahuEdgeClient = client;
        return client;
    }

    @Override
    public void process(Exchange exchange) throws InvalidPayloadException {
        Message message = exchange.getMessage();

        boolean messageHasNullBody = message.getBody() == null;

        if (messageHasNullBody) {
            message.setBody(message, Message.class);
        }

        SparkplugBPayload payload = message.getMandatoryBody(SparkplugBPayload.class);

        String messageType = (edgeNodeDescriptor.isDeviceDescriptor()) ? "DDATA" : "NDATA";
        message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);

        message.setHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, edgeNodeDescriptor);

        Optional.ofNullable(payload.getUuid())
                .ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
        Optional.ofNullable(payload.getTimestamp())
                .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
        Optional.ofNullable(payload.getSeq())
                .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

        try {
            currentClient().publishData(edgeNodeDescriptor, payload);
        } catch (Throwable t) {
            exchange.setException(t);
        }

        if (messageHasNullBody) {
            message.setBody(null);
        }
    }

    private TahuEdgeClient createClient(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {

        TahuEdgeClient tahuEdgeClient = descriptorClients.computeIfAbsent(clientDescriptor, end -> {

            TahuConfiguration configuration = endpoint.getConfiguration();

            List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();
            long rebirthDebounceDelay = configuration.getRebirthDebounceDelay();

            String primaryHostId = endpoint.getPrimaryHostId();
            List<String> deviceIds = endpoint.getDeviceIdList();
            boolean useAliases = endpoint.isUseAliases();

            clientExecutorService = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this,
                    end.getDescriptorString());

            BdSeqManager bdSeqManager = Optional.ofNullable(endpoint.getBdSeqManager())
                    .orElseGet(() -> new CamelBdSeqManager(end, endpoint.getBdSeqNumPath()));

            TahuEdgeClient client = new TahuEdgeClient.ClientBuilder()
                    .edgeNodeDescriptor(edgeNodeDescriptor)
                    .deviceIds(deviceIds)
                    .primaryHostId(primaryHostId)
                    .useAliases(useAliases)
                    .rebirthDebounceDelay(rebirthDebounceDelay)
                    .serverDefinitions(serverDefinitions)
                    .bdSeqManager(bdSeqManager)
                    .clientExecutorService(clientExecutorService)
                    .build();

            // a client that replaces a stopped one gets the metric maps of the Edge Node and of its Devices again
            descriptorMetricMaps.forEach((descriptor, metricMap) -> {
                EdgeNodeDescriptor nodeOf = descriptor.isDeviceDescriptor()
                        ? ((DeviceDescriptor) descriptor).getEdgeNodeDescriptor() : descriptor;
                if (end.equals(nodeOf)) {
                    client.addDeviceMetricDataPayloadMap(descriptor, metricMap);
                }
            });

            return client;
        });

        // Add the metricDataPayloadMap based on the original
        // EdgeNodeDescriptor/DeviceDescriptor. This ensures the maps for an edge node
        // and all its devices are updated correctly regardless of creation order.
        tahuEdgeClient.addDeviceMetricDataPayloadMap(edgeNodeDescriptor, endpoint.getMetricDataTypePayloadMap());

        return tahuEdgeClient;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        SparkplugBPayloadMap metricMap = endpoint.getMetricDataTypePayloadMap();
        if (metricMap != null) {
            synchronized (descriptorMetricMaps) {
                if (descriptorMetricMaps.get(edgeNodeDescriptor) == metricMap) {
                    descriptorMetricMapUsers.merge(edgeNodeDescriptor, 1, Integer::sum);
                } else {
                    descriptorMetricMaps.put(edgeNodeDescriptor, metricMap);
                    descriptorMetricMapUsers.put(edgeNodeDescriptor, 1);
                }
            }
            // the client of the Edge Node may have been created again while this producer was stopped
            TahuEdgeClient client = descriptorClients.get(clientDescriptor);
            if (client != null) {
                client.addDeviceMetricDataPayloadMap(edgeNodeDescriptor, metricMap);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        SparkplugBPayloadMap metricMap = endpoint.getMetricDataTypePayloadMap();
        if (metricMap != null) {
            synchronized (descriptorMetricMaps) {
                // only when no other started producer (of the same endpoint) uses the map
                if (descriptorMetricMaps.get(edgeNodeDescriptor) == metricMap
                        && descriptorMetricMapUsers.merge(edgeNodeDescriptor, -1, Integer::sum) <= 0) {
                    descriptorMetricMaps.remove(edgeNodeDescriptor, metricMap);
                    descriptorMetricMapUsers.remove(edgeNodeDescriptor);
                }
            }
        }
    }

    static final class TahuEdgeNodeProducer extends TahuEdgeProducer {
        private TahuEdgeNodeProducer(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {
            super(endpoint, edgeNodeDescriptor);
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();

            TahuEdgeClient client = clientForStart();
            descriptorFutures.computeIfAbsent(edgeNodeDescriptor, end -> client.startup());
        }

        @Override
        protected void doSuspend() throws Exception {
            super.doSuspend();

            currentClient().suspend();
        }

        @Override
        protected void doResume() throws Exception {
            super.doResume();

            currentClient().resume();
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();

            Future<?> clientFuture = descriptorFutures.remove(edgeNodeDescriptor);
            if (clientFuture != null) {
                TahuEdgeClient client = tahuEdgeClient;
                client.shutdown();
                // a shut down client cannot connect again: the next start of an Edge Node producer creates a new
                // client, else it would take this one from the map and never connect
                descriptorClients.remove(edgeNodeDescriptor, client);
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(client.getClientExecutorService());
            }
        }
    }

    static final class TahuEdgeDeviceProducer extends TahuEdgeProducer {
        private TahuEdgeDeviceProducer(TahuEdgeEndpoint endpoint, DeviceDescriptor deviceDescriptor) {
            super(endpoint, deviceDescriptor);
        }
    }

    static final class Builder {

        private final TahuEdgeEndpoint endpoint;

        private String groupId;
        private String edgeNode;
        private String deviceId;

        public Builder(TahuEdgeEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder edgeNode(String edgeNode) {
            this.edgeNode = edgeNode;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public TahuEdgeProducer build() {

            TahuEdgeProducer response;

            // If this Producer is for a Device, the edgeNodeDescriptor will be a
            // DeviceDescriptor (subclass of the EdgeNodeDescriptor) describing both the
            // Edge Node to which the Device is attached and the Device itself.
            if (ObjectHelper.isNotEmpty(deviceId)) {
                response = new TahuEdgeDeviceProducer(endpoint, new DeviceDescriptor(groupId, edgeNode, deviceId));
            } else {
                response = new TahuEdgeNodeProducer(endpoint, new EdgeNodeDescriptor(groupId, edgeNode));
            }

            return response;
        }
    }
}
