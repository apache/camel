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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
@Timeout(60)
public class SplitParallelTimeoutNoAggregationStrategyTest extends ContextTestSupport {

    private final Phaser phaser = new Phaser(3);

    @BeforeEach
    void sendEarly() {
        Assumptions.assumeTrue(context.isStarted(), "The test cannot be run because the context is not started");
        template.sendBody("direct:start", "A,B,C");
    }

    @Test
    public void testSplitTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A will timeout so we only get B and/or C
        mock.message(0).body().not(body().contains("A"));

        phaser.awaitAdvanceInterruptibly(0, 5000, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .split(body().tokenize(",")).parallelProcessing().timeout(100)
                        .choice()
                            .when(body().isEqualTo("A")).to("direct:a")
                            .when(body().isEqualTo("B")).to("direct:b")
                            .when(body().isEqualTo("C")).to("direct:c")
                            .end() // end
                        // choice
                        .end() // end split
                        .to("mock:result");

                from("direct:a").process(e -> phaser.arriveAndAwaitAdvance()).delay(200).setBody(constant("A"));

                from("direct:b").process(e -> phaser.arriveAndAwaitAdvance()).setBody(constant("B"));

                from("direct:c").process(e -> phaser.arriveAndAwaitAdvance()).delay(10).setBody(constant("C"));
            }
        };
    }

}
