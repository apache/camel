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

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hooks of a rule in the list and the report are those where it can fire in the context now: {@code poll} only for
 * a route that consumes a matching endpoint with a scheduled poll, {@code send} only when something sends to a matching
 * endpoint (a to, a toD, a template that sent to it), and any hook where it fired already. The rule itself still fires
 * at all its hooks, and its {@code fired} counts keep them all.
 */
class ChaosHooksTest extends ChaosTestSupport {

    @BindToRegistry("testpoll")
    private final TestPollComponent testPoll = new TestPollComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a scheduled poll that does not poll during the test, and is never sent to
                from("testpoll:inbox?initialDelay=3600000").routeId("poller").to("mock:out");
                // consumed, but neither sent to nor polled on a schedule
                from("seda:in").routeId("in").to("mock:in");
                // direct:orders is consumed and sent to; mock:kafka sent to; the toD knows its endpoint when it sends
                from("direct:calls").routeId("calls").to("direct:orders");
                from("direct:orders").routeId("orders").to("mock:kafka").toD("mock:dyn-${header.n}")
                        .recipientList(constant("mock:list-a,mock:list-b"));
            }
        };
    }

    private List<Object> hooks(String id) {
        return (JsonArray) ruleReport(id).get("hooks");
    }

    @Test
    void pollOnlyWhereARoutePollsOnASchedule() {
        String id = chaos().addRule("endpoint=testpoll:inbox* error");
        assertThat(hooks(id)).containsExactly("poll");
        assertThat(ruleReport(id).getString("description")).isEqualTo("fail every poll of testpoll:inbox*");
        // the counts keep every hook of the rule
        assertThat(((JsonObject) ruleReport(id).get("fired")).keySet()).containsExactlyInAnyOrder("send", "poll");

        assertThat(hooks(chaos().addRule("component=testpoll drop at=poll"))).containsExactly("poll");
        // nothing sends to it
        assertThat(hooks(chaos().addRule("endpoint=testpoll:inbox* drop at=send"))).isEmpty();
    }

    @Test
    void sendOnlyWhereSomethingSends() {
        assertThat(hooks(chaos().addRule("endpoint=mock:kafka error"))).containsExactly("send");
        assertThat(ruleReport("r1").getString("description")).isEqualTo("fail every send to mock:kafka");
        // consumed by a route and sent to by another
        assertThat(hooks(chaos().addRule("endpoint=direct:orders error"))).containsExactly("send");
        assertThat(hooks(chaos().addRule("component=mock delay duration=1ms rate=0.5"))).containsExactly("send");
        // at=reply is the reply of a send
        assertThat(hooks(chaos().addRule("endpoint=mock:kafka corrupt mode=empty at=reply"))).containsExactly("reply");
        // the other targets keep their hooks
        assertThat(hooks(chaos().addRule("route=orders corrupt mode=headers"))).containsExactly("entry");
        assertThat(hooks(chaos().addRule("step=to1 delay duration=1ms"))).containsExactly("step");
    }

    @Test
    void theSendsThatAreKnownOnlyWhenTheySend() {
        // the toD and the recipient list have not sent yet: their endpoints are not there
        assertThat(context.hasEndpoint("mock:dyn-1")).isNull();
        assertThat(hooks(chaos().addRule("endpoint=mock:dyn-* delay duration=1ms"))).containsExactly("send");
        assertThat(hooks(chaos().addRule("endpoint=mock:list-b error"))).containsExactly("send");
        assertThat(hooks(chaos().addRule("endpoint=mock:other-* error"))).isEmpty();
    }

    @Test
    void consumedOnlyCanFireNowhereUntilItFires() {
        String id = chaos().addRule("endpoint=seda:in error");
        assertThat(hooks(id)).isEmpty();
        // the words name all its hooks then
        assertThat(ruleReport(id).getString("description")).isEqualTo("fail every send and poll of seda:in");

        // a template sends to it: the rule fires there, so it can
        template.send("seda:in", e -> e.getMessage().setBody("x"));
        assertThat(hooks(id)).containsExactly("send");
        assertThat(chaos().describeRule(id)).isEqualTo("fail every send to seda:in");
    }

    @Test
    void anEndpointThatIsNotThere() {
        assertThat(hooks(chaos().addRule("endpoint=nothing:* error"))).isEmpty();
    }
}
