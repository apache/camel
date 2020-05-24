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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class DefaultHealthCheckServiceTest {

    @Test(timeout = 10000)
    public void testDefaultHealthCheckService() throws Exception {
        CamelContext context = null;

        try {
            MyHealthCheck check = new MyHealthCheck("test", HealthCheck.State.UP);
            List<HealthCheck.State> results = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(10);

            DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
            registry.register(check);

            DefaultHealthCheckService service = new DefaultHealthCheckService();
            Map<String, Object> options = new HashMap<>();
            options.put("test", "test");

            service.setHealthCheckOptions("test", options);
            service.setCheckInterval(500, TimeUnit.MILLISECONDS);
            service.addStateChangeListener((s, c) -> {
                results.add(s);
                check.flip();
                latch.countDown();
            });

            context = new DefaultCamelContext();
            context.setExtension(HealthCheckRegistry.class, registry);
            context.addService(service);
            context.start();

            latch.await();

            for (int i = 0; i < results.size(); i++) {
                if (i % 2 == 0) {
                    Assert.assertEquals(HealthCheck.State.UP, results.get(i));
                } else {
                    Assert.assertEquals(HealthCheck.State.DOWN, results.get(i));
                }
            }

            Assert.assertEquals(1, service.getResults().size());
            Assert.assertEquals(check, service.getResults().iterator().next().getCheck());
            Assert.assertEquals(options, check.getOptions());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    // ********************************
    //
    // ********************************

    private class MyHealthCheck extends AbstractHealthCheck {
        private HealthCheck.State state;
        private Map<String, Object> options;

        MyHealthCheck(String id, HealthCheck.State state) {
            super(id);
            getConfiguration().setEnabled(true);

            this.state = state;
        }

        public void flip() {
            this.state = this.state == State.UP ? State.DOWN : State.UP;
        }

        @Override
        public void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.state(state);
            this.options = options;
        }

        public Map<String, Object> getOptions() {
            return options;
        }
    }
}
