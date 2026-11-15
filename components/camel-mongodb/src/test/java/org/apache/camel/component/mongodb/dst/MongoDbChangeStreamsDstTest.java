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
package org.apache.camel.component.mongodb.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-mongodb change streams consumer against a fake MongoDB, with dropped
 * collections, a server that cannot be reached, failed getMores, failed and slow processing, and route restarts: a
 * stored resume token never passes an event that was not routed, and once the faults cleared every event is routed. A
 * failing seed is replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds;
 * {@code -Ddst.mongodb.strictFirstCursor=true} also counts the events that a cursor opened again before any exchange
 * completed skips (see {@link MongoDbChangeStreamsWorld}).
 */
@DstInstrument("org.apache.camel.component.mongodb")
class MongoDbChangeStreamsDstTest {

    @DstTest(seeds = 1000)
    void everyEventIsRoutedAfterAStopOrADrop(Dst dst) throws Exception {
        new MongoDbChangeStreamsWorld(dst).run();
    }
}
