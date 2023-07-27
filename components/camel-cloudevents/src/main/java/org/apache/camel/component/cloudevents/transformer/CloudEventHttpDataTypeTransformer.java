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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Data type represents a default Camel CloudEvent V1 Http binding. The data type reads Camel specific CloudEvent
 * headers and transforms these to Http headers according to the CloudEvents Http binding specification. Sets default
 * values for CloudEvent attributes such as the Http content type header, event source, event type.
 */
@DataTypeTransformer(name = "http:application-cloudevents")
public class CloudEventHttpDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Map<String, Object> headers = message.getHeaders();

        CloudEvent cloudEvent = CloudEvents.v1_0;
        headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).http(),
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_ID, message.getExchange().getExchangeId()));
        headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).http(),
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, cloudEvent.version()));
        headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).http(),
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_TYPE));
        headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).http(),
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_SOURCE));

        if (headers.containsKey(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT)) {
            headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT).http(),
                    headers.get(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        }

        if (headers.containsKey(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE)) {
            headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).http(),
                    headers.get(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE));
        }

        headers.putIfAbsent(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TIME).http(),
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_TIME, cloudEvent.getEventTime(message.getExchange())));
        headers.putIfAbsent(Exchange.CONTENT_TYPE,
                headers.getOrDefault(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "application/json"));

        cloudEvent.attributes().stream().map(CloudEvent.Attribute::id).forEach(headers::remove);
    }
}
