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

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ContextHealthCheckTest {

    @Test
    public void testOverallStatusOnceTheContextIsStarted() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        ContextHealthCheck check = new ContextHealthCheck();
        check.setCamelContext(context);

        HealthCheck.Result result = check.call();

        assertEquals(HealthCheck.State.UP, result.getState());
        assertFalse(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
        assertEquals(ServiceStatus.Started, result.getDetails().get("context.status"));
    }

    @Test
    public void testOverallStatusOnceTheContextIsSuspended() {
        CamelContext context = new DefaultCamelContext();
        context.suspend();

        ContextHealthCheck check = new ContextHealthCheck();
        check.setCamelContext(context);

        HealthCheck.Result result = check.call();

        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertTrue(result.getMessage().isPresent());
        assertFalse(result.getDetails().containsKey(AbstractHealthCheck.CHECK_ENABLED));
        assertEquals(ServiceStatus.Suspended, result.getDetails().get("context.status"));
    }

}
