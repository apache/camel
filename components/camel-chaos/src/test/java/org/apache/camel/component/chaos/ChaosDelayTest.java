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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The delay fault waits through TimeSource on the thread that sends or runs the step. A virtual clock records the waits
 * (it is static, so this class runs alone).
 */
@Isolated
class ChaosDelayTest extends ChaosTestSupport {

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
                from("direct:start").routeId("start").to("mock:target").id("send1").log("done").id("log1");
            }
        };
    }

    @Test
    void delayAtASendWaitsOnTheSendingThread() throws Exception {
        String id = chaos().addRule("endpoint=mock:target delay duration=1500ms");
        getMockEndpoint("mock:target").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(clock.sleeps()).containsExactly(1500L);
        assertThat(clock.sleepers()).containsExactly(Thread.currentThread().getName());
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void delayAtAStepWaitsOnTheThreadThatRunsIt() throws Exception {
        String id = chaos().addRule("step=log1 delay duration=2s");
        getMockEndpoint("mock:target").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "hello");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(clock.sleeps()).containsExactly(2000L);
        assertThat(clock.sleepers()).containsExactly(Thread.currentThread().getName());
        assertThat(fired(id, "step")).isEqualTo(1);
    }

    @Test
    void ruleEndsAfterItsDurationOnTheClockOfTimeSource() throws Exception {
        String id = chaos().addRule("endpoint=mock:target error until=1m");
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException()).isNotNull();

        clock.advance(TimeUnit.MINUTES.toMillis(1));
        getMockEndpoint("mock:target").expectedMessageCount(1);
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException()).isNull();

        MockEndpoint.assertIsSatisfied(context);
        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(1);
    }
}
