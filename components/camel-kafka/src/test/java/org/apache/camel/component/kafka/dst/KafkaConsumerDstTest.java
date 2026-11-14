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
package org.apache.camel.component.kafka.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-kafka consumer against a fake Kafka cluster and consumer group, with
 * consumer creation failures, poll failures, failed and slow commits, failed and slow processing, route restarts,
 * suspend and resume of the route, and rebalances: a committed offset never passes a record the route has not finished
 * with, a suspended route stops fetching, and once the faults cleared every record is finished and committed. A failing
 * seed is replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds;
 * {@code -Ddst.kafka.modes=BATCH} and {@code -Ddst.kafka.pollOnError=RECONNECT} narrow the configurations.
 */
@DstInstrument(value = "org.apache.camel.component.kafka",
               alsoRewrite = {
                       "org.apache.camel.util.StopWatch", "org.apache.camel.impl.engine.DefaultShutdownStrategy",
                       "org.apache.camel.support.task" })
class KafkaConsumerDstTest {

    @DstTest(seeds = 1000)
    void committedOffsetsNeverPassAnUnfinishedRecord(Dst dst) throws Exception {
        new KafkaWorld(dst).run();
    }
}
