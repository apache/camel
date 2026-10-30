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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The faults at the steps of a route ({@code step=}), and how the error handlers handle the faults at steps and at
 * sends: redelivery, the dead letter channel, onException and doTry.
 */
class ChaosStepAndErrorHandlerTest extends ChaosTestSupport {

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

                from("direct:onexception").routeId("onexception")
                        .onException(IOException.class).handled(true).to("mock:handled").end()
                        .to("mock:io").id("io");

                from("direct:trycatch").routeId("trycatch")
                        .doTry()
                            .to("mock:try").id("try")
                        .doCatch(ChaosException.class)
                            .to("mock:caught")
                        .end();
            }
        };
    }

    @Test
    void errorAtAStepFailsTheStepBeforeItsProcessor() throws Exception {
        String id = chaos().addRule("step=mark error");
        getMockEndpoint("mock:plain").expectedMessageCount(0);

        Exchange out = template.send("direct:plain", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        assertThat(out.getMessage().getHeader("seen")).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(1);
    }

    @Test
    void stepPatternMatchesTheNodeId() throws Exception {
        String id = chaos().addRule("step=se* error");
        getMockEndpoint("mock:plain").expectedMessageCount(0);

        Exchange out = template.send("direct:plain", e -> e.getMessage().setBody("x"));

        // the send step "send" fails; "mark" does not match
        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        assertThat(out.getMessage().getHeader("seen")).isEqualTo("yes");
        assertThat(fired(id, "step")).isEqualTo(1);
    }

    @Test
    void sendFaultIsRedeliveredAndThenGoesToTheDeadLetterChannel() throws Exception {
        String id = chaos().addRule("endpoint=mock:flaky error");
        getMockEndpoint("mock:flaky").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:redeliver", "x");

        MockEndpoint.assertIsSatisfied(context);
        // the first try and two redeliveries each ask the hook
        assertThat(fired(id, "send")).isEqualTo(3);
    }

    @Test
    void redeliverySucceedsWhenTheRuleFiresOnlyOnce() throws Exception {
        String id = chaos().addRule("endpoint=mock:flaky error until=1");
        getMockEndpoint("mock:flaky").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        template.sendBody("direct:redeliver", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void stepFaultIsRedeliveredToo() throws Exception {
        String id = chaos().addRule("step=flaky error until=2");
        getMockEndpoint("mock:flaky").expectedMessageCount(1);

        template.sendBody("direct:redeliver", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(2);
    }

    @Test
    void onExceptionHandlesTheTypedFault() throws Exception {
        chaos().addRule("endpoint=mock:io error exception=" + IOException.class.getName() + " message=\"disk full\"");
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:onexception", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        Exception caught = getMockEndpoint("mock:handled").getReceivedExchanges().get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertThat(caught).isExactlyInstanceOf(IOException.class).hasMessage("disk full");
    }

    @Test
    void doCatchGetsTheFaultOfASendAndOfAStep() throws Exception {
        chaos().addRule("endpoint=mock:try error until=1");
        String step = chaos().addRule("step=try error");
        getMockEndpoint("mock:try").expectedMessageCount(0);
        getMockEndpoint("mock:caught").expectedMessageCount(2);

        template.sendBody("direct:trycatch", "first");
        template.sendBody("direct:trycatch", "second");

        MockEndpoint.assertIsSatisfied(context);
        // the first exchange: the step rule is asked first (the step runs before its send), so it decides both times
        assertThat(fired(step, "step")).isEqualTo(2);
    }
}
