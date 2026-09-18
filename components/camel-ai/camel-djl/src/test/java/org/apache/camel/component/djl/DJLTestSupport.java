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
package org.apache.camel.component.djl;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;

/**
 * Shared helpers for the camel-djl tests.
 */
final class DJLTestSupport {

    /**
     * Maximum time to wait for a route to deliver the expected messages. DJL downloads models from the model zoo on
     * first use, so the wait has to be generous, but it must stay well below the Surefire fork timeout
     * ({@code camel.surefire.forkTimeout}) so that a stuck route fails its own test instead of killing the fork and
     * with it every remaining test in the module.
     */
    private static final long RESULT_WAIT_TIME = TimeUnit.MINUTES.toMillis(2);

    private DJLTestSupport() {
    }

    /**
     * Waits for the mock endpoint to be satisfied, failing after {@link #RESULT_WAIT_TIME} rather than blocking forever
     * like {@link MockEndpoint#await()} does when the route never delivers.
     */
    static void assertMockSatisfied(MockEndpoint mock) throws InterruptedException {
        mock.setResultWaitTime(RESULT_WAIT_TIME);
        mock.assertIsSatisfied();
    }
}
