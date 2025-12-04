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

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.tahu.SparkplugParsingException;
import org.eclipse.tahu.message.PayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.StatePayload;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.mqtt.ClientCallback;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;
import org.eclipse.tahu.util.SparkplugUtil;
import org.eclipse.tahu.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class TahuEdgeClientCallback implements ClientCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeClientCallback.class);

    private TahuEdgeClient client;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final TahuEdgeMetricHandler tahuEdgeNodeMetricHandler;

    private final Marker loggingMarker;

    TahuEdgeClientCallback(EdgeNodeDescriptor edgeNodeDescriptor, TahuEdgeMetricHandler tahuEdgeNodeMetricHandler) {
        this.edgeNodeDescriptor = edgeNodeDescriptor;
        this.tahuEdgeNodeMetricHandler = tahuEdgeNodeMetricHandler;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());
    }

    void setClient(TahuEdgeClient client) {
        this.client = client;
    }

    @Override
    public void messageArrived(
            MqttServerName mqttServerName,
            MqttServerUrl mqttServerUrl,
            MqttClientId mqttClientId,
            String rawTopic,
            MqttMessage mqttMessage) {

        final Topic topic;
        try {
            topic = TopicUtil.parseTopic(rawTopic);
        } catch (SparkplugParsingException e) {
            throw new RuntimeCamelException("Exception caught parsing Sparkplug topic " + rawTopic, e);
        }

        if (!SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX.equals(topic.getNamespace())) {
            LOG.warn(loggingMarker, "Received message on non-Sparkplug topic: {}", topic);
        } else if (topic.isType(MessageType.STATE)) {
            handleSTATEMessage(topic, mqttMessage);
        } else if (topic.isType(MessageType.NDEATH)
                && topic.getEdgeNodeDescriptor().equals(edgeNodeDescriptor)) {
            handleNDEATHMessage(topic, mqttMessage);
        } else if (topic.isType(MessageType.NCMD) || topic.isType(MessageType.DCMD)) {
            handleCMDMessage(topic, mqttMessage);
        } else {
            LOG.debug(loggingMarker, "Received unexpected Sparkplug message of type {} - ignoring", topic.getType());
        }
    }

    void handleSTATEMessage(Topic topic, MqttMessage mqttMessage) {
        LOG.debug(loggingMarker, "Received STATE message: {} :: {}", topic, new String(mqttMessage.getPayload()));

        try {
            ObjectMapper mapper = new ObjectMapper();
            StatePayload statePayload = mapper.readValue(mqttMessage.getPayload(), StatePayload.class);
            client.handleStateMessage(topic.getHostApplicationId(), statePayload);
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Exception caught handling STATE message with topic " + topic + " and payload "
                            + new String(mqttMessage.getPayload()),
                    e);
        }
    }

    void handleNDEATHMessage(Topic topic, MqttMessage mqttMessage) {
        if (!client.isDisconnectedOrDisconnecting()) {
            // MQTT Server published our LWT message before client finished disconnecting

            if (client.isConnectedToPrimaryHost()) {
                // Find payload's bdSeq to determine how to proceed
                long messageBdSeq;
                try {
                    final SparkplugBPayload payload =
                            new SparkplugBPayloadDecoder().buildFromByteArray(mqttMessage.getPayload(), null);
                    messageBdSeq = SparkplugUtil.getBdSequenceNumber(payload);
                } catch (Exception e) {
                    throw new RuntimeCamelException(
                            "Exception caught handling DEATH message while connected to primary host on topic " + topic,
                            e);
                }

                long currentBirthBdSeq = tahuEdgeNodeMetricHandler.getCurrentBirthBdSeq();

                if (currentBirthBdSeq == messageBdSeq) {
                    // This is our latest LWT - treat as a rebirth
                    handleRebirthRequest();
                } else {
                    LOG.warn(
                            loggingMarker,
                            "Received unexpected LWT for {} with different bdSeq - expected {} received {} - ignoring",
                            edgeNodeDescriptor,
                            currentBirthBdSeq,
                            messageBdSeq);
                }
            } else {
                LOG.debug(
                        loggingMarker,
                        "Received unexpected LWT for {} but not connected to primary host - ignoring",
                        edgeNodeDescriptor);
            }
        } else {
            LOG.debug(
                    loggingMarker, "Received expected LWT for {} - no action required", topic.getEdgeNodeDescriptor());
        }
    }

    void handleRebirthRequest() {
        LOG.warn(loggingMarker, "Received unexpected LWT for {} - publishing BIRTH sequence", edgeNodeDescriptor);
        try {
            client.handleRebirthRequest(true);
        } catch (Exception e) {
            LOG.warn(
                    loggingMarker,
                    "Received unexpected LWT but failed to publish new BIRTH sequence for {} - continuing",
                    edgeNodeDescriptor,
                    e);
        }
    }

    void handleCMDMessage(Topic topic, MqttMessage mqttMessage) {
        try {
            PayloadDecoder<SparkplugBPayload> decoder = new SparkplugBPayloadDecoder();
            final SparkplugBPayload payload = decoder.buildFromByteArray(mqttMessage.getPayload(), null);

            if (topic.isType(MessageType.NCMD)) {
                handleNCMDMessage(payload);
            } else if (topic.isType(MessageType.DCMD)) {
                handleDCMDMessage(payload, topic.getDeviceId());
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Exception caught decoding Sparkplug message with topic " + topic + " and payload "
                            + new String(mqttMessage.getPayload()),
                    e);
        }
    }

    void handleNCMDMessage(SparkplugBPayload ncmdPayload) {
        List<Metric> responseMetrics = tahuEdgeNodeMetricHandler.processCMDMetrics(ncmdPayload, edgeNodeDescriptor);

        if (!responseMetrics.isEmpty()) {
            SparkplugBPayload ndataPayload = new SparkplugBPayload.SparkplugBPayloadBuilder()
                    .addMetrics(responseMetrics)
                    .createPayload();

            LOG.debug(loggingMarker, "Publishing NDATA based on NCMD message for {}", edgeNodeDescriptor);

            client.publishNodeData(ndataPayload);
        } else {
            LOG.warn(
                    loggingMarker,
                    "Received NCMD with no valid metrics to write for {} - ignoring",
                    edgeNodeDescriptor);
        }
    }

    void handleDCMDMessage(SparkplugBPayload dcmdPayload, String deviceId) {
        DeviceDescriptor deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, deviceId);

        List<Metric> responseMetrics = tahuEdgeNodeMetricHandler.processCMDMetrics(dcmdPayload, deviceDescriptor);

        if (!responseMetrics.isEmpty()) {
            SparkplugBPayload ddataPayload = new SparkplugBPayload.SparkplugBPayloadBuilder()
                    .addMetrics(responseMetrics)
                    .createPayload();

            LOG.debug(loggingMarker, "Publishing DDATA based on DCMD message for {}", deviceDescriptor);

            client.publishDeviceData(deviceId, ddataPayload);
        } else {
            LOG.warn(loggingMarker, "Received DCMD with no valid metrics to write for {} - ignoring", deviceDescriptor);
        }
    }

    @Override
    public void shutdown() {}

    @Override
    public void connectionLost(
            MqttServerName mqttServerName,
            MqttServerUrl mqttServerUrl,
            MqttClientId mqttClientId,
            Throwable throwable) {}

    @Override
    public void connectComplete(
            boolean reconnect, MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId) {}
}
