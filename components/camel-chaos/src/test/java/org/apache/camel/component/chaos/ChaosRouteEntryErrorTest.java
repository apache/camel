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

import java.net.ConnectException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code route=X error} and {@code route=X delay}: the rule fires once per exchange as it enters the route, and the
 * exchange fails or waits at the first step of the route, inside the error handler of the route.
 */
class ChaosRouteEntryErrorTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:plain").routeId("plain")
                        .setHeader("seen", constant("yes")).id("mark")
                        .to("mock:plain").id("send");

                from("direct:redeliver").routeId("redeliver")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:flaky").id("flaky");

                from("direct:dlc").routeId("dlc")
                        .errorHandler(deadLetterChannel("mock:dead").redeliveryDelay(0))
                        .to("mock:never").id("never");

                from("direct:onexception").routeId("onexception")
                        .onException(ConnectException.class).handled(true).to("mock:handled").end()
                        .to("mock:io").id("io");

                from("direct:caller").routeId("caller")
                        .to("direct:plain").id("callPlain")
                        .to("mock:caller").id("afterCall");
            }
        };
    }

    @Test
    void theExchangeFailsAtTheFirstStep() throws Exception {
        String id = chaos().addRule("route=plain error");
        getMockEndpoint("mock:plain").expectedMessageCount(0);

        Exchange out = template.send("direct:plain", e -> e.getMessage().setBody("x"));

        // without error handling the caller gets the failure, and no step ran
        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        assertThat(out.getMessage().getHeader("seen")).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "entry")).isEqualTo(1);
        assertThat(routeReport("plain").getLong("failed")).isEqualTo(1);
        JsonObject fault = (JsonObject) ((JsonArray) chaos().report().toJson().get("recentFaults")).get(0);
        assertThat(fault.getString("hook")).isEqualTo("entry");
        assertThat(fault.getString("route")).isEqualTo("plain");
    }

    @Test
    void theRedeliveryOfTheFirstStepRecovers() throws Exception {
        String id = chaos().addRule("route=redeliver error");
        getMockEndpoint("mock:flaky").expectedMessageCount(2);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:redeliver", "x");
        template.sendBody("direct:redeliver", "y");

        MockEndpoint.assertIsSatisfied(context);
        // once per exchange, as it entered the route: the redelivery runs the step itself
        assertThat(fired(id, "entry")).isEqualTo(2);
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(2);
        assertThat(routeReport("redeliver").getLong("redeliveries")).isEqualTo(2);
        assertThat(routeReport("redeliver").getLong("completed")).isEqualTo(2);
        JsonObject effect = (JsonObject) ruleReport(id).get("effect");
        assertThat(effect.getLong("recovered")).isEqualTo(2);
    }

    @Test
    void theDeadLetterChannelOfTheRouteHandlesIt() throws Exception {
        String id = chaos().addRule("route=dlc error");
        getMockEndpoint("mock:never").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        Exchange out = template.send("direct:dlc", e -> e.getMessage().setBody("x"));

        // no redeliveries, so the dead letter channel takes it
        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "entry")).isEqualTo(1);
        assertThat(routeReport("dlc").getLong("handled")).isEqualTo(1);
    }

    @Test
    void onExceptionOfTheRouteHandlesTheTypedFault() throws Exception {
        chaos().addRule("route=onexception error exception=" + ConnectException.class.getName() + " message=refused");
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:onexception", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        Exception caught = getMockEndpoint("mock:handled").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertThat(caught).isExactlyInstanceOf(ConnectException.class).hasMessage("refused");
    }

    @Test
    void aRouteThatIsCalledFailsAtItsOwnFirstStep() throws Exception {
        String id = chaos().addRule("route=plain error");
        getMockEndpoint("mock:caller").expectedMessageCount(0);
        getMockEndpoint("mock:plain").expectedMessageCount(0);

        Exchange out = template.send("direct:caller", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "entry")).isEqualTo(1);
        JsonObject fault = (JsonObject) ((JsonArray) chaos().report().toJson().get("recentFaults")).get(0);
        assertThat(fault.getString("route")).isEqualTo("plain");
    }

    @Test
    void theRateDecidesPerExchange() {
        String id = chaos().addRule("route=plain error rate=every:2");

        assertThat(template.send("direct:plain", e -> e.getMessage().setBody("1")).getException()).isNull();
        assertThat(template.send("direct:plain", e -> e.getMessage().setBody("2")).getException())
                .isInstanceOf(ChaosException.class);
        assertThat(template.send("direct:plain", e -> e.getMessage().setBody("3")).getException()).isNull();

        assertThat(ruleReport(id).getLong("matches")).isEqualTo(3);
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void aDelayWaitsAtTheFirstStepThenTheRouteGoesOn() throws Exception {
        String id = chaos().addRule("route=plain delay duration=50ms");
        getMockEndpoint("mock:plain").expectedMessageCount(1);

        long start = System.nanoTime();
        Exchange out = template.send("direct:plain", e -> e.getMessage().setBody("x"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(out.getException()).isNull();
        assertThat(millis).isGreaterThanOrEqualTo(50);
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "entry")).isEqualTo(1);
        assertThat(ruleReport(id).getString("description")).isEqualTo("delay every message entering route plain by 50"
                                                                      + " milliseconds");
    }

    @Test
    void corruptAtTheEntryStillChangesTheMessageBeforeTheFirstStep() throws Exception {
        String error = chaos().addRule("route=redeliver error until=1");
        String corrupt = chaos().addRule("route=plain corrupt mode=empty");
        getMockEndpoint("mock:plain").expectedBodiesReceived("");

        template.sendBody("direct:plain", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(corrupt, "entry")).isEqualTo(1);
        assertThat(fired(error, "entry")).isZero();
    }
}
