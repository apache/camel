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

package org.apache.camel.opentelemetry.metrics.eventnotifier;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.CAMEL_CONTEXT_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ENDPOINT_NAME_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.FAILED_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_EXCHANGE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.util.StringHelper;

public interface OpenTelemetryExchangeEventNotifierNamingStrategy {

    /**
     * Default naming strategy.
     */
    OpenTelemetryExchangeEventNotifierNamingStrategy DEFAULT = new OpenTelemetryExchangeEventNotifierNamingStrategy() {
        @Override
        public String getSentTimerName() {
            return DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
        }

        @Override
        public String getElapsedTimerName() {
            return DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER;
        }

        @Override
        public String getInflightExchangesName() {
            return DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
        }

        @Override
        public String getLastProcessedTimeName() {
            return DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT;
        }
    };

    String getSentTimerName();

    String getElapsedTimerName();

    String getInflightExchangesName();

    String getLastProcessedTimeName();

    default Attributes getAttributes(ExchangeEvent event, Endpoint endpoint, boolean isBaseEndpointURI) {
        String uri = "";
        if (endpoint != null) {
            uri = endpoint.toString();
            if (isBaseEndpointURI) {
                uri = StringHelper.before(uri, "?", uri);
            }
        }
        Exchange exchange = event.getExchange();
        String routeId = exchange.getFromRouteId();

        AttributesBuilder builder = Attributes.builder();
        builder.put(
                        AttributeKey.stringKey(CAMEL_CONTEXT_ATTRIBUTE),
                        exchange.getContext().getName())
                .put(AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_EXCHANGE)
                .put(
                        AttributeKey.stringKey(EVENT_TYPE_ATTRIBUTE),
                        event.getClass().getSimpleName())
                .put(AttributeKey.stringKey(ENDPOINT_NAME_ATTRIBUTE), uri)
                .put(AttributeKey.stringKey(FAILED_ATTRIBUTE), Boolean.toString(exchange.isFailed()));
        if (routeId != null) {
            builder.put(AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE), routeId);
        }
        return builder.build();
    }

    default Attributes getInflightExchangesAttributes(CamelContext camelContext, String routeId) {
        return Attributes.of(
                AttributeKey.stringKey(CAMEL_CONTEXT_ATTRIBUTE), camelContext == null ? "" : camelContext.getName(),
                AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_EXCHANGE,
                AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE), routeId);
    }
}
