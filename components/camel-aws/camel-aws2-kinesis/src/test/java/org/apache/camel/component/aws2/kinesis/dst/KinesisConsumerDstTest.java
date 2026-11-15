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
package org.apache.camel.component.aws2.kinesis.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-aws2-kinesis consumer against a fake data stream, with shard iterators
 * that expire while the route is suspended or an exchange is slow, failed and slow GetRecords, GetShardIterator and
 * ListShards calls, resharding, checkpoints in a resume cache, route restarts and suspensions: a consumer routes each
 * record at most once (apart from a record after its checkpoint), a restarted route resumes after its checkpoint, and
 * once the faults cleared every record is routed. A failing seed is replayed with {@code -Ddst.seed=<seed>};
 * {@code -Ddst.seeds=5000} runs more seeds.
 */
@DstInstrument("org.apache.camel.component.aws2.kinesis")
class KinesisConsumerDstTest {

    @DstTest(seeds = 1000)
    void everyRecordIsRoutedOnceByEachConsumerAndAfterItsCheckpoint(Dst dst) throws Exception {
        new KinesisWorld(dst).run();
    }
}
