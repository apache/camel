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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.camel.component.tahu.TahuException;
import org.eclipse.tahu.edge.api.MetricHandler;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class TahuEdgeMetricHandler implements MetricHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeMetricHandler.class);

    private final BdSeqManager bdSeqManager;
    private volatile long currentBirthBdSeq;
    private volatile long currentDeathBdSeq;

    private TahuEdgeClient client;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final ConcurrentMap<SparkplugDescriptor, SparkplugBPayloadMap> descriptorMetricMap =
            new ConcurrentHashMap<>();

    private final Marker loggingMarker;

    TahuEdgeMetricHandler(EdgeNodeDescriptor edgeNodeDescriptor, BdSeqManager bdSeqManager) {
        this.edgeNodeDescriptor = edgeNodeDescriptor;
        this.bdSeqManager = bdSeqManager;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        currentBirthBdSeq = currentDeathBdSeq = bdSeqManager.getNextDeathBdSeqNum();
    }

    void setClient(TahuEdgeClient client) {
        this.client = client;
    }

    @Override
    public Topic getDeathTopic() {
        return new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, edgeNodeDescriptor, MessageType.NDEATH);
    }

    @Override
    public byte[] getDeathPayloadBytes() throws Exception {
        currentDeathBdSeq &= 0xFFL;

        SparkplugBPayload deathPayload = new SparkplugBPayload.SparkplugBPayloadBuilder()
                .addMetric(new MetricBuilder(
                                SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY, MetricDataType.Int64, currentDeathBdSeq)
                        .createMetric())
                .createPayload();

        LOG.debug(loggingMarker, "Created death payload with bdSeq metric {}", currentDeathBdSeq);

        currentBirthBdSeq = currentDeathBdSeq++;
        bdSeqManager.storeNextDeathBdSeqNum(currentDeathBdSeq);

        SparkplugBPayloadEncoder encoder = new SparkplugBPayloadEncoder();

        return encoder.getBytes(deathPayload, true);
    }

    @Override
    public boolean hasMetric(SparkplugDescriptor sparkplugDescriptor, String metricName) {
        return descriptorMetricMap.containsKey(sparkplugDescriptor)
                && descriptorMetricMap.get(sparkplugDescriptor).getMetric(metricName) != null;
    }

    @Override
    public void publishBirthSequence() {
        try {
            Date timestamp = new Date();

            // SparkplugBPayloadMap, not a SparkplugBPayload
            SparkplugBPayloadMap nBirthPayload = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .setTimestamp(timestamp)
                    .addMetrics(getCachedMetrics(edgeNodeDescriptor))
                    .addMetric(new MetricBuilder(
                                    SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                                    MetricDataType.Int64,
                                    currentBirthBdSeq)
                            .createMetric())
                    .createPayload();

            LOG.debug(loggingMarker, "Created birth payload with bdSeq metric {}", currentBirthBdSeq);

            client.publishNodeBirth(nBirthPayload);

            descriptorMetricMap.keySet().stream()
                    .filter(sd -> sd.isDeviceDescriptor())
                    .forEach(sd -> {
                        DeviceDescriptor deviceDescriptor = (DeviceDescriptor) sd;
                        String deviceId = deviceDescriptor.getDeviceId();

                        SparkplugBPayload dBirthPayload = new SparkplugBPayload.SparkplugBPayloadBuilder()
                                .setTimestamp(timestamp)
                                .addMetrics(getCachedMetrics(deviceDescriptor))
                                .createPayload();

                        client.publishDeviceBirth(deviceId, dBirthPayload);
                    });

        } catch (Exception e) {
            throw new TahuException(edgeNodeDescriptor, "Exception caught publishing birth sequence", e);
        }
    }

    SparkplugBPayloadMap addDeviceMetricDataPayloadMap(
            SparkplugDescriptor metricDescriptor, SparkplugBPayloadMap metricDataTypePayloadMap) {
        return descriptorMetricMap.put(metricDescriptor, metricDataTypePayloadMap);
    }

    List<Metric> getCachedMetrics(SparkplugDescriptor sd) {
        return Optional.ofNullable(descriptorMetricMap.get(sd))
                .map(SparkplugBPayloadMap::getMetrics)
                .orElse(List.of());
    }

    SparkplugBPayloadMap getDescriptorMetricMap(SparkplugDescriptor sd) {
        return descriptorMetricMap.get(sd);
    }

    void updateCachedMetrics(SparkplugDescriptor sd, SparkplugBPayload payload) {
        Optional.ofNullable(descriptorMetricMap.get(sd)).ifPresent(cachedMetrics -> {
            payload.getMetrics().stream().forEach(payloadMetric -> {
                cachedMetrics.updateMetricValue(payloadMetric.getName(), payloadMetric, null);
            });
        });
    }

    long getCurrentBirthBdSeq() {
        LOG.trace(loggingMarker, "getCurrentBirthBdSeq() : {}", currentBirthBdSeq);
        return currentBirthBdSeq;
    }

    long getCurrentDeathBdSeq() {
        LOG.trace(loggingMarker, "getCurrentDeathBdSeq() : {}", currentDeathBdSeq);
        return currentDeathBdSeq;
    }

    List<Metric> processCMDMetrics(SparkplugBPayload payload, SparkplugDescriptor cmdDescriptor) {
        List<Metric> receivedMetrics = payload.getMetrics();
        if (receivedMetrics == null || receivedMetrics.isEmpty()) {
            return List.of();
        }

        // Look for a METRIC_NODE_REBIRTH received metric with True value
        if (!cmdDescriptor.isDeviceDescriptor()) {
            Map<Boolean, List<Metric>> groupedMetrics = receivedMetrics.stream()
                    .collect(Collectors.groupingBy(m -> (Boolean) SparkplugMeta.METRIC_NODE_REBIRTH.equals(m.getName())
                            && m.getDataType() == MetricDataType.Boolean
                            && (Boolean) m.getValue()));

            if (groupedMetrics.containsKey(Boolean.TRUE)
                    && !groupedMetrics.get(Boolean.TRUE).isEmpty()) {
                client.handleRebirthRequest(true);
            }

            receivedMetrics = groupedMetrics.get(Boolean.FALSE);
        }

        final SparkplugBPayloadMap cachedMetrics = descriptorMetricMap.get(cmdDescriptor);
        if (cachedMetrics == null) {
            return List.of();
        }

        return receivedMetrics.stream()
                .map(m -> getCachedMetric(m.getName(), cachedMetrics, cmdDescriptor))
                .filter(Objects::nonNull)
                .toList();
    }

    private Metric getCachedMetric(
            String metricName, SparkplugBPayloadMap cachedMetrics, SparkplugDescriptor cmdDescriptor) {
        Metric cachedMetric = cachedMetrics.getMetric(metricName);

        if (cachedMetric == null) {
            LOG.warn(
                    loggingMarker,
                    "Received CMD request for {} metric {} not in configured metrics - skipping",
                    cmdDescriptor,
                    metricName);
            return null;
        }

        try {
            Metric responseMetric = new Metric(cachedMetric);

            responseMetric.setHistorical(true);

            return responseMetric;
        } catch (Exception e) {
            LOG.warn(
                    loggingMarker,
                    "Exception caught copying metric handling CMD request for {} metric {} - skipping",
                    cmdDescriptor,
                    metricName);
            return null;
        }
    }
}
