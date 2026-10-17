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
package org.apache.camel.component.aws2.sqs.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the visibility timeout extender of camel-aws2-sqs with slow and failing processing,
 * short polling and faults of the SQS calls: a message the consumer holds must not be delivered again. Regression test
 * for CAMEL-25700 (apache/camel#27985): before that fix 1397 of 5000 seeds fail. The world was written by Claus Ibsen
 * for CAMEL-25738. A failing seed is replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds;
 * {@code -Ddst.sqs.visibilityTimeouts=1} shows CAMEL-25737.
 */
@DstInstrument("org.apache.camel.component.aws2.sqs")
class SqsVisibilityDstTest {

    @DstTest(seeds = 1000)
    void noMessageIsDeliveredAgainWhileTheConsumerHoldsIt(Dst dst) throws Exception {
        new SqsWorld(dst).run();
    }
}
