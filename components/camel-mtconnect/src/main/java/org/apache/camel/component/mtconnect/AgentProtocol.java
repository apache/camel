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
package org.apache.camel.component.mtconnect;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MTConnect protocol against one agent, one step at a time: probe, current, with assets=true the assets, then
 * sample, either the long-lived streaming request or one sample request per step. It resynchronises from current when
 * the agent's buffer no longer holds the next observation, re-reads the probe when the agent restarts, and reports an
 * outage once per data item. Each observation becomes one exchange of the consumer, processed synchronously in sequence
 * order. The streaming and the polling consumer drive it; they own only the threading or the scheduling. Not
 * thread-safe: one step runs at a time.
 */
final class AgentProtocol {

    private static final Logger LOG = LoggerFactory.getLogger(AgentProtocol.class);

    private enum Phase {
        PROBE,
        CURRENT,
        ASSETS,
        SAMPLE
    }

    private static final String ASSET_CHANGED = "AssetChanged";
    private static final String ASSET_REMOVED = "AssetRemoved";
    private static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    private static final String GONE = "gone";

    private final MTConnectEndpoint endpoint;
    private final DefaultConsumer consumer;
    private final AgentReader reader;
    private final boolean streaming;

    private Phase phase = Phase.PROBE;
    private Map<String, DataItem> dataItems = Map.of();
    private final Set<String> known = new LinkedHashSet<>();
    private final Set<String> bad = new HashSet<>();
    private Long instanceId;
    private long cursor;
    private long count;
    private String resync;
    private boolean outage;
    private int delivered;
    // asset tracking: the asset buffer size from the probe, the device names by uuid, the AssetChanged and
    // AssetRemoved observations whose asset is still to be read, and the version last sent per asset id
    private Long assetBufferSize;
    private Map<String, String> deviceNames = Map.of();
    private final Map<String, Observation> pendingAssets = new LinkedHashMap<>();
    private final Map<String, String> sentAssets = new HashMap<>();

