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
 * The SQS world of {@link SqsVisibilityDstTest} with synchronous redelivery: a failed message is retried by Camel's
 * error handler after a redelivery delay longer than the visibility timeout, so the visibility timeout extender has to
 * keep the message invisible while the error handler waits. The wait is a sleep in Camel core, which runs in virtual
 * time through Camel's internal time source; without it each wait would block the test thread for real (and the seed
 * would fail on the watchdog or on the invariant that the redelivery delay passed).
 */
@DstInstrument("org.apache.camel.component.aws2.sqs")
class SqsRedeliveryDstTest {

    @DstTest(seeds = 500)
    void noMessageIsDeliveredAgainWhileTheErrorHandlerWaitsToRetryIt(Dst dst) throws Exception {
        new SqsWorld(dst, true).run();
    }
}
