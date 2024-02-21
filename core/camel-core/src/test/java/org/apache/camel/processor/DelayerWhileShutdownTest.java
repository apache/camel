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

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Delayer while shutting down so its interrupted and will also stop.
 */
public class DelayerWhileShutdownTest extends ContextTestSupport {

    private final Phaser phaser = new Phaser(2);

    @BeforeEach
    void sendEarly() {
        template.sendBody("seda:a", "Long delay");
        template.sendBody("seda:b", "Short delay");
    }

    @Test
    public void testSendingMessageGetsDelayed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Short delay");

        phaser.awaitAdvanceInterruptibly(0, 5000, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:a").process(e -> phaser.arriveAndAwaitAdvance()).delay(1000).to("mock:result");
                from("seda:b").process(e -> phaser.arriveAndAwaitAdvance()).delay(1).to("mock:result");
            }
        };
    }
}