    /**
     * @param streaming whether the sample phase reads the streaming request, or one sample request per step
     */
    AgentProtocol(MTConnectEndpoint endpoint, DefaultConsumer consumer, AgentReader reader, boolean streaming) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.reader = reader;
        this.streaming = streaming;
    }

    /**
     * Performs the next step: probe, current, assets or sample. A failure is reported here, as an outage or to the
     * exception handler.
     *
     * @return true when the next step may follow at once; false after a failure, or when the agent ended the sample
     *         stream, so that the caller waits before the next step
     */
    boolean step() throws InterruptedException {
        delivered = 0;
        try {
            switch (phase) {
                case PROBE -> probe();
                case CURRENT -> current();
                case ASSETS -> assets();
                case SAMPLE -> {
                    if (!readPendingAssets()) {
                        return true;
                    }
                    return streaming ? stream() : poll();
                }
            }
            return true;
        } catch (AgentFailure failure) {
            if (consumer.isRunAllowed()) {
                onFailure(failure);
            }
            return false;
        } catch (RuntimeException e) {
            consumer.getExceptionHandler().handleException("Unexpected failure reading the MTConnect agent "
                                                           + endpoint.getAgentUrl(),
                    e);
            return false;
        }
    }

    /** The number of messages the last step delivered. */
    int delivered() {
        return delivered;
    }

    private void probe() throws AgentFailure, InterruptedException {
        AgentReader.Answer answer = reader.probe();
        if (answer.document() instanceof AgentDocument.Error error) {
            throw agentError(error);
        }
        AgentDocument.Probe probe = answer.expect(AgentDocument.Probe.class);
        Long probeInstance = probe.header().instanceId();
        if (instanceId != null && !instanceId.equals(probeInstance)) {
            resync = MTConnectConstants.RESYNC_INSTANCE_CHANGED;
        }
        instanceId = probeInstance;
        dataItems = probe.dataItems();
        deviceNames = new HashMap<>();
        dataItems.values().forEach(item -> deviceNames.put(item.deviceUuid(), item.deviceName()));
        assetBufferSize = probe.header().assetBufferSize();
        pendingAssets.clear();
        known.clear();
        if (endpoint.getPath() == null) {
            known.addAll(dataItems.keySet());
        }
        Long bufferSize = probe.header().bufferSize();
        count = bufferSize == null ? endpoint.getCount() : Math.min(endpoint.getCount(), bufferSize);
        answered();
        LOG.debug("Probed {}: {} data items, instance {}, buffer size {}", endpoint.getAgentUrl(), dataItems.size(),
                instanceId, bufferSize);
        phase = Phase.CURRENT;
    }

    private void current() throws AgentFailure, InterruptedException {
        AgentReader.Answer answer = reader.current(null);
        if (!accept(answer.document())) {
            return;
        }
        AgentDocument.Streams streams = answer.expect(AgentDocument.Streams.class);
        for (Observation observation : streams.observations()) {
            if (!consumer.isRunAllowed()) {
                return;
            }
            deliver(observation, MTConnectConstants.DOCUMENT_CURRENT, resync);
        }
        cursor = next(streams);
        resync = null;
        bad.clear();
        phase = endpoint.isAssets() ? Phase.ASSETS : Phase.SAMPLE;
    }

    /**
     * Reads every asset the agent holds for the device and type, removed ones included, after each current, so that
     * changes and removals the samples did not report, during a long outage or before an agent restart, are not lost.
     * The count is the asset buffer size; the version check sends each asset version once.
     */
    private void assets() throws AgentFailure, InterruptedException {
        AgentReader.Answer answer = reader.assets(endpoint.getAssetType(), assetBufferSize, true);
        if (!accept(answer.document())) {
            return;
        }
        AgentDocument.Assets assets = answer.expect(AgentDocument.Assets.class);
        for (AgentDocument.Asset asset : assets.assets()) {
            if (!consumer.isRunAllowed()) {
                return;
            }
            sendAsset(asset, asset.removed());
        }
        // the whole read covers the changes reported before it
        pendingAssets.clear();
        phase = Phase.SAMPLE;
    }

    /**
     * Reads the asset of each AssetChanged and AssetRemoved observation delivered so far, after the observation. A
     * failed read stays pending for the next step. False when the state machine changed phase.
     */
    private boolean readPendingAssets() throws AgentFailure, InterruptedException {
        while (!pendingAssets.isEmpty()) {
            if (!consumer.isRunAllowed()) {
                return false;
            }
            Map.Entry<String, Observation> next = pendingAssets.entrySet().iterator().next();
            String id = next.getKey();
            Observation event = next.getValue();
            boolean removal = event.elementName().equals(ASSET_REMOVED);
            AgentReader.Answer answer = reader.asset(List.of(id));
            AgentDocument document = answer.document();
            if (document instanceof AgentDocument.Error error && error.hasCode(ASSET_NOT_FOUND)) {
                answered();
                pendingAssets.remove(id);
                if (removal) {
                    sendGone(id, event);
                } else {
                    LOG.debug("The MTConnect agent {} no longer holds the changed asset {}", endpoint.getAgentUrl(), id);
                }
                continue;
            }
            if (!accept(document)) {
                return false;
            }
            for (AgentDocument.Asset asset : answer.expect(AgentDocument.Assets.class).assets()) {
                sendAsset(asset, removal || asset.removed());
            }
            pendingAssets.remove(id);
        }
        return true;
    }

    /**
     * Notes the asset of an AssetChanged or AssetRemoved observation for reading, unless the type filter excludes it.
     */
    private void track(Observation observation) {
        String element = observation.elementName();
        if (!endpoint.isAssets() || observation.unavailable() || !"EVENT".equals(observation.category())
                || !element.equals(ASSET_CHANGED) && !element.equals(ASSET_REMOVED) || observation.value() == null) {
            return;
        }
        String type = observation.attributes().assetType();
        if (endpoint.getAssetType() != null && type != null && !endpoint.getAssetType().equals(type)) {
            return;
        }
        String id = observation.value().toString().trim();
        if (!id.isEmpty()) {
            // the latest event per asset decides whether it is read as changed or as removed
            pendingAssets.remove(id);
            pendingAssets.put(id, observation);
        }
    }

    /** Sends an asset message unless this version of the asset, its timestamp and removed flag, was sent already. */
    private void sendAsset(AgentDocument.Asset asset, boolean removed) {
        if (endpoint.getAssetType() != null && !endpoint.getAssetType().equals(asset.type())) {
            return;
        }
        String version = asset.timestamp() + "/" + removed;
        if (version.equals(sentAssets.put(asset.assetId(), version))) {
            return;
        }
        Exchange exchange = consumer.createExchange(false);
        AssetMessages.fill(exchange.getMessage(), endpoint.getAgentUrl(), asset, removed,
                deviceNames.get(asset.deviceUuid()), instanceId);
        process(exchange);
    }

    /** Sends the message of a removed asset the agent no longer holds: no body, the removed flag. */
    private void sendGone(String id, Observation removal) {
        if (GONE.equals(sentAssets.put(id, GONE))) {
            return;
        }
        Exchange exchange = consumer.createExchange(false);
        AssetMessages.fillGone(exchange.getMessage(), endpoint.getAgentUrl(), id, removal, instanceId);
        process(exchange);
    }

    /** Reads the sample stream; false when the agent ended it without an error, true when the phase changed. */
    private boolean stream() throws AgentFailure, InterruptedException {
        try (AgentReader.SampleStream stream
                = reader.stream(cursor, count, endpoint.getInterval(), endpoint.getHeartbeat())) {
            AgentReader.Answer answer;
            while (consumer.isRunAllowed() && (answer = stream.next()) != null) {
                if (!sample(answer) || !readPendingAssets()) {
                    return true;
                }
            }
        }
        // an agent or proxy that ends every stream at once must not get a request loop without a delay
        LOG.debug("The MTConnect agent {} ended the sample stream", endpoint.getAgentUrl());
        return false;
    }

    /** One sample request from the cursor, which the agent answers with one document. */
    private boolean poll() throws AgentFailure, InterruptedException {
        if (sample(reader.sample(cursor, null, count))) {
            readPendingAssets();
        }
        return true;
    }

    /** Delivers one sample document; false when the state machine left the sample phase. */
    private boolean sample(AgentReader.Answer answer) throws AgentFailure, InterruptedException {
        if (!accept(answer.document())) {
            return false;
        }
        AgentDocument.Streams streams = answer.expect(AgentDocument.Streams.class);
        for (Observation observation : streams.observations()) {
            if (!consumer.isRunAllowed()) {
                return false;
            }
            deliver(observation, MTConnectConstants.DOCUMENT_SAMPLE, null);
            bad.remove(observation.dataItemId());
            track(observation);
        }
        cursor = next(streams);
        if (!bad.isEmpty() && streams.header().drained()) {
            return refreshUntouched();
        }
        return true;
    }

    /**
     * After an outage, once the samples have caught up, delivers the current value of every item that the catch-up did
     * not touch. Values at or after the cursor are left to the samples, so that nothing is delivered twice.
     */
    private boolean refreshUntouched() throws AgentFailure, InterruptedException {
        AgentReader.Answer answer = reader.current(null);
        if (!accept(answer.document())) {
            return false;
        }
        AgentDocument.Streams current = answer.expect(AgentDocument.Streams.class);
        for (Observation observation : current.observations()) {
            if (!consumer.isRunAllowed()) {
                return false;
            }
            if (bad.contains(observation.dataItemId()) && observation.sequence() < cursor) {
                deliver(observation, MTConnectConstants.DOCUMENT_CURRENT, null);
            }
        }
        bad.clear();
        return true;
    }

    /**
     * Applies the rules every document is subject to: a new instance id means the agent restarted, and OUT_OF_RANGE
     * means its buffer no longer holds the cursor. False when the state machine changed phase.
     */
    private boolean accept(AgentDocument document) throws AgentFailure {
        Long documentInstance = document.header().instanceId();
        if (instanceId != null && documentInstance != null && !instanceId.equals(documentInstance)) {
            LOG.info("The MTConnect agent {} restarted: instance {} became {}", endpoint.getAgentUrl(), instanceId,
                    documentInstance);
            answered();
            resync = MTConnectConstants.RESYNC_INSTANCE_CHANGED;
            phase = Phase.PROBE;
            return false;
        }
        if (document instanceof AgentDocument.Error error) {
            if (error.hasCode(MTConnectConstants.RESYNC_OUT_OF_RANGE)) {
                LOG.info("The MTConnect agent {} no longer holds sequence {}; resynchronising from current",
                        endpoint.getAgentUrl(), cursor);
                answered();
                resync = MTConnectConstants.RESYNC_OUT_OF_RANGE;
                phase = Phase.CURRENT;
                return false;
            }
            throw agentError(error);
        }
        answered();
        return true;
    }

    private void onFailure(AgentFailure failure) {
        if (failure.kind() == AgentFailure.Kind.AGENT_ERROR && phase != Phase.ASSETS) {
            // the device or request may have changed on the agent: start over from the probe. An error to the assets
            // request retries that request, so that the current values are not delivered again on every attempt
            phase = Phase.PROBE;
        }
        if (outage) {
            LOG.debug("The MTConnect agent {} still cannot be read: {}", endpoint.getAgentUrl(), failure.getMessage());
            return;
        }
        outage = true;
        // an error the agent reports goes to the exception handler as well, once per outage
        if (failure.kind() == AgentFailure.Kind.AGENT_ERROR) {
            consumer.getExceptionHandler()
                    .handleException("The MTConnect agent " + endpoint.getAgentUrl() + " cannot be read", failure);
        } else {
            LOG.warn("The MTConnect agent {} cannot be read: {}", endpoint.getAgentUrl(), failure.getMessage());
        }
        String reason = failure.kind() == AgentFailure.Kind.UNREACHABLE
                ? MTConnectConstants.REASON_UNREACHABLE : MTConnectConstants.REASON_ERROR;
        Instant now = Instant.now();
        for (String id : known) {
            if (!consumer.isRunAllowed()) {
                return;
            }
            Exchange exchange = consumer.createExchange(false);
            ObservationMessages.fillFailure(exchange.getMessage(), endpoint.getAgentUrl(), dataItems.get(id), reason,
                    failure.getMessage(), now);
            process(exchange);
            bad.add(id);
        }
    }

    /** Marks the end of an outage when the agent answers again. */
    private void answered() {
        if (outage) {
            outage = false;
            LOG.info("The MTConnect agent {} answers again", endpoint.getAgentUrl());
        }
    }

    private void deliver(Observation observation, String document, String resyncMarker) {
        DataItem item = dataItems.get(observation.dataItemId());
        if (item != null) {
            known.add(observation.dataItemId());
        }
        Exchange exchange = consumer.createExchange(false);
        ObservationMessages.fill(exchange.getMessage(), endpoint.getAgentUrl(), item, observation, instanceId, document,
                resyncMarker);
        process(exchange);
    }

    private void process(Exchange exchange) {
        try {
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            consumer.getExceptionHandler().handleException("Error processing an MTConnect message", exchange,
                    exchange.getException());
        }
        consumer.releaseExchange(exchange, false);
        delivered++;
    }

    private static long next(AgentDocument.Streams streams) throws AgentFailure {
        Long next = streams.header().nextSequence();
        if (next == null) {
            throw new AgentFailure(AgentFailure.Kind.ERROR, "Streams document without nextSequence", null);
        }
        return next;
    }

    /** An error document other than OUT_OF_RANGE, with the agent's error codes and texts as the message. */
    private static AgentFailure agentError(AgentDocument.Error error) {
        return new AgentFailure(AgentFailure.Kind.AGENT_ERROR, error.describe(), null);
    }
}
