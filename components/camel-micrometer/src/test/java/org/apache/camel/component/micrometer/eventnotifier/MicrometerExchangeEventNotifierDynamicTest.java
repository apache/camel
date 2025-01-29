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

import java.util.Map;
import java.util.Set;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MicrometerExchangeEventNotifierDynamicTest extends AbstractMicrometerEventNotifierTest {

    MicrometerExchangeEventNotifier eventNotifier = new MicrometerExchangeEventNotifier();

    private static final String ROUTE_ID = "test";
    private static final String MOCK_OUT = "mock://out";
    private static final String DIRECT_IN = "direct://in";

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        return eventNotifier;
    }

    @Test
    public void testBaseEndpointURI() throws Exception {
        this.eventNotifier.setBaseEndpointURI(true);
        int count = 10;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, i);
        }

        mock.assertIsSatisfied();

        // Let's calculate the number of entries hold by the meter registry.
        // We need to scan the entire data structure to make sure only one
        // entry exists.
        Set<MeterRegistry> set = meterRegistry.getRegistries();
        assertEquals(2, set.size());
        for (MeterRegistry mr : set) {
            assertEquals(5, mr.getMeters().size());
            int counter = 0;
            for (Meter m : mr.getMeters()) {
                if (m.getId().getName().equals(MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME) &&
                        m.getId().getTag("endpointName").equals("my://component")) {
                    counter++;
                    Measurement entry = null;
                    for (Measurement me : m.measure()) {
                        if (Statistic.COUNT.equals(Statistic.valueOf(me.getStatistic().name()))) {
                            entry = me;
                        }
                    }
                    assertNotNull(entry);
                    assertEquals(count, entry.getValue());
                }
            }
            assertEquals(1, counter, "Only one measure should be present for 'my://component' endpoint.");
        }
    }

    @Test
    public void testFullEndpointURI() throws Exception {
        this.eventNotifier.setBaseEndpointURI(false);
        int count = 10;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, i);
        }

        mock.assertIsSatisfied();

        // Let's calculate the number of entries hold by the meter registry.
        // We need to scan the entire data structure to make all entries exists.
        Set<MeterRegistry> set = meterRegistry.getRegistries();
        assertEquals(2, set.size());
        for (MeterRegistry mr : set) {
            assertEquals(14, mr.getMeters().size());
            int counter = 0;
            for (Meter m : mr.getMeters()) {
                if (m.getId().getName().equals(MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME) &&
                        m.getId().getTag("endpointName").startsWith("my://component")) {
                    counter++;
                    Measurement entry = null;
                    for (Measurement me : m.measure()) {
                        if (Statistic.COUNT.equals(Statistic.valueOf(me.getStatistic().name()))) {
                            entry = me;
                        }
                    }
                    assertNotNull(entry);
                    assertEquals(1, entry.getValue());
                }
            }
            assertEquals(count, counter, "It must exist as many 'my://component' endpoint as the number of exchanges.");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_IN)
                        .routeId(ROUTE_ID)
                        .toD("my:component?clear=val-${body}&password=secret-${body}")
                        .to(MOCK_OUT);
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("my", new MyComponent());
    }

    @Component("my")
    class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            return new MyEndpoint(uri, this, parameters);
        }

    }

    @UriEndpoint(scheme = "my", syntax = "my", title = "my")
    class MyEndpoint extends DefaultEndpoint {

        @UriParam(label = "common,security", secret = true)
        private String password;
        @UriParam(label = "common")
        private String clear;

        MyEndpoint(String uri, MyComponent myComponent, Map<String, Object> parameters) {
            super(uri, myComponent);
            this.clear = parameters.remove("clear").toString();
            this.password = parameters.remove("password").toString();
        }

        @Override
        public Producer createProducer() throws Exception {
            return new DefaultProducer(this) {

                @Override
                public void process(Exchange exchange) throws Exception {
                    // NOOP
                }

            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new DefaultConsumer(this, processor);
        }

    }

}
