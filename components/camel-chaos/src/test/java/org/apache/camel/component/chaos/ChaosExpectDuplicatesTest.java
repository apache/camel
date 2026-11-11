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
package org.apache.camel.component.chaos;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26791: an expectation of the duplicates a route processed (payment:duplicates<=0), for a duplicate fault: the
 * messages the route completed a second time, with the same body or the same key=, that no idempotent consumer
 * recognised. The clock is virtual.
 */
@Isolated
class ChaosExpectDuplicatesTest extends ChaosTestSupport {

    // the HTTP server of the integration, which a call of the shop reaches over HTTP
    private static final int PORT = AvailablePortFinder.getNextAvailable();

    private FakeClock clock;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        clock = FakeClock.install();
    }

    @Override
    protected void cleanupResources() throws Exception {
        clock.close();
        super.cleanupResources();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the shop charges each order at the payment route, which charges every message it gets
                from("direct:orders").routeId("orders").to("direct:payment");
                from("direct:payment").routeId("payment").to("mock:charged");
                // the same, with an idempotent consumer in front of the charge
                from("direct:refunds").routeId("refunds").to("direct:payback");
                from("direct:payback").routeId("payback")
                        .idempotentConsumer(body(), MemoryIdempotentRepository.memoryIdempotentRepository())
                        .to("mock:paidBack");
                // a call over HTTP to a route of the same integration: each call is an exchange of its own there, with
                // the body as a stream
                from("direct:checkout").routeId("checkout").to("netty-http:http://localhost:" + PORT + "/charge");
                from("netty-http:http://0.0.0.0:" + PORT + "/charge").routeId("charge")
                        // read after the key was: an empty body would mean the key consumed it
                        .convertBodyTo(String.class)
                        .to("mock:charge")
                        .setBody(constant("{\"charged\": true}"));
            }
        };
    }

    private JsonObject expectation(String id) {
        return (JsonObject) ruleReport(id).get("expectation");
    }

    @Test
    void theDuplicatesARouteProcessed() throws Exception {
        // every second order is sent to payment twice, as a sender that delivers it again
        String id = chaos().addRule("endpoint=direct:payment duplicate rate=every:2 expect=payment:duplicates<=0 within=1m");
        for (int i = 0; i < 4; i++) {
            template.sendBody("direct:orders", "order " + i);
        }

        // charged twice: decided at once, as it cannot become met
        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(e.getLong("value")).isEqualTo(2);
        assertThat(e.getLong("samples")).isEqualTo(6);
        assertThat(getMockEndpoint("mock:charged").getReceivedCounter()).isEqualTo(6);
        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.FAIL);
        assertThat(report.toText()).contains(
                "expect payment:duplicates<=0 within 1m0s: unmet, 2 duplicates processed (6 completed in payment)");
        // a duplicate processed is not a message lost
        assertThat(report.getLost()).isZero();
        assertThat(chaos().describeRule(id)).endsWith("; expects no duplicates processed in route payment within 1 minute");
    }

    @Test
    void theDuplicatesOfAnHttpCallToARouteOfTheIntegration() {
        String id = chaos().addRule("endpoint=netty-http:http://localhost:" + PORT
                                    + "/charge duplicate rate=every:2 expect=charge:duplicates<=0 within=1m");
        for (int i = 0; i < 4; i++) {
            template.sendBody("direct:checkout", "{\"order\": " + i + "}");
        }

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(e.getLong("value")).isEqualTo(2);
        // the key read the body, and the route still had it
        assertThat(getMockEndpoint("mock:charge").getReceivedExchanges())
                .extracting(x -> x.getMessage().getBody(String.class))
                .containsExactly("{\"order\": 0}", "{\"order\": 1}", "{\"order\": 1}", "{\"order\": 2}", "{\"order\": 3}",
                        "{\"order\": 3}");
    }

    @Test
    void theDuplicatesAnIdempotentConsumerRecognisedAreNotCounted() {
        String id = chaos().addRule("endpoint=direct:payback duplicate rate=every:2 expect=payback:duplicates<=0 within=1m");
        for (int i = 0; i < 4; i++) {
            template.sendBody("direct:refunds", "refund " + i);
        }
        clock.advance(60_001);

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("met");
        assertThat(e.getLong("value")).isZero();
        assertThat(getMockEndpoint("mock:paidBack").getReceivedCounter()).isEqualTo(4);
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
    }

    @Test
    void theKeyOfAMessage() {
        // the body does not tell the orders apart, the header does
        String id = chaos().addRule(
                "endpoint=direct:payment duplicate rate=every:2 expect=payment:duplicates<=0 within=1m key=${header.orderId}");
        for (int i = 0; i < 4; i++) {
            template.sendBodyAndHeader("direct:orders", "an order", "orderId", "ORD-" + i);
        }

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        // with the body as the key, each order after the first would count
        assertThat(e.getLong("value")).isEqualTo(2);
        assertThat(chaos().describeRule(id)).contains("in route payment (a message known by ${header.orderId})");
    }

    @Test
    void theRouteThatReceivesADuplicateForTheSuggestion() {
        Map<String, String> consumers = Map.of("direct://payment", "payment", "platform-http:///refunds", "refunds");

        // a suggestion of a duplicate fault adds the expectation of the route that receives it
        assertThat(ChaosAdvisor.receiver("direct://payment", consumers)).isEqualTo("payment");
        assertThat(ChaosAdvisor.receiver("http://localhost:8080/refunds", consumers)).isEqualTo("refunds");
        // another system, or a path no route serves
        assertThat(ChaosAdvisor.receiver("http://payments.example.com/refunds", consumers)).isNull();
        assertThat(ChaosAdvisor.receiver("http://localhost:8080/stock", consumers)).isNull();
        assertThat(ChaosAdvisor.receiver("kafka://orders", consumers)).isNull();
    }

    @Test
    void aDuplicatesExpectationIsAtMostAndKeyNeedsIt() {
        assertThatThrownBy(() -> chaos().addRule("endpoint=direct:payment duplicate expect=payment:duplicates>=1"))
                .hasMessageContaining("needs <= or = for the duplicates processed, such as expect=payment:duplicates<=0");
        assertThatThrownBy(() -> chaos().addRule("endpoint=direct:payment duplicate expect=payment:failed<=0 key=${body}"))
                .hasMessageContaining("key= is the key of a message for an expectation of duplicates");
        assertThatThrownBy(() -> chaos().addRule(
                "endpoint=direct:payment duplicate expect=payment:duplicates<=0 key=groovy:body.id"))
                .hasMessageContaining("key= takes an expression in the simple language");
    }
}
