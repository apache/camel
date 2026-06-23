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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.CamelJmxConfig;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.assertj.core.api.Assertions.assertThat;

public class MicrometerExchangeEventNotifierProducerTemplateTest extends AbstractMicrometerEventNotifierTest {

    private PrometheusMeterRegistry prometheusRegistry;

    private static final String DIRECT_TRIGGER = "direct:trigger";
    private static final String SEDA_WORKER = "seda:worker";
    private static final String MOCK_RESULT = "mock:result";
    private static final String PRODUCER_ROUTE_ID = "producer-route";
    private static final String WORKER_ROUTE_ID = "worker-route";

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        return new MicrometerExchangeEventNotifier();
    }

    @Override
    public void addRegistry() {
        meterRegistry = new CompositeMeterRegistry();
        meterRegistry.add(new SimpleMeterRegistry());
        prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        meterRegistry.add(prometheusRegistry);
        meterRegistry.add(new JmxMeterRegistry(CamelJmxConfig.DEFAULT, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT));
    }

    @Test
    void notify_whenProducerTemplateAndRouteExchangesAreMixed_shouldRegisterTimersWithConsistentTagKeys() throws Exception {
        final var mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(2);

        template.sendBody(DIRECT_TRIGGER, "from-route");
        template.sendBody(SEDA_WORKER, "from-producer-template");

        mock.assertIsSatisfied();

        final var exchangeEventTimers = meterRegistry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME.equals(id.getName()))
                .toList();

        final var tagKeySets = exchangeEventTimers.stream()
                .map(id -> id.getTags().stream().map(Tag::getKey).sorted().toList())
                .distinct()
                .toList();

        assertThat(exchangeEventTimers).isNotEmpty();
        assertThat(tagKeySets).hasSize(1);
        assertThat(exchangeEventTimers)
                .allSatisfy(id -> assertThat(id.getTags().stream().map(Tag::getKey))
                        .contains(ROUTE_ID_TAG));
        assertThat(prometheusRegistry.scrape()).isNotBlank();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_TRIGGER).routeId(PRODUCER_ROUTE_ID)
                        .to(SEDA_WORKER);
                from(SEDA_WORKER).routeId(WORKER_ROUTE_ID)
                        .to(MOCK_RESULT);
            }
        };
    }

}
