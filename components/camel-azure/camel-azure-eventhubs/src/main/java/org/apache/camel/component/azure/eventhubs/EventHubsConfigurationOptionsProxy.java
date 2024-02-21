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
        return getOption(exchange, EventHubsConstants.PARTITION_KEY, configuration::getPartitionKey, String.class);
    }

    public String getPartitionId(final Exchange exchange) {
        return getOption(exchange, EventHubsConstants.PARTITION_ID, configuration::getPartitionId, String.class);
    }

    public EventHubsConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(
            final Exchange exchange, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(getObjectFromHeaders(exchange, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(exchange, headerName, type);
    }

}
