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
package org.apache.camel.component.slack.dst;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;

/**
 * Deterministic simulation test of the camel-slack consumer, suspended and resumed at times chosen by the seed while it
 * polls and routes a batch: every message posted to the channel must be routed. Regression test for CAMEL-25684
 * (apache/camel#27923): before that fix 2652 of 5000 seeds fail. A failing seed is replayed with
 * {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000} runs more seeds.
 */
@DstInstrument("org.apache.camel.component.slack")
class SlackSuspendDstTest {

    @DstTest(seeds = 1000)
    void everyPostedMessageIsRoutedAcrossSuspendAndResume(Dst dst) throws Exception {
        new SlackWorld(dst).run();
    }
}
