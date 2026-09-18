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
package org.apache.camel.impl.event;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelEventJsonTest extends ContextTestSupport {

    @Test
    void testContextStartedEventAsJson() throws Exception {
        CamelEvent event = new CamelContextStartedEvent(context);
        event.setTimestamp(123456789L);

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "CamelContextStarted")
                .containsEntry("eventClass", "CamelContextStartedEvent")
                .containsEntry("timestamp", 123456789L)
                .containsEntry("contextName", context.getName())
                .containsKey("message");
        assertThat(event.toJSon(2)).contains("\"type\": \"CamelContextStarted\"");
    }

    @Test
    void testExchangeFailedEventAsJson() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailedEvent failedEvent) {
                    Map<String, Object> json = failedEvent.asJSon();
                    assertThat(json)
                            .containsEntry("type", "ExchangeFailed")
                            .containsKey("exchangeId")
                            .containsKey("routeId")
                            .containsKey("message");
                    assertThat(json.get("exception")).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> exception = (Map<String, Object>) json.get("exception");
                    assertThat(exception)
                            .containsEntry("type", "java.lang.IllegalArgumentException")
                            .containsEntry("message", "boom");
                }
            }
        });

        try {
            template.sendBody("direct:fail", "Hello");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    void testExchangeSentEventAsJson() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        ExchangeSentEvent event = new ExchangeSentEvent(
                template.send("direct:sent", exchange -> exchange.getMessage().setBody("Hello")),
                context.getEndpoint("mock:result"),
                42);
        event.setTimestamp(999L);

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "ExchangeSent")
                .containsEntry("timestamp", 999L)
                .containsEntry("timeTaken", 42L)
                .containsKey("exchangeId")
                .containsKey("endpointUri");
    }

    @Test
    void testExchangeFailureHandlingEventAsJson() throws Exception {
        Exchange exchange = createExchangeWithBody("payload");
        exchange.setException(new RuntimeException("failed"));

        ExchangeFailureHandlingEvent event = new ExchangeFailureHandlingEvent(
                exchange,
                ex -> {
                },
                true,
                "mock:dead");

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "ExchangeFailureHandling")
                .containsEntry("deadLetterChannel", true)
                .containsEntry("deadLetterUri", "mock:dead")
                .containsKey("failureHandler")
                .containsKey("exception");
    }

    @Test
    void testRouteReloadedEventAsJson() {
        Route route = context.getRoute("jsonRoute");
        RouteReloadedEvent event = new RouteReloadedEvent(route, 2, 5);

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "RouteReloaded")
                .containsEntry("routeId", "jsonRoute")
                .containsEntry("index", 2)
                .containsEntry("total", 5);
    }

    @Test
    void testRouteRestartingFailureEventAsJson() {
        Route route = context.getRoute("jsonRoute");
        RouteRestartingFailureEvent event
                = new RouteRestartingFailureEvent(route, 3, new IllegalStateException("restart"), true);

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "RouteRestartingFailure")
                .containsEntry("attempt", 3L)
                .containsEntry("exhausted", true)
                .containsKey("exception");
    }

    @Test
    void testStepFailedEventAsJson() {
        Exchange exchange = createExchangeWithBody("step-body");
        exchange.setException(new IllegalStateException("step failed"));

        StepFailedEvent event = new StepFailedEvent(exchange, "myStep");

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "StepFailed")
                .containsEntry("stepId", "myStep")
                .containsKey("exchangeId")
                .containsKey("exception");
    }

    @Test
    void testServiceStartupFailureEventAsJson() {
        ServiceStartupFailureEvent event
                = new ServiceStartupFailureEvent(context, "my-service", new RuntimeException("startup failed"));

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "ServiceStartupFailure")
                .containsEntry("service", "my-service")
                .containsEntry("contextName", context.getName())
                .containsKey("exception");
        assertThat(json.get("exception")).isInstanceOf(Map.class);
    }

    @Test
    void testExchangeRedeliveryEventAsJson() {
        Exchange exchange = createExchangeWithBody("payload");
        exchange.setException(new RuntimeException("redelivery cause"));

        ExchangeRedeliveryEvent event = new ExchangeRedeliveryEvent(exchange, 2);

        Map<String, Object> json = event.asJSon();

        assertThat(json)
                .containsEntry("type", "ExchangeRedelivery")
                .containsEntry("attempt", 2)
                .containsKey("exception");
    }

    @Test
    void testDefaultCamelEventToJsonEscapesSpecialCharacters() {
        CamelEvent event = new CamelEvent() {
            @Override
            public Type getType() {
                return Type.Custom;
            }

            @Override
            public Object getSource() {
                return "source";
            }

            @Override
            public long getTimestamp() {
                return 42;
            }

            @Override
            public void setTimestamp(long timestamp) {
            }

            @Override
            public String toString() {
                return "Route \"my-route\" failed\nline2";
            }
        };

        String json = event.toJSon(0);

        assertThat(json)
                .isEqualTo("{\"type\":\"Custom\",\"timestamp\":42,\"message\":\"Route \\\"my-route\\\" failed\\nline2\"}");
        assertThat(json).doesNotContain("\"my-route\" failed");
    }

    @Test
    void testAsJsonReturnsJsonObjectCompatibleMap() {
        CamelEvent event = new CamelContextInitializedEvent(context);

        Map<String, Object> json = event.asJSon();

        assertThat(json).isInstanceOf(JsonObject.class);
        assertThat(new JsonObject(json)).containsEntry("type", "CamelContextInitialized");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fail").routeId("jsonRoute").throwException(new IllegalArgumentException("boom"));
                from("direct:sent").to("mock:result");
            }
        };
    }
}
