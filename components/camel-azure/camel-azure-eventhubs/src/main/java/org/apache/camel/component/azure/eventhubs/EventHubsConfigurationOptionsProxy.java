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
package org.apache.camel.component.azure.eventhubs;

import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link EventHubsConfiguration} and {@link EventHubsConstants}. Ideally this is responsible to
 * obtain the correct configurations options either from configs or exchange headers
 */
public class EventHubsConfigurationOptionsProxy {

    private final EventHubsConfiguration configuration;

    public EventHubsConfigurationOptionsProxy(final EventHubsConfiguration configuration) {
        this.configuration = configuration;
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }

    public String getPartitionKey(final Exchange exchange) {
        return getOption(exchange, EventHubsConstants.PARTITION_KEY, EventHubsConstants.RECEIVED_PARTITION_KEY,
                configuration::getPartitionKey, String.class);
    }

    public String getPartitionId(final Exchange exchange) {
        return getOption(exchange, EventHubsConstants.PARTITION_ID, EventHubsConstants.RECEIVED_PARTITION_ID,
                configuration::getPartitionId, String.class);
    }

    /**
     * The partition key of the event received by an azure-eventhubs consumer, when the exchange comes from one.
     */
    public String getReceivedPartitionKey(final Exchange exchange) {
        return ObjectHelper.isEmpty(exchange)
                ? null
                : exchange.getProperty(EventHubsConstants.RECEIVED_PARTITION_KEY, String.class);
    }

    public EventHubsConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(
            final Exchange exchange, final String headerName, final String receivedPropertyName,
            final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        if (ObjectHelper.isEmpty(exchange)) {
            return fallbackFn.get();
        }
        final R value = getObjectFromHeaders(exchange, headerName, type);
        // a header still holding the value of the received event describes that event, it is not a partition
        // chosen by the route
        if (ObjectHelper.isEmpty(value) || value.equals(exchange.getProperty(receivedPropertyName, type))) {
            return fallbackFn.get();
        }
        return value;
    }

}
