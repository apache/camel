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
package org.apache.camel.component.pulsar.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstSeed;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-pulsar consumer against a fake broker, with subscribe failures, broker
 * restarts, route restarts, suspend and resume, and slow and failing processing: a started route holds the configured
 * number of Pulsar consumers and every published message is routed. Regression test for CAMEL-25869: before that fix
 * 871 of 5000 seeds fail. A failing seed is replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more
 * seeds; {@code -Ddst.pulsar.subscribeFailRates=0} runs without subscribe failures.
 */
@DstInstrument(value = "org.apache.camel.component.pulsar",
               alsoRewrite = { "org.apache.camel.util.StopWatch", "org.apache.camel.impl.engine.DefaultShutdownStrategy" })
class PulsarConsumerDstTest {

    @DstTest(seeds = 1000)
    void aStartedRouteHoldsItsConsumersAndRoutesEveryMessage(Dst dst) throws Exception {
        new PulsarWorld(dst).run();
    }

    /**
     * The seed that found CAMEL-25869: Key_Shared with one consumer whose subscribe times out. Before the fix the route
     * reported Started with no consumer and routed none of the 11 published messages.
     */
    @DstTest
    @DstSeed(8)
    void startedAgainAfterItsOnlyConsumerFailedToSubscribe(Dst dst) throws Exception {
        new PulsarWorld(dst).run();
    }
}
