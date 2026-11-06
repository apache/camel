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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The faults at the reply of a send ({@code at=reply}): after the producer succeeded, so its side effect happened.
 */
class ChaosReplyFaultsTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:pay").routeId("pay")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:payments").to("mock:done");
                from("direct:ask").routeId("ask").to("mock:service").to("mock:after");
                from("direct:fail").routeId("fail").to("mock:broken").to("mock:after");
            }
        };
    }

    @Test
    void anErrorAtTheReplyFailsTheSendAfterTheProducerRan() throws Exception {
        // the duplicate payment: the payment is made, the send fails, and the redelivery pays again
        String id = chaos().addRule("endpoint=mock:payments error at=reply until=1");
        getMockEndpoint("mock:payments").expectedBodiesReceived("pay 10", "pay 10");
        getMockEndpoint("mock:done").expectedMessageCount(1);

        Exchange out = template.send("direct:pay", e -> e.getMessage().setBody("pay 10"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "reply")).isEqualTo(1);
        JsonObject effect = ruleReport(id).getJsonObject("effect");
        assertThat(effect.getLong("recovered")).isEqualTo(1);
        assertThat(chaos().report().toText()).contains(id + " error ChaosException at reply mock://payments");
    }

    @Test
    void anErrorAtTheReplyWithoutRedeliveryFailsTheExchange() throws Exception {
        String id = chaos().addRule("endpoint=mock:service error at=reply");
        getMockEndpoint("mock:service").expectedMessageCount(1);
        getMockEndpoint("mock:after").expectedMessageCount(0);

        Exchange out = template.send("direct:ask", e -> e.getMessage().setBody("ping"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class).hasMessage("Chaos error of rule " + id);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void corruptAtTheReplyChangesWhatTheRouteGoesOnWith() throws Exception {
        String id = chaos().addRule("endpoint=mock:service corrupt mode=empty at=reply");
        MockEndpoint service = getMockEndpoint("mock:service");
        service.whenAnyExchangeReceived(e -> e.getMessage().setBody("pong"));
        service.expectedBodiesReceived("ping");
        getMockEndpoint("mock:after").expectedBodiesReceived("");

        template.sendBody("direct:ask", "ping");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "reply")).isEqualTo(1);
    }

    @Test
    void theReplyIsNotAskedAfterASendThatChaosDroppedOrThatFailed() throws Exception {
        String drop = chaos().addRule("endpoint=mock:service drop until=1");
        String reply = chaos().addRule("endpoint=mock:service error at=reply");
        getMockEndpoint("mock:service").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(1);

        // the producer did not run, so there is no reply to fail
        assertThat(template.send("direct:ask", e -> e.getMessage().setBody("a")).getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(drop, "send")).isEqualTo(1);
        assertThat(ruleReport(reply).getLong("matches")).isZero();

        // a producer that fails by itself has no reply either
        chaos().changeRule(reply, "endpoint=mock:broken error at=reply");
        getMockEndpoint("mock:broken").whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("broken");
        });
        assertThat(template.send("direct:fail", e -> e.getMessage().setBody("b")).getException())
                .isInstanceOf(IllegalStateException.class);
        assertThat(ruleReport(reply).getLong("matches")).isZero();

        // the next send runs the producer, and the reply rule fires
        assertThat(template.send("direct:ask", e -> e.getMessage().setBody("c")).getException()).isNull();
        chaos().changeRule(reply, "endpoint=mock:service error at=reply");
        assertThat(template.send("direct:ask", e -> e.getMessage().setBody("d")).getException())
                .isInstanceOf(ChaosException.class);
    }
}
