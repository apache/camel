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
import org.eclipse.tahu.model.MqttServerDefinition;

public abstract class TahuEdgeProducer extends DefaultProducer {

    protected static final ConcurrentMap<EdgeNodeDescriptor, TahuEdgeClient> descriptorClients =
            new ConcurrentHashMap<>();
    protected static final ConcurrentMap<EdgeNodeDescriptor, Future<?>> descriptorFutures = new ConcurrentHashMap<>();

    private final CamelContext camelContext;

    protected final TahuEdgeClient tahuEdgeClient;
    protected ExecutorService clientExecutorService;
    protected final EdgeNodeDescriptor edgeNodeDescriptor;

    private TahuEdgeProducer(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {
        super(endpoint);

        camelContext = endpoint.getCamelContext();

        this.edgeNodeDescriptor = edgeNodeDescriptor;

        tahuEdgeClient = createClient(endpoint, edgeNodeDescriptor);
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

        Optional.ofNullable(payload.getUuid()).ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
        Optional.ofNullable(payload.getTimestamp())
                .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
        Optional.ofNullable(payload.getSeq())
                .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

        try {
            tahuEdgeClient.publishData(edgeNodeDescriptor, payload);
        } catch (Throwable t) {
            exchange.setException(t);
        }

        if (messageHasNullBody) {
            message.setBody(null);
        }
    }

    private TahuEdgeClient createClient(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {

        // Only create clients for Edge Nodes, not Devices. If passed a
        // DeviceDescriptor, create/lookup based on its EdgeNodeDescriptor, i.e. minus
        // its deviceId.
        EdgeNodeDescriptor clientCreationDescriptor = edgeNodeDescriptor;
        if (edgeNodeDescriptor.isDeviceDescriptor()) {
            clientCreationDescriptor = ((DeviceDescriptor) edgeNodeDescriptor).getEdgeNodeDescriptor();
        }

        TahuEdgeClient tahuEdgeClient = descriptorClients.computeIfAbsent(clientCreationDescriptor, end -> {
            TahuConfiguration configuration = endpoint.getConfiguration();

            List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();
            long rebirthDebounceDelay = configuration.getRebirthDebounceDelay();

            String primaryHostId = endpoint.getPrimaryHostId();
            List<String> deviceIds = endpoint.getDeviceIdList();
            boolean useAliases = endpoint.isUseAliases();

            clientExecutorService =
                    camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, end.getDescriptorString());

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

            return client;
        });

        // Add the metricDataPayloadMap based on the original
        // EdgeNodeDescriptor/DeviceDescriptor. This ensures the maps for an edge node
        // and all its devices are updated correctly regardless of creation order.
        tahuEdgeClient.addDeviceMetricDataPayloadMap(edgeNodeDescriptor, endpoint.getMetricDataTypePayloadMap());

        return tahuEdgeClient;
    }

    static final class TahuEdgeNodeProducer extends TahuEdgeProducer {
        private TahuEdgeNodeProducer(TahuEdgeEndpoint endpoint, EdgeNodeDescriptor edgeNodeDescriptor) {
            super(endpoint, edgeNodeDescriptor);
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();

            descriptorFutures.computeIfAbsent(edgeNodeDescriptor, end -> tahuEdgeClient.startup());
        }

        @Override
        protected void doSuspend() throws Exception {
            super.doSuspend();

            tahuEdgeClient.suspend();
        }

        @Override
        protected void doResume() throws Exception {
            super.doResume();

            tahuEdgeClient.resume();
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();

            Future<?> clientFuture = descriptorFutures.remove(edgeNodeDescriptor);
            if (clientFuture != null) {
                tahuEdgeClient.shutdown();
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
