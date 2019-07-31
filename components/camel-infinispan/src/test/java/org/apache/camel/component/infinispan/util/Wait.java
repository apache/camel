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
package org.apache.camel.component.infinispan.util;

import static org.junit.Assert.assertTrue;

public final class Wait {

    private Wait() {
    }

    public static void waitFor(Condition ec, long timeout) {
        waitFor(ec, timeout, 10);
    }

    /**
     * @param ec Condition that has to be met after the timeout
     * @param timeout Overall timeout - how long to wait for the condition
     * @param loops How many times to check the condition before the timeout expires.
     */
    public static void waitFor(Condition ec, long timeout, int loops) {
        if (loops <= 0) {
            throw new IllegalArgumentException("Number of loops must be positive");
        }
        long sleepDuration = timeout / loops;
        if (sleepDuration == 0) {
            sleepDuration = 1;
        }
        try {
            for (int i = 0; i < loops; i++) {
                if (ec.isSatisfied()) {
                    return;
                }
                Thread.sleep(sleepDuration);
            }
            assertTrue("The condition was not satisfied after " + timeout + " ms", ec.isSatisfied());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected!", e);
        }
    }
}