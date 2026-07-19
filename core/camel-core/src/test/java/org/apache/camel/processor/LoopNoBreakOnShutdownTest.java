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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class LoopNoBreakOnShutdownTest extends ContextTestSupport {

    private static final int LOOP_COUNT = 100;

    @Test
    void testLoopNoBreakOnShutdown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(LOOP_COUNT);

        template.asyncSendBody("seda:foo", "foo");

        // Wait until at least 1 loop iteration has completed and reached mock:result.
        // This guarantees the exchange is registered in the inflight repository and
        // the LoopProcessor's taskCount > 0, so the shutdown strategy will properly
        // wait for all 100 iterations to complete instead of seeing 0 inflight
        // exchanges and proceeding with immediate shutdown.
        await().atMost(10, SECONDS).until(() -> mock.getReceivedCounter() >= 1);

        context.stop();

        // after context.stop(), all shutdown-deferred processing has completed
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {

                from("seda:foo")
                        .startupOrder(1)
                        .loop(LOOP_COUNT).delay(50)
                        .to("seda:bar");

                from("seda:bar")
                        .startupOrder(2)
                        .shutdownRoute(ShutdownRoute.Defer)
                        .to("mock:result");
            }
        };
    }
}
