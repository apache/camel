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

import java.util.Map;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ENDPOINT_NAME_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Test OpenTelemetryExchangeEventNotifier with dynamic endpoint URIs.
 */
public class OpenTelemetryExchangeEventNotifierDynamicTest extends AbstractOpenTelemetryTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryExchangeEventNotifier eventNotifier = getEventNotifier();
        context.getManagementStrategy().addEventNotifier(eventNotifier);
        eventNotifier.init();
        return context;
    }

    protected OpenTelemetryExchangeEventNotifier getEventNotifier() {
        OpenTelemetryExchangeEventNotifier eventNotifier = new OpenTelemetryExchangeEventNotifier();
        eventNotifier.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        // create metrics for each dynamic endpoint URI
        eventNotifier.setBaseEndpointURI(false);
        return eventNotifier;
    }

    @Test
    public void testEventNotifier() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint("mock://out");
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct://in", i);
        }

        mock.assertIsSatisfied();

        int nameCount = 0;
        for (PointData pd : getAllPointDataForRouteId(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER, "test")) {
            String name = pd.getAttributes().get(stringKey(ENDPOINT_NAME_ATTRIBUTE));
            // should have recorded metrics for each dynamic endpoint name, e.g. mc://component?clear=val-0&password=xxxxxx
            if (name != null && name.startsWith("mc://component")) {
                nameCount++;
                assertInstanceOf(HistogramPointData.class, pd);
                HistogramPointData hpd = (HistogramPointData) pd;
                assertEquals(1, hpd.getCount());
            }
        }
        assertEquals(count, nameCount, "number of 'mc://component' endpoints should equal the number of exchanges.");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://in")
                        .routeId("test")
                        .toD("mc:component?clear=val-${body}&password=secret-${body}")
                        .to("mock://out");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("mc", new MyComponent());
    }

    private class MyComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyEndpoint(uri, this, parameters);
        }
    }

    private class MyEndpoint extends DefaultEndpoint {
        private final String password;
        private final String clear;

        MyEndpoint(String uri, MyComponent myComponent, Map<String, Object> parameters) {
            super(uri, myComponent);
            this.clear = parameters.remove("clear").toString();
            this.password = parameters.remove("password").toString();
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    // noop
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
