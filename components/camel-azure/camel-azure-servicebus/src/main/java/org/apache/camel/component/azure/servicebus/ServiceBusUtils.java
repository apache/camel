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
package org.apache.camel.component.azure.servicebus;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import org.apache.camel.util.ObjectHelper;

public final class ServiceBusUtils {

    private ServiceBusUtils() {
    }

    public static ServiceBusMessage createServiceBusMessage(
            final Object data, final Map<String, Object> applicationProperties, final String correlationId) {
        ServiceBusMessage serviceBusMessage;
        if (data instanceof String) {
            serviceBusMessage = new ServiceBusMessage((String) data);
        } else if (data instanceof byte[]) {
            serviceBusMessage = new ServiceBusMessage((byte[]) data);
        } else if (data instanceof BinaryData) {
            serviceBusMessage = new ServiceBusMessage((BinaryData) data);
        } else {
            throw new IllegalArgumentException("Make sure your message data is in String, byte[] or BinaryData");
        }
        if (applicationProperties != null) {
            serviceBusMessage.getRawAmqpMessage().getApplicationProperties().putAll(applicationProperties);
        }
        if (ObjectHelper.isNotEmpty(correlationId)) {
            serviceBusMessage.setCorrelationId(correlationId);
        }
        return serviceBusMessage;
    }

    public static Iterable<ServiceBusMessage> createServiceBusMessages(
            final Iterable<?> data, final Map<String, Object> applicationProperties, final String correlationId) {
        return StreamSupport.stream(data.spliterator(), false)
                .map(obj -> createServiceBusMessage(obj, applicationProperties, correlationId))
                .collect(Collectors.toList());
    }
}
