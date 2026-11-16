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
package org.apache.camel.component.google.pubsub.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-google-pubsub consumer (synchronousPull) against a fake subscription, with
 * failed pulls, acknowledgements and nacks, ack deadlines that expire under slow processing, the max delivery attempts
 * of a dead letter policy, grouped messages acked together, and route stops while the consumer pulls: a delivery is
 * acknowledged only after its exchange completed, a stopped consumer does not pull, and once the faults cleared every
 * message is acknowledged or dead lettered. A failing seed is replayed with {@code -Ddst.seed=<seed>};
 * {@code -Ddst.seeds=5000} runs more seeds.
 */
@DstInstrument("org.apache.camel.component.google.pubsub")
class PubsubConsumerDstTest {

    @DstTest(seeds = 1000)
    void everyDeliveryIsAcknowledgedOnlyAfterItsExchangeCompleted(Dst dst) throws Exception {
        new PubsubWorld(dst).run();
    }
}
