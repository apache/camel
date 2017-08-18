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
package org.apache.camel.impl.health;

import java.time.Duration;
import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.junit.Assert;
import org.junit.Test;

public class HealthCheckTest {
    @Test
    public void testCheck() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.UP);

        HealthCheck.Result result;

        result = check.call();

        Assert.assertEquals(HealthCheck.State.UNKNOWN, result.getState());
        Assert.assertTrue(result.getMessage().isPresent());
        Assert.assertEquals("Disabled", result.getMessage().get());
        Assert.assertEquals(false, result.getDetails().get(MyHealthCheck.CHECK_ENABLED));

        check.getConfiguration().setEnabled(true);

        result = check.call();

        Assert.assertEquals(HealthCheck.State.UP, result.getState());
        Assert.assertFalse(result.getMessage().isPresent());
        Assert.assertFalse(result.getDetails().containsKey(MyHealthCheck.CHECK_ENABLED));
    }

    @Test
    public void testInterval() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.UP);
        check.getConfiguration().setEnabled(true);
        check.getConfiguration().setInterval(Duration.ofMillis(1000));

        HealthCheck.Result result1 = check.call();
        Assert.assertEquals(HealthCheck.State.UP, result1.getState());

        Thread.sleep(100);

        HealthCheck.Result result2 = check.call();
        Assert.assertEquals(HealthCheck.State.UP, result2.getState());
        Assert.assertEquals(result1.getDetails().get(MyHealthCheck.INVOCATION_TIME), result2.getDetails().get(MyHealthCheck.INVOCATION_TIME));
        Assert.assertEquals(result1.getDetails().get(MyHealthCheck.INVOCATION_COUNT), result2.getDetails().get(MyHealthCheck.INVOCATION_COUNT));
        Assert.assertNotEquals(check.getMetaData().get(MyHealthCheck.INVOCATION_ATTEMPT_TIME), result2.getDetails().get(MyHealthCheck.INVOCATION_TIME));

        Thread.sleep(1250);

        HealthCheck.Result result3 = check.call();
        Assert.assertEquals(HealthCheck.State.UP, result3.getState());
        Assert.assertNotEquals(result2.getDetails().get(MyHealthCheck.INVOCATION_TIME), result3.getDetails().get(MyHealthCheck.INVOCATION_TIME));
        Assert.assertNotEquals(result2.getDetails().get(MyHealthCheck.INVOCATION_COUNT), result3.getDetails().get(MyHealthCheck.INVOCATION_COUNT));
        Assert.assertEquals(check.getMetaData().get(MyHealthCheck.INVOCATION_ATTEMPT_TIME), result3.getDetails().get(MyHealthCheck.INVOCATION_TIME));
    }

    @Test
    public void testThreshold() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.DOWN);
        check.getConfiguration().setEnabled(true);
        check.getConfiguration().setFailureThreshold(3);

        HealthCheck.Result result;

        for (int i = 0; i < check.getConfiguration().getFailureThreshold(); i++) {
            result = check.call();

            Assert.assertEquals(HealthCheck.State.UP, result.getState());
            Assert.assertEquals(i + 1, result.getDetails().get(MyHealthCheck.INVOCATION_COUNT));
            Assert.assertEquals(i + 1, result.getDetails().get(MyHealthCheck.FAILURE_COUNT));
        }

        Assert.assertEquals(HealthCheck.State.DOWN, check.call().getState());
    }

    @Test
    public void testIntervalThreshold() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.DOWN);
        check.getConfiguration().setEnabled(true);
        check.getConfiguration().setInterval(Duration.ofMillis(500));
        check.getConfiguration().setFailureThreshold(3);

        HealthCheck.Result result;
        int icount;
        int fcount;

        for (int i = 0; i < check.getConfiguration().getFailureThreshold(); i++) {
            result = check.call();

            icount = (int)result.getDetails().get(MyHealthCheck.INVOCATION_COUNT);
            fcount = (int)result.getDetails().get(MyHealthCheck.FAILURE_COUNT);

            Assert.assertEquals(HealthCheck.State.UP, result.getState());
            Assert.assertEquals(i + 1, icount);
            Assert.assertEquals(i + 1, fcount);

            result = check.call();

            Assert.assertEquals(HealthCheck.State.UP, result.getState());
            Assert.assertEquals(icount, result.getDetails().get(MyHealthCheck.INVOCATION_COUNT));
            Assert.assertEquals(fcount, result.getDetails().get(MyHealthCheck.FAILURE_COUNT));

            Thread.sleep(550);
        }

        Assert.assertEquals(HealthCheck.State.DOWN, check.call().getState());
    }


    // ********************************
    //
    // ********************************

    private class MyHealthCheck extends AbstractHealthCheck {
        private HealthCheck.State state;

        MyHealthCheck() {
            super("my");

            this.state = HealthCheck.State.UP;
        }

        public void setState(State state) {
            this.state = state;
        }

        public State getState() {
            return state;
        }

        @Override
        public void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.state(state);
        }
    }
}
