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

package org.apache.camel.component.tahu.handlers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.tahu.edge.EdgeClient;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.RandomStartupDelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@SuppressWarnings("unused")
public final class TahuEdgeClient extends EdgeClient {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeClient.class);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5L);

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final TahuEdgeMetricHandler tahuEdgeNodeMetricHandler;
    private final TahuEdgeClientCallback tahuEdgeNodeClientCallback;

    private final ExecutorService clientExecutorService;
    private volatile Future<?> clientSubmittedFuture = null;
    private volatile boolean suspended = false;

    private final Marker loggingMarker;

    private TahuEdgeClient(
            TahuEdgeMetricHandler tahuEdgeNodeMetricHandler,
            EdgeNodeDescriptor edgeNodeDescriptor,
            List<String> deviceIds,
            String primaryHostId,
            boolean useAliases,
            Long rebirthDebounceDelay,
            List<MqttServerDefinition> mqttServerDefinitions,
            TahuEdgeClientCallback tahuEdgeNodeClientCallback,
            RandomStartupDelay randomStartupDelay,
            ExecutorService clientExecutorService) {
        super(
                tahuEdgeNodeMetricHandler,
                edgeNodeDescriptor,
                deviceIds,
                primaryHostId,
                useAliases,
                rebirthDebounceDelay,
                mqttServerDefinitions,
                tahuEdgeNodeClientCallback,
                randomStartupDelay);

        this.edgeNodeDescriptor = edgeNodeDescriptor;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        this.tahuEdgeNodeMetricHandler = tahuEdgeNodeMetricHandler;
        this.tahuEdgeNodeClientCallback = tahuEdgeNodeClientCallback;

        this.clientExecutorService = clientExecutorService;
    }

    public Future<?> startup() {
        if (clientSubmittedFuture == null) {
            clientSubmittedFuture = clientExecutorService.submit(this);
            suspended = false;
        }
        return clientSubmittedFuture;
    }

    public void suspend() {
        if (clientSubmittedFuture != null && !suspended) {
            this.disconnect(false);
            suspended = true;
        }
    }

    public void resume() {
        if (clientSubmittedFuture != null && suspended) {
            this.handleRebirthRequest(false);
            suspended = false;
        }
    }

    @Override
    public void shutdown() {
        Future<?> clientSubmittedFuture = this.clientSubmittedFuture;
        if (clientSubmittedFuture != null) {
            suspended = false;
            super.shutdown();

            try {
                clientSubmittedFuture.get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                clientSubmittedFuture.cancel(true);
            } catch (CancellationException | ExecutionException | InterruptedException e) {
                LOG.warn(loggingMarker, "Caught exception waiting for client shutdown", e);
            }
            this.clientSubmittedFuture = null;
        }
    }

    public SparkplugBPayloadMap addDeviceMetricDataPayloadMap(
            SparkplugDescriptor metricDescriptor, SparkplugBPayloadMap metricDataTypePayloadMap) {
        return tahuEdgeNodeMetricHandler.addDeviceMetricDataPayloadMap(metricDescriptor, metricDataTypePayloadMap);
    }

    public void publishData(SparkplugDescriptor sd, SparkplugBPayload payload) {
        tahuEdgeNodeMetricHandler.updateCachedMetrics(sd, payload);

        if (sd.isDeviceDescriptor()) {
            publishDeviceData(((DeviceDescriptor) sd).getDeviceId(), payload);
        } else {
            publishNodeData(payload);
        }
    }

    public static final class ClientBuilder {

        private EdgeNodeDescriptor edgeNodeDescriptor;
        private List<String> deviceIds;
        private String primaryHostId;
        private boolean useAliases;
        private Long rebirthDebounceDelay = null;
        private List<MqttServerDefinition> serverDefinitions;
        private RandomStartupDelay randomStartupDelay = null;
        private BdSeqManager bdSeqManager;
        private ExecutorService clientExecutorService;

        private volatile TahuEdgeClient tahuEdgeClient;

        public ClientBuilder() {}

        public ClientBuilder edgeNodeDescriptor(EdgeNodeDescriptor end) {
            checkBuildState();
            this.edgeNodeDescriptor = end;
            return this;
        }

        public ClientBuilder deviceIds(List<String> deviceIds) {
            checkBuildState();
            this.deviceIds = List.copyOf(deviceIds);
            return this;
        }

        public ClientBuilder primaryHostId(String primaryHostId) {
            checkBuildState();
            this.primaryHostId = primaryHostId;
            return this;
        }

        public ClientBuilder useAliases(boolean useAliases) {
            checkBuildState();
            this.useAliases = useAliases;
            return this;
        }

        public ClientBuilder rebirthDebounceDelay(Long rebirthDebounceDelay) {
            checkBuildState();
            this.rebirthDebounceDelay = rebirthDebounceDelay;
            return this;
        }

        public ClientBuilder serverDefinitions(List<MqttServerDefinition> serverDefinitions) {
            checkBuildState();
            this.serverDefinitions = List.copyOf(serverDefinitions);
            return this;
        }

        public ClientBuilder bdSeqManager(BdSeqManager bsm) {
            checkBuildState();
            this.bdSeqManager = bsm;
            return this;
        }

        public ClientBuilder clientExecutorService(ExecutorService clientExecutorService) {
            checkBuildState();
            this.clientExecutorService = clientExecutorService;
            return this;
        }

        private void checkBuildState() throws IllegalStateException {
            if (tahuEdgeClient != null) {
                throw new IllegalStateException(
                        "Unable to reuse a ClientBuilder for multiple TahuEdgeClient instances");
            }
        }

        public TahuEdgeClient build() {
            TahuEdgeClient cachedTahuEdgeClient = tahuEdgeClient;
            if (cachedTahuEdgeClient == null) {

                TahuEdgeMetricHandler tahuEdgeNodeMetricHandler =
                        new TahuEdgeMetricHandler(edgeNodeDescriptor, bdSeqManager);
                TahuEdgeClientCallback tahuClientCallback =
                        new TahuEdgeClientCallback(edgeNodeDescriptor, tahuEdgeNodeMetricHandler);

                cachedTahuEdgeClient = tahuEdgeClient = new TahuEdgeClient(
                        tahuEdgeNodeMetricHandler,
                        edgeNodeDescriptor,
                        deviceIds,
                        primaryHostId,
                        useAliases,
                        rebirthDebounceDelay,
                        serverDefinitions,
                        tahuClientCallback,
                        randomStartupDelay,
                        clientExecutorService);

                LOG.debug(
                        tahuEdgeClient.loggingMarker,
                        "Created TahuEdgeClient for {} with deviceIds {}",
                        edgeNodeDescriptor,
                        deviceIds);

                tahuEdgeNodeMetricHandler.setClient(cachedTahuEdgeClient);
                tahuClientCallback.setClient(cachedTahuEdgeClient);
            }

            return cachedTahuEdgeClient;
        }
    }
}
