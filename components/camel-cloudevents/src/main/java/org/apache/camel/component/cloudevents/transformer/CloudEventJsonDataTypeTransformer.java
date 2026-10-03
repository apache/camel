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

package org.apache.camel.component.cloudevents.transformer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

/**
 * Data type represents a default Camel CloudEvent V1 Json format binding. The data type reads Camel specific CloudEvent
 * headers and transforms these to a Json object representing the CloudEvents Json format specification. Sets default
 * values for CloudEvent attributes such as the Http content type header, event source, event type.
 */
@DataTypeTransformer(name = "application-cloudevents+json",
                     description = "Adds default CloudEvent (JSon binding) headers to the Camel message (such as content-type, event source, event type etc.)")
public class CloudEventJsonDataTypeTransformer extends Transformer {

    public static final String APPLICATION_CLOUDEVENTS_JSON = "application/cloudevents+json";
    public static final String APPLICATION_JSON = "application/json";

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Map<String, Object> headers = message.getHeaders();

        String dataContentType = headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, APPLICATION_JSON).toString();
        if (!APPLICATION_CLOUDEVENTS_JSON.equals(dataContentType)) {
            Map<String, Object> cloudEventAttributes = new HashMap<>();
            CloudEvent cloudEvent = CloudEvents.v1_0;
            for (CloudEvent.Attribute attribute : cloudEvent.attributes()) {
                if (headers.containsKey(attribute.id())) {
                    cloudEventAttributes.put(attribute.json(), headers.get(attribute.id()));
                }
            }

            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).json(),
                    cloudEvent.version());
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).json(),
                    message.getExchange().getExchangeId());
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).json(),
                    CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_TYPE);
            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).json(),
                    CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_SOURCE);

            cloudEventAttributes.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TIME).json(),
                    cloudEvent.getEventTime(message.getExchange()));

            String body = MessageHelper.extractBodyAsString(message);
            cloudEventAttributes.putIfAbsent("data", body);
            cloudEventAttributes.putIfAbsent(
                    cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json(), dataContentType);

            headers.put(Exchange.CONTENT_TYPE, APPLICATION_CLOUDEVENTS_JSON);

            message.setBody(createCouldEventJsonObject(cloudEventAttributes));

            cloudEvent.attributes().stream().map(CloudEvent.Attribute::id).forEach(headers::remove);
        }
    }

    private String createCouldEventJsonObject(Map<String, Object> cloudEventAttributes) {
        StringBuilder builder = new StringBuilder("{");

        cloudEventAttributes.forEach((key, value) -> {
            builder.append(" ");
            appendJsonString(builder, key);
            builder.append(":");
            if ("data".equals(key) && value instanceof String data && isJson(data)) {
                // set Json data as nested object in the data field
                builder.append(data);
            } else {
                appendJsonString(builder, String.valueOf(value));
            }
            builder.append(",");
        });

        if (!cloudEventAttributes.isEmpty()) {
            builder.deleteCharAt(builder.lastIndexOf(","));
        }

        return builder.append("}").toString();
    }

    /**
     * Appends the value as a Json string, escaping the quote, the backslash and the control characters (RFC 8259,
     * section 7).
     */
    private static void appendJsonString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    /**
     * Whether the data is a Json object or array, which is then set as nested Json value. Text that only starts like
     * Json (such as a log line "[INFO] ...") is set as a Json string.
     */
    private static boolean isJson(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }

        String trimmed = data.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }
        try {
            Jsoner.deserialize(trimmed);
            return true;
        } catch (DeserializationException e) {
            return false;
        }
    }
}
