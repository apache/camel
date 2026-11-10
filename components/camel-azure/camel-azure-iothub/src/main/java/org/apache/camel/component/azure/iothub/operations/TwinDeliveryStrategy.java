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
package org.apache.camel.component.azure.iothub.operations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.twin.Twin;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates Azure IoT Hub device twin reported properties from a {@code Map<String, Object>} body.
 *
 * <p>
 * The body must be a {@link Map} of reported properties (any payload decoding, e.g. Protobuf/CloudEvent to Map, is the
 * responsibility of a preceding processor in the route). Updates are delta-based: only changed properties are sent.
 * </p>
 */
public class TwinDeliveryStrategy implements DeliveryStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(TwinDeliveryStrategy.class);

    @Override
    public void execute(DeviceClient client, String deviceId, DeliveryContext context) throws Exception {
        Map<String, Object> twinProperties = context.twinProperties();
        if (twinProperties == null) {
            throw new IllegalArgumentException(
                    "Twin delivery expects twin properties (a Map) to be provided either as the message body "
                                               + "or via the CamelAzureIoTHubTwinProperties header");
        }

        Map<String, Object> propertyMap = clean(twinProperties);
        Twin twin = client.getTwin();
        TwinCollection reportedProperties = twin.getReportedProperties();

        Map<String, Object> delta = computeDelta(propertyMap, reportedProperties);
        if (delta.isEmpty()) {
            LOG.debug("No twin changes for device {}. Skipping update.", deviceId);
            return;
        }

        TwinCollection update = new TwinCollection(delta);
        client.updateReportedProperties(update);
        LOG.debug("Device twin updated for device: {}", deviceId);
    }

    private Map<String, Object> computeDelta(Map<String, Object> newMap, Map<String, Object> existingMap) {
        Map<String, Object> delta = new HashMap<>();
        for (Map.Entry<String, Object> e : newMap.entrySet()) {
            if (!sameValue(e.getValue(), existingMap.get(e.getKey()))) {
                delta.put(e.getKey(), e.getValue());
            }
        }
        return delta;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        if (left instanceof Number && right instanceof Number) {
            return numericEquals((Number) left, (Number) right);
        }
        return Objects.equals(left, right);
    }

    private boolean numericEquals(Number left, Number right) {
        return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> clean(Map<String, Object> map) {
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nested = clean((Map<String, Object>) value);
                if (!nested.isEmpty()) {
                    cleaned.put(entry.getKey(), nested);
                } else {
                    cleaned.put(entry.getKey(), null);
                }
            } else if (value instanceof List<?> list) {
                List<Object> cleanedList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<String, Object> nested = clean((Map<String, Object>) item);
                        if (!nested.isEmpty()) {
                            cleanedList.add(nested);
                        } else {
                            cleanedList.add(null);
                        }
                    } else {
                        cleanedList.add(item);
                    }
                }
                cleaned.put(entry.getKey(), cleanedList);
            } else {
                cleaned.put(entry.getKey(), value);
            }
        }
        return cleaned;
    }

    @Override
    public boolean supports(IoTHubDeliveryMode mode) {
        return mode == IoTHubDeliveryMode.TWIN || mode == IoTHubDeliveryMode.BOTH;
    }
}
