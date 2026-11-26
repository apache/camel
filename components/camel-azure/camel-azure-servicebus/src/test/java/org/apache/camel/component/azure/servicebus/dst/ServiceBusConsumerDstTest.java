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
package org.apache.camel.component.azure.servicebus.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-azure-servicebus consumer in peek-lock mode against a fake queue, with an
 * asynchronous route slower than the lock duration, failed lock renewals and settlements, and route stops while
 * messages are processed: no message is delivered again while the consumer holds it, a message is settled as its
 * exchange ended, and once the faults cleared every message is completed or dead-lettered. A failing seed is replayed
 * with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds.
 */
@DstInstrument("org.apache.camel.component.azure.servicebus")
class ServiceBusConsumerDstTest {

    @DstTest(seeds = 1000)
    void noMessageIsDeliveredAgainWhileTheConsumerHoldsIt(Dst dst) throws Exception {
        new ServiceBusWorld(dst).run();
    }
}
