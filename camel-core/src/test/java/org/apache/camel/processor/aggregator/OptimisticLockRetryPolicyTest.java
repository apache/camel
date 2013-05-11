/**
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

import junit.framework.TestCase;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;

/**
 * @version
 */
public class OptimisticLockRetryPolicyTest extends TestCase {

    private static long precision = 5L; // give or take 5ms

    public void testRandomBackOff() throws Exception {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(true);
        policy.setExponentialBackOff(false);
        policy.setMaximumRetryDelay(500L);

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            policy.doDelay(i);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed <= policy.getMaximumRetryDelay() + precision && elapsed >= 0);
        }
    }

    public void testExponentialBackOff() throws Exception {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(true);
        policy.setMaximumRetryDelay(0L);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            policy.doDelay(i);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= (policy.getRetryDelay() << i) - precision);
            assertTrue(elapsed <= (policy.getRetryDelay() << i) + precision);
        }
    }

    public void testExponentialBackOffMaximumRetryDelay() throws Exception {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(true);
        policy.setMaximumRetryDelay(200L);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            policy.doDelay(i);
            long elapsed = System.currentTimeMillis() - start;
            switch (i) {
            case 0:
                assertTrue(elapsed <= 50 + precision);
                assertTrue(elapsed >= 50 - precision);
                break;
            case 1:
                assertTrue(elapsed <= 100 + precision);
                assertTrue(elapsed >= 100 - precision);
                break;
            default:
                assertTrue(elapsed <= 200 + precision);
                assertTrue(elapsed >= 200 - precision);
                break;
            }
        }
    }

    public void testRetryDelay() throws Exception {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        policy.setRandomBackOff(false);
        policy.setExponentialBackOff(false);
        policy.setMaximumRetryDelay(0L);
        policy.setRetryDelay(50L);

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            policy.doDelay(i);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed <= policy.getRetryDelay() + precision);
            assertTrue(elapsed >= policy.getRetryDelay() - precision);
        }
    }

    public void testMaximumRetries() throws Exception {
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
}