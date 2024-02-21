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
package org.apache.camel.processor.aggregator;

import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimisticLockRetryPolicyTest {

    @Test
    void testRandomBackOff() {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(true);
        policy.setExponentialBackOff(false);
        policy.setMaximumRetryDelay(100L);

        for (int i = 0; i < 10; i++) {
            long delay = getDelay(policy, i);
            assertTrue(delay <= policy.getMaximumRetryDelay() && delay >= 0);
        }
    }

    @Test
    void testExponentialBackOff() {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(true);
        policy.setMaximumRetryDelay(0L);
        policy.setRetryDelay(10L);

        for (int i = 0; i < 6; i++) {
            long delay = getDelay(policy, i);
            assertDelay(10L << i, delay);
        }
    }

    @Test
    void testExponentialBackOffMaximumRetryDelay() throws Exception {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(true);
        policy.setMaximumRetryDelay(100L);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            long delay = getDelay(policy, i);
            if (i == 0) {
                assertDelay(50L, delay);
            } else {
                assertDelay(100L, delay);
            }
        }
    }

    @Test
    void testRetryDelay() {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(false);
        policy.setMaximumRetryDelay(0L);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            long delay = getDelay(policy, i);
            assertDelay(50L, delay);
        }
    }

    @Test
    void testMaximumRetries() {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(false);
        policy.setMaximumRetryDelay(0L);
        policy.setMaximumRetries(2);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            switch (i) {
                case 0:
                case 1:
                    assertTrue(policy.shouldRetry(i));
                    break;
                default:
                    assertFalse(policy.shouldRetry(i));
            }
        }
    }

    private long getDelay(OptimisticLockRetryPolicy policy, int i) {
        return policy.getDelay(i);
    }

    private void assertDelay(long expectedDelay, long actualDelay) {
        assertEquals(expectedDelay, actualDelay);
    }
}
