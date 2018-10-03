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
package org.apache.camel.util.backoff;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class BackOffTest {

    @Test
    public void testSimpleBackOff() {
        final BackOff backOff = BackOff.builder().build();
        final BackOffTimerTask context = new BackOffTimerTask(backOff, null, t -> true);

        long delay;

        for (int i = 1; i <= 5; i++) {
            delay = context.next();
            Assert.assertEquals(i, context.getCurrentAttempts());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), delay);
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), context.getCurrentDelay());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis() * i, context.getCurrentElapsedTime());
        }
    }

    @Test
    public void testBackOffWithMultiplier() {
        final BackOff backOff = BackOff.builder().multiplier(1.5).build();
        final BackOffTimerTask context = new BackOffTimerTask(backOff, null, t -> true);

        long delay = BackOff.DEFAULT_DELAY.toMillis();
        long oldDelay;
        long elapsed = 0;

        for (int i = 1; i <= 5; i++) {
            oldDelay = delay;
            delay = context.next();
            elapsed += delay;

            Assert.assertEquals(i, context.getCurrentAttempts());
            Assert.assertEquals((long)(oldDelay * 1.5), delay);
            Assert.assertEquals((long)(oldDelay * 1.5), context.getCurrentDelay());
            Assert.assertEquals(elapsed, context.getCurrentElapsedTime(), 0);
        }
    }

    @Test
    public void testBackOffWithMaxAttempts() {
        final BackOff backOff = BackOff.builder().maxAttempts(5L).build();
        final BackOffTimerTask context = new BackOffTimerTask(backOff, null, t -> true);

        long delay;

        for (int i = 1; i <= 5; i++) {
            delay = context.next();
            Assert.assertEquals(i, context.getCurrentAttempts());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), delay);
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), context.getCurrentDelay());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis() * i, context.getCurrentElapsedTime());
        }

        delay = context.next();
        Assert.assertEquals(6, context.getCurrentAttempts());
        Assert.assertEquals(BackOff.NEVER, delay);
    }

    @Test
    public void testBackOffWithMaxTime() {
        final BackOff backOff = BackOff.builder().maxElapsedTime(9, TimeUnit.SECONDS).build();
        final BackOffTimerTask context = new BackOffTimerTask(backOff, null, t -> true);

        long delay;

        for (int i = 1; i <= 5; i++) {
            delay = context.next();
            Assert.assertEquals(i, context.getCurrentAttempts());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), delay);
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis(), context.getCurrentDelay());
            Assert.assertEquals(BackOff.DEFAULT_DELAY.toMillis() * i, context.getCurrentElapsedTime());
        }

        delay = context.next();
        Assert.assertEquals(6, context.getCurrentAttempts());
        Assert.assertEquals(BackOff.NEVER, delay);
    }
}
