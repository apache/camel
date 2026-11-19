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
package org.apache.camel.processor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.component.PollDynamicAwareSupport;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Companion to {@link DynamicEndpointMessageOptionLiteralTest}: a {@code RAW(...)} option value of an endpoint created
 * from a uri computed from the current message is unwrapped, but taken as literal text: a {@code $simple{...}} in it is
 * not evaluated. An endpoint written in the route (such as {@code to("log:x?marker=RAW($simple{...})")}) still
 * evaluates it.
 */
class DynamicEndpointMessageRawOptionTest extends ContextTestSupport {

    private static final String PROP = "camel.test.rawOption." + System.nanoTime();
    private static final String PROP_VALUE = "prop-value-" + System.nanoTime();
    private static final AtomicInteger HITS = new AtomicInteger();

    @BeforeAll
    static void setSystemProperty() {
        System.setProperty(PROP, PROP_VALUE);
    }

    @AfterAll
    static void clearSystemProperty() {
        System.clearProperty(PROP);
    }

    @BeforeEach
    void resetHits() {
        HITS.set(0);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("probe", new Probe());
        return context;
    }

    @Test
    void toDRawSystemPropertyFunctionIsLiteral() {
        assertToDMarkerIsLiteral("sys", "$simple{sys." + PROP + "}");
    }

    @Test
    void toDRawEnvFunctionIsLiteral() {
        assertToDMarkerIsLiteral("env", "$simple{env.HOME}");
    }

    @Test
    void toDRawBeanFunctionIsLiteral() {
        assertToDMarkerIsLiteral("bean", "$simple{bean:probe?method=hit}");
        assertThat(HITS.get()).isZero();
    }

    @Test
    void toDRawCamelContextFunctionIsLiteral() {
        assertToDMarkerIsLiteral("ctx", "$simple{camelContext.getName()}");
    }

    @Test
    void toDRawValueIsStillUnwrapped() {
        assertToDMarkerIsLiteral("plain", "a+b&c=d");
    }

    @Test
    void toDRawPropertyPlaceholderInRouteIsResolved() {
        template.sendBodyAndHeader("direct:todph", "Hello", "id", "ph");

        assertThat(markerOf("log://todph-ph")).isEqualTo(PROP_VALUE);
    }

    @Test
    void staticEndpointRawSimpleIsEvaluated() {
        template.sendBody("direct:static", "Hello");

        assertThat(markerOf("log://static")).isEqualTo(PROP_VALUE);
    }

    @Test
    void sendDynamicAwareRawSimpleIsLiteral() throws Exception {
        LenientSendDynamicAware aware = new LenientSendDynamicAware();
        aware.setCamelContext(context);
        aware.setScheme("log");
        aware.init();
        Exchange exchange = new DefaultExchange(context);
        String uri = "log:aware?marker=RAW($simple{sys." + PROP + "})&custom=RAW($simple{bean:probe?method=hit})";

        Map<String, Object> known = aware.endpointProperties(exchange, uri);
        Map<String, Object> lenient = aware.endpointLenientProperties(exchange, uri);

        assertThat(known).containsEntry("marker", "$simple{sys." + PROP + "}");
        assertThat(lenient).containsEntry("custom", "$simple{bean:probe?method=hit}");
        assertThat(HITS.get()).isZero();
    }

    @Test
    void pollDynamicAwareRawSimpleIsLiteral() throws Exception {
        LenientPollDynamicAware aware = new LenientPollDynamicAware();
        aware.setCamelContext(context);
        aware.setScheme("log");
        aware.init();
        Exchange exchange = new DefaultExchange(context);
        String uri = "log:aware?marker=RAW($simple{sys." + PROP + "})&custom=RAW($simple{bean:probe?method=hit})";

        Map<String, Object> known = aware.endpointProperties(exchange, uri);
        Map<String, Object> lenient = aware.endpointLenientProperties(exchange, uri);

        assertThat(known).containsEntry("marker", "$simple{sys." + PROP + "}");
        assertThat(lenient).containsEntry("custom", "$simple{bean:probe?method=hit}");
        assertThat(HITS.get()).isZero();
    }

    private void assertToDMarkerIsLiteral(String id, String value) {
        template.sendBodyAndHeaders("direct:tod", "Hello", Map.of("id", id, "m", "RAW(" + value + ")"));

        assertThat(markerOf("log://tod-" + id)).isEqualTo(value);
    }

    private String markerOf(String uriPrefix) {
        for (Endpoint e : context.getEndpoints()) {
            if (e instanceof LogEndpoint le && e.getEndpointUri().startsWith(uriPrefix)) {
                return le.getMarker();
            }
        }
        throw new AssertionError("No log endpoint for " + uriPrefix);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tod").toD("log:tod-${header.id}?marker=${header.m}");
                from("direct:todph").toD("log:todph-${header.id}?marker=RAW({{sys:" + PROP + "}})");
                from("direct:static").to("log:static?marker=RAW($simple{sys." + PROP + "})");
            }
        };
    }

    public static class Probe {
        public String hit() {
            HITS.incrementAndGet();
            return "probe-called";
        }
    }

    private static final class LenientSendDynamicAware extends SendDynamicAwareSupport {
        @Override
        public boolean isLenientProperties() {
            return true;
        }

        @Override
        public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) {
            return null;
        }

        @Override
        public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) {
            return null;
        }

        @Override
        public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) {
            return null;
        }

        @Override
        public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) {
            return null;
        }
    }

    private static final class LenientPollDynamicAware extends PollDynamicAwareSupport {
        @Override
        public boolean isLenientProperties() {
            return true;
        }

        @Override
        public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) {
            return null;
        }

        @Override
        public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) {
            return null;
        }
    }
}
