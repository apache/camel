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

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthCheckTest {

    @Test
    public void testCheck() throws Exception {
        CamelContext context = new DefaultCamelContext();

        MyHealthCheck check = new MyHealthCheck();
        check.setCamelContext(context);
        check.setState(HealthCheck.State.UP);
        // disable
        check.setEnabled(false);

        HealthCheck.Result result;

        result = check.call();

        assertEquals(HealthCheck.State.UNKNOWN, result.getState());
        assertTrue(result.getMessage().isPresent());
        assertEquals("Disabled", result.getMessage().get());
        assertEquals(false, result.getDetails().get(AbstractHealthCheck.CHECK_ENABLED));

        check.setEnabled(true);

        result = check.call();

        assertEquals(HealthCheck.State.UP, result.getState());
        assertFalse(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
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
