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

package org.apache.camel.component.azure.eventhubs.transform;

import java.util.Map;

import org.apache.camel.Message;
import org.apache.camel.component.azure.eventhubs.EventHubsConstants;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Data type transformer converts Azure Eventhubs consumer response to CloudEvent v1_0 data format. The data type sets
 * Camel specific CloudEvent headers with values extracted from Azure Eventhubs consumer response.
 */
@DataTypeTransformer(name = "azure-eventhubs:application-cloudevents",
                     description = "Adds CloudEvent headers to the Camel message with Azure Eventhubs consumer response details")
public class EventHubsCloudEventDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Map<String, Object> headers = message.getHeaders();

        CloudEvent cloudEvent = CloudEvents.v1_0;
        headers.putIfAbsent(CloudEvent.CAMEL_CLOUD_EVENT_ID, message.getExchange().getExchangeId());
        headers.putIfAbsent(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, cloudEvent.version());
        headers.put(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event.azure.eventhubs.consume");

        if (message.getHeaders().containsKey(EventHubsConstants.PARTITION_ID)) {
            headers.put(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE,
                    "azure.eventhubs." + message.getHeader(EventHubsConstants.PARTITION_ID, String.class));
        }

        headers.put(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, message.getHeader(EventHubsConstants.SEQUENCE_NUMBER, Long.class));
        headers.put(CloudEvent.CAMEL_CLOUD_EVENT_TIME, cloudEvent.getEventTime(message.getExchange()));
        headers.put(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, CloudEvent.APPLICATION_OCTET_STREAM_MIME_TYPE);
    }
}
