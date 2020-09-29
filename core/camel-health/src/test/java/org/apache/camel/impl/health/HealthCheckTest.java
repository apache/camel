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
package org.apache.camel.impl.health;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthCheckTest {

    @Test
    public void testCheck() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.UP);
        // disable
        check.getConfiguration().setEnabled(false);

        HealthCheck.Result result;

        result = check.call();

        assertEquals(HealthCheck.State.UNKNOWN, result.getState());
        assertTrue(result.getMessage().isPresent());
        assertEquals("Disabled", result.getMessage().get());
        assertEquals(false, result.getDetails().get(AbstractHealthCheck.CHECK_ENABLED));

        check.getConfiguration().setEnabled(true);

        result = check.call();

        assertEquals(HealthCheck.State.UP, result.getState());
        assertFalse(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
    }

    @Test
    public void testInterval() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.UP);
        check.getConfiguration().setEnabled(true);
        check.getConfiguration().setInterval(1000);

        HealthCheck.Result result1 = check.call();
        assertEquals(HealthCheck.State.UP, result1.getState());

        Thread.sleep(100);

        HealthCheck.Result result2 = check.call();
        assertEquals(HealthCheck.State.UP, result2.getState());
        assertEquals(result1.getDetails().get(AbstractHealthCheck.INVOCATION_TIME),
                result2.getDetails().get(AbstractHealthCheck.INVOCATION_TIME));
        assertEquals(result1.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT),
                result2.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT));
        assertNotEquals(check.getMetaData().get(AbstractHealthCheck.INVOCATION_ATTEMPT_TIME),
                result2.getDetails().get(AbstractHealthCheck.INVOCATION_TIME));

        Thread.sleep(1250);

        HealthCheck.Result result3 = check.call();
        assertEquals(HealthCheck.State.UP, result3.getState());
        assertNotEquals(result2.getDetails().get(AbstractHealthCheck.INVOCATION_TIME),
                result3.getDetails().get(AbstractHealthCheck.INVOCATION_TIME));
        assertNotEquals(result2.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT),
                result3.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT));
        assertEquals(check.getMetaData().get(AbstractHealthCheck.INVOCATION_ATTEMPT_TIME),
                result3.getDetails().get(AbstractHealthCheck.INVOCATION_TIME));
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

            assertEquals(HealthCheck.State.UP, result.getState());
            assertEquals(i + 1, result.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT));
            assertEquals(i + 1, result.getDetails().get(AbstractHealthCheck.FAILURE_COUNT));
        }

        assertEquals(HealthCheck.State.DOWN, check.call().getState());
    }

    @Test
    public void testIntervalThreshold() throws Exception {
        MyHealthCheck check = new MyHealthCheck();
        check.setState(HealthCheck.State.DOWN);
        check.getConfiguration().setEnabled(true);
        check.getConfiguration().setInterval(500);
        check.getConfiguration().setFailureThreshold(3);

        HealthCheck.Result result;
        int icount;
        int fcount;

        for (int i = 0; i < check.getConfiguration().getFailureThreshold(); i++) {
            result = check.call();

            icount = (int) result.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT);
            fcount = (int) result.getDetails().get(AbstractHealthCheck.FAILURE_COUNT);

            assertEquals(HealthCheck.State.UP, result.getState());
            assertEquals(i + 1, icount);
            assertEquals(i + 1, fcount);

            result = check.call();

            assertEquals(HealthCheck.State.UP, result.getState());
            assertEquals(icount, result.getDetails().get(AbstractHealthCheck.INVOCATION_COUNT));
            assertEquals(fcount, result.getDetails().get(AbstractHealthCheck.FAILURE_COUNT));

            Thread.sleep(550);
        }

        assertEquals(HealthCheck.State.DOWN, check.call().getState());
    }

    // ********************************
    //
    // ********************************

    private static class MyHealthCheck extends AbstractHealthCheck {
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
