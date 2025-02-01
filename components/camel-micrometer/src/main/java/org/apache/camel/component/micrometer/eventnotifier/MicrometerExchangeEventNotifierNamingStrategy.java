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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.apache.camel.component.micrometer.MicrometerConstants.ENDPOINT_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.FAILED_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.KIND;
import static org.apache.camel.component.micrometer.MicrometerConstants.KIND_EXCHANGE;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;

public interface MicrometerExchangeEventNotifierNamingStrategy {

    Predicate<Meter.Id> EVENT_NOTIFIERS
            = id -> KIND_EXCHANGE.equals(id.getTag(KIND));

    /**
     * Default naming strategy that uses micrometer naming convention.
     */
    MicrometerExchangeEventNotifierNamingStrategy DEFAULT = new MicrometerExchangeEventNotifierNamingStrategyDefault();

    /**
     * Naming strategy that uses the classic/legacy naming style (camelCase)
     */
    MicrometerExchangeEventNotifierNamingStrategy LEGACY = new MicrometerExchangeEventNotifierNamingStrategyLegacy();

    String getName(Exchange exchange, Endpoint endpoint);

    // Use the base endpoint to avoid increasing the number
    // of separate events on dynamic endpoints (ie, toD).
    boolean isBaseEndpointURI();

    default String formatName(String name) {
        return name;
    }

    default String getInflightExchangesName() {
        return formatName(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT);
    }

    default Tags getTags(ExchangeEvent event, Endpoint endpoint) {
        String uri = "";
        if (endpoint != null) {
            uri = endpoint.toString();
            if (isBaseEndpointURI()) {
                uri = StringHelper.before(uri, "?", uri);
            }
        }
        String routeId = event.getExchange().getFromRouteId();
        if (routeId != null) {
            return Tags.of(
                    CAMEL_CONTEXT_TAG, event.getExchange().getContext().getName(),
                    KIND, KIND_EXCHANGE,
                    EVENT_TYPE_TAG, event.getClass().getSimpleName(),
                    ROUTE_ID_TAG, routeId,
                    ENDPOINT_NAME, uri,
                    FAILED_TAG, Boolean.toString(event.getExchange().isFailed()));
        } else {
            return Tags.of(
                    CAMEL_CONTEXT_TAG, event.getExchange().getContext().getName(),
                    KIND, KIND_EXCHANGE,
                    EVENT_TYPE_TAG, event.getClass().getSimpleName(),
                    ENDPOINT_NAME, uri,
                    FAILED_TAG, Boolean.toString(event.getExchange().isFailed()));
        }
    }

    default Tags getInflightExchangesTags(CamelContext camelContext, String routeId) {
        return Tags.of(
                CAMEL_CONTEXT_TAG, camelContext.getName(),
                KIND, KIND_EXCHANGE,
                ROUTE_ID_TAG, routeId);
    }
}
