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
package org.apache.camel.component.kafka.share.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-kafka-share consumer against a fake share group, with consumer creation
 * failures, poll and commit failures, acquisition locks that expire under slow processing, route restarts, and suspend
 * and resume: every published record completes in the route at least once or is archived by the group, and a record is
 * accepted only for a delivery the route completed. A failing seed is replayed with {@code -Ddst.seed=<seed>};
 * {@code -Ddst.seeds=5000} runs more seeds; {@code -Ddst.share.pollOnError=RECONNECT} runs one pollOnError strategy.
 */
@DstInstrument(value = "org.apache.camel.component.kafka.share",
               alsoRewrite = {
                       "org.apache.camel.util.StopWatch", "org.apache.camel.impl.engine.DefaultShutdownStrategy",
                       "org.apache.camel.support.task" })
class KafkaShareConsumerDstTest {

    @DstTest(seeds = 1000)
    void everyRecordCompletesOrIsArchivedAndOnlyCompletedOnesAreAccepted(Dst dst) throws Exception {
        new KafkaShareWorld(dst).run();
    }
}
