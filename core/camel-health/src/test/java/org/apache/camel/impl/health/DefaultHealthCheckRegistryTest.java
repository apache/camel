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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class DefaultHealthCheckRegistryTest {

    @Test
    public void testDefaultHealthCheckRegistryRepositorySetter() {
        HealthCheckRegistry registry1 = new DefaultHealthCheckRegistry();
        HealthCheckRegistry registry2 = new DefaultHealthCheckRegistry();
        registry1.addRepository(() -> Stream.of(new MyHealthCheck("G1", "1")));
        registry2.setRepositories(registry1.getRepositories());
        Assert.assertArrayEquals(registry1.getRepositories().toArray(), registry2.getRepositories().toArray());
    }

    @Test
    public void testDefaultHealthCheckRegistry() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setIncludeContextCheck(false);
        registry.register(new MyHealthCheck("G1", "1"));
        registry.register(new MyHealthCheck("G1", "1"));
        registry.register(new MyHealthCheck("G1", "2"));
        registry.register(new MyHealthCheck("G2", "3"));

        List<HealthCheck> checks = registry.stream().collect(Collectors.toList());
        Assert.assertEquals(3, checks.size());

        for (HealthCheck check : checks) {
            HealthCheck.Result response = check.call();

            Assert.assertEquals(HealthCheck.State.UP, response.getState());
            Assert.assertFalse(response.getMessage().isPresent());
            Assert.assertFalse(response.getError().isPresent());
        }
    }

    @Test
    public void testDefaultHealthCheckRegistryWithRepositories() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setIncludeContextCheck(false);

        registry.register(new MyHealthCheck("G1", "1"));
        registry.register(new MyHealthCheck("G1", "1"));
        registry.register(new MyHealthCheck("G1", "2"));
        registry.register(new MyHealthCheck("G2", "3"));

        registry.addRepository(() -> Stream.of(new MyHealthCheck("G1", "1"), new MyHealthCheck("G1", "4")));

        List<HealthCheck> checks = registry.stream().collect(Collectors.toList());
        Assert.assertEquals(4, checks.size());
        Assert.assertEquals(1, checks.stream().filter(h -> h.getId().equals("4")).count());
        Assert.assertEquals(3, checks.stream().filter(h -> h.getGroup().equals("G1")).count());

        for (HealthCheck check : checks) {
            HealthCheck.Result response = check.call();

            Assert.assertEquals(HealthCheck.State.UP, response.getState());
            Assert.assertFalse(response.getMessage().isPresent());
            Assert.assertFalse(response.getError().isPresent());
        }
    }

    @Test
    public void testInjectCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();

        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);

        registry.register(new MyHealthCheck("G1", "1"));
        registry.register(new MyHealthCheck("G1", "2"));
        registry.register(new MyHealthCheck("G2", "3"));

        context.start();
        registry.start();

        List<HealthCheck> checks = registry.stream().collect(Collectors.toList());
        // should also include default
        Assert.assertEquals(4, checks.size());

        for (HealthCheck check : checks) {
            HealthCheck.Result response = check.call();

            Assert.assertEquals(HealthCheck.State.UP, response.getState());
            Assert.assertFalse(response.getMessage().isPresent());
            Assert.assertFalse(response.getError().isPresent());
            Assert.assertSame(context, ((CamelContextAware) check).getCamelContext());
        }
    }

    private class MyHealthCheck extends AbstractHealthCheck implements CamelContextAware {

        private CamelContext context;

        protected MyHealthCheck(String group, String id) {
            super(group, id);
            getConfiguration().setEnabled(true);
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
}
