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
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Message;

/**
 * The one mapping from an observation to the {@code CamelMTConnect*} headers, used for consumer messages and producer
 * replies alike, and the Bad message of a data item whose agent cannot be read. The probe's data item supplies the
 * category, type and units; the observation supplies what the probe lacks.
 */
final class ObservationMessages {

    /** The key of the value in an observation map of a producer reply. */
    static final String VALUE = "value";

    private static final String DEFAULT_REPRESENTATION = "VALUE";
    private static final String HEADER_PREFIX = "CamelMTConnect";

    private ObservationMessages() {
    }

    /**
     * Fills a consumer message: the value as the body and the metadata as headers.
     *
     * @param item     the probe's data item, or null when the probe did not list it
     * @param document Current or Sample
     * @param resync   the resync marker, or null
     */
    static void fill(
            Message message, String agent, DataItem item, Observation observation, Long instanceId, String document,
            String resync) {
        message.setBody(observation.value());
        message.getHeaders().putAll(headers(agent, item, observation, instanceId, document, resync));
    }

    /**
     * The observation as an entry of a producer reply: the header names without the {@code CamelMTConnect} prefix, and
     * the value under {@value #VALUE}.
     */
    static Map<String, Object> map(
            String agent, DataItem item, Observation observation, Long instanceId, String document) {
        Map<String, Object> map = new LinkedHashMap<>();
        headers(agent, item, observation, instanceId, document, null)
                .forEach((name, value) -> map.put(name.substring(HEADER_PREFIX.length()), value));
        map.put(VALUE, observation.value());
        return map;
    }

    /** The headers of an observation, in a stable order; attributes without a value are left out. */
    static Map<String, Object> headers(
            String agent, DataItem item, Observation observation, Long instanceId, String document, String resync) {
        Map<String, Object> headers = new LinkedHashMap<>();
        put(headers, MTConnectConstants.AGENT, agent);
        put(headers, MTConnectConstants.DEVICE_UUID, observation.deviceUuid());
        put(headers, MTConnectConstants.DEVICE_NAME, observation.deviceName());
        put(headers, MTConnectConstants.COMPONENT_ID, observation.componentId());
        put(headers, MTConnectConstants.COMPONENT_TYPE, observation.componentType());
        put(headers, MTConnectConstants.COMPONENT_NAME, observation.componentName());
        put(headers, MTConnectConstants.DATA_ITEM_ID, observation.dataItemId());
        if (item != null) {
            put(headers, MTConnectConstants.DATA_ITEM_NAME, item.name());
            put(headers, MTConnectConstants.CATEGORY, item.category());
            put(headers, MTConnectConstants.TYPE, item.type());
            put(headers, MTConnectConstants.SUB_TYPE, item.subType());
            dataItemUnits(headers, item);
        }
        // what the probe does not name comes from the observation
        putMissing(headers, MTConnectConstants.DATA_ITEM_NAME, observation.name());
        putMissing(headers, MTConnectConstants.CATEGORY, observation.category());
        putMissing(headers, MTConnectConstants.TYPE, observation.type());
        putMissing(headers, MTConnectConstants.SUB_TYPE, observation.subType());
        put(headers, MTConnectConstants.SEQUENCE, observation.sequence());
        put(headers, MTConnectConstants.TIMESTAMP, observation.timestamp());
        put(headers, MTConnectConstants.INSTANCE_ID, instanceId);
        put(headers, MTConnectConstants.QUALITY,
                observation.unavailable() ? MTConnectConstants.QUALITY_BAD : MTConnectConstants.QUALITY_GOOD);
        if (observation.unavailable()) {
            put(headers, MTConnectConstants.REASON, MTConnectConstants.REASON_UNAVAILABLE);
        }
        Observation.Attributes attributes = observation.attributes();
        if (observation.category().equals("CONDITION")) {
            put(headers, MTConnectConstants.LEVEL, observation.elementName());
            put(headers, MTConnectConstants.NATIVE_CODE, attributes.nativeCode());
            put(headers, MTConnectConstants.NATIVE_SEVERITY, attributes.nativeSeverity());
            put(headers, MTConnectConstants.QUALIFIER, attributes.qualifier());
        }
        put(headers, MTConnectConstants.COMPOSITION_ID, attributes.compositionId());
        put(headers, MTConnectConstants.RESET_TRIGGERED, attributes.resetTriggered());
        put(headers, MTConnectConstants.STATISTIC, attributes.statistic());
        put(headers, MTConnectConstants.DURATION, attributes.duration());
        put(headers, MTConnectConstants.SAMPLE_RATE, attributes.sampleRate());
        put(headers, MTConnectConstants.ASSET_TYPE, attributes.assetType());
        put(headers, MTConnectConstants.DOCUMENT, document);
        put(headers, MTConnectConstants.RESYNC, resync);
        return headers;
    }

    /**
     * Fills the Bad message of a data item whose agent cannot be read: no body, no sequence, the detection time.
     *
     * @param reason UNREACHABLE or ERROR
     * @param error  the failure as text
     */
    static void fillFailure(Message message, String agent, DataItem item, String reason, String error, Instant now) {
        Map<String, Object> headers = new LinkedHashMap<>();
        put(headers, MTConnectConstants.AGENT, agent);
        put(headers, MTConnectConstants.DEVICE_UUID, item.deviceUuid());
        put(headers, MTConnectConstants.DEVICE_NAME, item.deviceName());
        put(headers, MTConnectConstants.COMPONENT_ID, item.componentId());
        put(headers, MTConnectConstants.COMPONENT_TYPE, item.componentType());
        put(headers, MTConnectConstants.COMPONENT_NAME, item.componentName());
        put(headers, MTConnectConstants.DATA_ITEM_ID, item.id());
        put(headers, MTConnectConstants.DATA_ITEM_NAME, item.name());
        put(headers, MTConnectConstants.CATEGORY, item.category());
        put(headers, MTConnectConstants.TYPE, item.type());
        put(headers, MTConnectConstants.SUB_TYPE, item.subType());
        dataItemUnits(headers, item);
        put(headers, MTConnectConstants.TIMESTAMP, now);
        put(headers, MTConnectConstants.QUALITY, MTConnectConstants.QUALITY_BAD);
        put(headers, MTConnectConstants.REASON, reason);
        put(headers, MTConnectConstants.ERROR, error);
        message.setBody(null);
        message.getHeaders().putAll(headers);
    }

    private static void dataItemUnits(Map<String, Object> headers, DataItem item) {
        put(headers, MTConnectConstants.UNITS, item.units());
        put(headers, MTConnectConstants.NATIVE_UNITS, item.nativeUnits());
        put(headers, MTConnectConstants.REPRESENTATION,
                item.representation() != null ? item.representation() : DEFAULT_REPRESENTATION);
    }

    private static void putMissing(Map<String, Object> headers, String name, Object value) {
        if (value != null) {
            headers.putIfAbsent(name, value);
        }
    }

    private static void put(Map<String, Object> headers, String name, Object value) {
        if (value != null) {
            headers.put(name, value);
        }
    }
}
