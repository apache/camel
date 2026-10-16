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
package org.apache.camel.test.dst.demo.slack;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Demo 2: the camel-slack consumer (CAMEL-25684, apache/camel#27923) with a consumer that is suspended and resumed at
 * times chosen by the seed, while it polls and routes a batch. Every message posted to the channel must be routed.
 * <p>
 * Runs against the code of the camel-slack module of the reactor. Enabled with {@code -Ddst.demo=true}: on a branch
 * without the fix it fails by design.
 */
@EnabledIfSystemProperty(named = "dst.demo", matches = "true")
@DstInstrument("org.apache.camel.component.slack")
class SlackSuspendDstTest {

    @DstTest(seeds = 2000)
    void everyPostedMessageIsRoutedAcrossSuspendAndResume(Dst dst) throws Exception {
        new SlackWorld(dst).run();
    }
}
