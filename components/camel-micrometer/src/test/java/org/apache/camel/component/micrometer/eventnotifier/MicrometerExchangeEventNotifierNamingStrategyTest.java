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

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.ENDPOINT_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.FAILED_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.KIND;
import static org.apache.camel.component.micrometer.MicrometerConstants.KIND_EXCHANGE;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicrometerExchangeEventNotifierNamingStrategyTest {

    private static final String CONTEXT_NAME = "testContext";
    private static final String ENDPOINT_URI = "seda://in";

    @Test
    void testDefaultFormatName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;

        String result = strategy.formatName("some.metric.name");

        assertEquals("some.metric.name", result);
    }

    @Test
    void testLegacyFormatName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.LEGACY;

        String result = strategy.formatName("some.metric.name");

        assertEquals("SomeMetricName", result);
    }

    @Test
    void getDefaultInflightExchangesName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;
        String result = strategy.getInflightExchangesName();

        assertEquals("camel.exchanges.inflight", result);
    }

    @Test
    void getLegacyInflightExchangesName() {
        MicrometerExchangeEventNotifierNamingStrategy strategy = MicrometerExchangeEventNotifierNamingStrategy.LEGACY;
        String result = strategy.getInflightExchangesName();

        assertEquals("CamelExchangesInflight", result);
    }

    @Test
    void getTagsWhenFromRouteIdIsNotNullShouldIncludeRouteIdTag() {
        final var strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;
        final var exchange = mock(Exchange.class);
        final var context = mock(CamelContext.class);
        final var endpoint = mock(Endpoint.class);

        when(exchange.getFromRouteId()).thenReturn("existingRoute");
        when(exchange.getContext()).thenReturn(context);
        when(exchange.isFailed()).thenReturn(false);
        when(context.getName()).thenReturn(CONTEXT_NAME);
        when(endpoint.toString()).thenReturn(ENDPOINT_URI);

        final var event = new ExchangeSentEvent(exchange, endpoint, 10L);
        final var tags = strategy.getTags(event, endpoint);

        assertThat(tagValue(tags, ROUTE_ID_TAG)).isEqualTo("existingRoute");
        assertThat(tagValue(tags, EVENT_TYPE_TAG)).isEqualTo("ExchangeSentEvent");
    }

    @Test
    void getTagsWhenFromRouteIdIsNullShouldUseEmptyRouteIdTag() {
        final var strategy = MicrometerExchangeEventNotifierNamingStrategy.DEFAULT;
        final var exchange = mock(Exchange.class);
        final var context = mock(CamelContext.class);
        final var endpoint = mock(Endpoint.class);

        when(exchange.getFromRouteId()).thenReturn(null);
        when(exchange.getContext()).thenReturn(context);
        when(exchange.isFailed()).thenReturn(true);
        when(context.getName()).thenReturn(CONTEXT_NAME);
        when(endpoint.toString()).thenReturn("mock://other");

        final var event = new ExchangeSentEvent(exchange, endpoint, 10L);
        final var tags = strategy.getTags(event, endpoint);

        assertThat(tagValue(tags, ROUTE_ID_TAG)).isEmpty();
        assertThat(tagValue(tags, CAMEL_CONTEXT_TAG)).isEqualTo(CONTEXT_NAME);
        assertThat(tagValue(tags, KIND)).isEqualTo(KIND_EXCHANGE);
        assertThat(tagValue(tags, EVENT_TYPE_TAG)).isEqualTo("ExchangeSentEvent");
        assertThat(tagValue(tags, ENDPOINT_NAME)).isEqualTo("mock://other");
        assertThat(tagValue(tags, FAILED_TAG)).isEqualTo("true");
    }

    private static String tagValue(final Tags tags, final String key) {
        return tags.stream()
                .filter(tag -> key.equals(tag.getKey()))
                .map(Tag::getValue)
                .findFirst()
                .orElse(null);
    }

}
