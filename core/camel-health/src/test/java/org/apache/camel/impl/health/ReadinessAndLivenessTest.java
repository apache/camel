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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadinessAndLivenessTest {

    @Test
    public void testLiveAndReady() throws Exception {
        CamelContext context = new DefaultCamelContext();

        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);

        context.getRegistry().bind("check1", new MyReadyCheck("G1", "1"));
        context.getRegistry().bind("check2", new MyLiveCheck("G1", "2"));

        context.start();
        registry.start();

        List<HealthCheck> checks = registry.stream().collect(Collectors.toList());
        assertEquals(2, checks.size());

        Collection<HealthCheck.Result> results = HealthCheckHelper.invokeReadiness(context);
        assertEquals(1, results.size());
        HealthCheck.Result result = results.iterator().next();
        assertEquals(HealthCheck.State.UP, result.getState());
        assertFalse(result.getCheck().isLiveness());
        assertTrue(result.getCheck().isReadiness());
        assertTrue(result.getCheck() instanceof MyReadyCheck);

        results = HealthCheckHelper.invokeLiveness(context);
        assertEquals(1, results.size());
        result = results.iterator().next();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertTrue(result.getCheck().isLiveness());
        assertFalse(result.getCheck().isReadiness());
        assertTrue(result.getCheck() instanceof MyLiveCheck);
    }

    private static class MyReadyCheck extends AbstractHealthCheck implements CamelContextAware {

        private CamelContext context;

        protected MyReadyCheck(String group, String id) {
            super(group, id);
        }

        @Override
        public boolean isLiveness() {
            return false;
        }

        @Override
        public void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.up();
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.context = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return context;
        }
    }

    private static class MyLiveCheck extends AbstractHealthCheck implements CamelContextAware {

        private CamelContext context;

        protected MyLiveCheck(String group, String id) {
            super(group, id);
        }

        @Override
        public boolean isReadiness() {
            return false;
        }

        @Override
        public void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.down();
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.context = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return context;
        }
    }
}
