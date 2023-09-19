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
package org.apache.camel.main;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.HealthCheckRegistryRepository;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainHealthCheckExcludedPatternTest {

    @Test
    public void testMainRoutesHealthCheckExcluded() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new Routes());
        main.addInitialProperty("camel.health.routes-enabled", "true");
        main.addInitialProperty("camel.health.exclude-pattern", "myseda");

        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);

            HealthCheckRegistry healthCheckRegistry
                    = camelContext.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
            assertNotNull(healthCheckRegistry);

            Optional<HealthCheckRepository> routes = healthCheckRegistry.getRepository("routes");
            assertTrue(routes.isPresent());

            RoutesHealthCheckRepository routesRepository = (RoutesHealthCheckRepository) routes.get();
            assertTrue(routesRepository.isEnabled());

            HealthCheck hc = healthCheckRegistry.getCheck("mydirect").get();
            assertTrue(hc.isEnabled());
            assertFalse(healthCheckRegistry.isExcluded(hc));

            hc = healthCheckRegistry.getCheck("myseda").get();
            assertTrue(hc.isEnabled());
            assertTrue(healthCheckRegistry.isExcluded(hc));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMainBasicHealthCheckRegistry() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new Routes());
        main.addInitialProperty("camel.health.exclude-pattern", "custom");

        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);

            final AtomicBoolean invoked = new AtomicBoolean();
            HealthCheck healthCheck = new AbstractHealthCheck("custom") {
                @Override
                protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                    invoked.set(true);
                }
            };

            // register custom health check
            camelContext.getRegistry().bind("custom", healthCheck);

            HealthCheckRegistry healthCheckRegistry
                    = camelContext.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
            assertNotNull(healthCheckRegistry);

            Optional<HealthCheckRepository> repository = healthCheckRegistry.getRepository("registry-health-check-repository");
            assertTrue(repository.isPresent());

            HealthCheckRegistryRepository registryRepository = (HealthCheckRegistryRepository) repository.get();
            assertTrue(registryRepository.isEnabled());

            List<HealthCheck> healthChecks = registryRepository.stream().toList();
            assertEquals(1, healthChecks.size());

            assertTrue(healthCheckRegistry.getCheck("custom").isPresent());

            // custom is excluded
            HealthCheckHelper.invoke(camelContext);
            assertFalse(invoked.get());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMainBasicHealthCheckAdded() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new Routes());
        main.addInitialProperty("camel.health.exclude-pattern", "custom");

        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);

            final AtomicBoolean invoked = new AtomicBoolean();
            HealthCheck healthCheck = new AbstractHealthCheck("custom") {
                @Override
                protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                    invoked.set(true);
                }
            };

            HealthCheckRegistry healthCheckRegistry
                    = camelContext.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
            assertNotNull(healthCheckRegistry);

            List<HealthCheck> healthChecks = healthCheckRegistry.stream().toList();
            int before = healthChecks.size();

            // register custom health check which should be excluded
            boolean added = healthCheckRegistry.register(healthCheck);
            assertTrue(added);

            healthChecks = healthCheckRegistry.stream().toList();
            int after = healthChecks.size();
            assertEquals(before + 1, after);

            assertTrue(healthCheckRegistry.getCheck("custom").isPresent());

            // custom is excluded
            HealthCheckHelper.invoke(camelContext);
            assertFalse(invoked.get());
        } finally {
            main.stop();
        }
    }

    static class Routes extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start").routeId("mydirect")
                    .to("log:direct");

            from("seda:start").routeId("myseda")
                    .to("log:seda");
        }
    }
}
