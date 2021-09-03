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
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
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

public class MainHealthCheckConfigTest {

    @Test
    public void testMainRoutesHealthCheckConfiguration() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new Routes());
        main.addInitialProperty("camel.health.config[direct].parent", "routes");
        main.addInitialProperty("camel.health.config[direct].enabled", "true");
        main.addInitialProperty("camel.health.config[seda].parent", "routes");
        main.addInitialProperty("camel.health.config[seda].enabled", "false");
        main.addInitialProperty("camel.health.routes-enabled", "true");

        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);

            HealthCheckRegistry healthCheckRegistry = camelContext.getExtension(HealthCheckRegistry.class);
            assertNotNull(healthCheckRegistry);

            Optional<HealthCheckRepository> routes = healthCheckRegistry.getRepository("routes");
            assertTrue(routes.isPresent());

            RoutesHealthCheckRepository routesRepository = (RoutesHealthCheckRepository) routes.get();
            assertTrue(routesRepository.isEnabled());

            Map<String, HealthCheckConfiguration> configurations = routesRepository.getConfigurations();
            assertNotNull(configurations);
            assertEquals(2, configurations.size());

            HealthCheckConfiguration direct = configurations.get("direct");
            assertNotNull(direct);
            assertTrue(direct.isEnabled());

            HealthCheckConfiguration seda = configurations.get("seda");
            assertNotNull(seda);
            assertFalse(seda.isEnabled());
        } finally {
            main.stop();
        }
    }

    @Test
    public void testMainBasicHealthCheckConfiguration() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new Routes());
        main.addInitialProperty("camel.health.config[custom].parent", "registry-health-check-repository");
        main.addInitialProperty("camel.health.config[custom].enabled", "false");
        main.addInitialProperty("camel.health.config[custom].interval", "20s");
        main.addInitialProperty("camel.health.config[custom].failure-threshold", "10");

        main.start();
        try {
            CamelContext camelContext = main.getCamelContext();
            assertNotNull(camelContext);

            HealthCheck healthCheck = new AbstractHealthCheck("custom") {
                @Override
                protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
                    // Noop
                }
            };

            // This configuration will be overridden by the camel-main config properties
            healthCheck.getConfiguration().setEnabled(true);
            healthCheck.getConfiguration().setInterval(10);
            healthCheck.getConfiguration().setFailureThreshold(5);
            camelContext.getRegistry().bind("custom", healthCheck);

            HealthCheckRegistry healthCheckRegistry = camelContext.getExtension(HealthCheckRegistry.class);
            assertNotNull(healthCheckRegistry);

            Optional<HealthCheckRepository> repository = healthCheckRegistry.getRepository("registry-health-check-repository");
            assertTrue(repository.isPresent());

            HealthCheckRegistryRepository registryRepository = (HealthCheckRegistryRepository) repository.get();
            assertTrue(registryRepository.isEnabled());

            List<HealthCheck> healthChecks = registryRepository.stream().collect(Collectors.toList());
            assertEquals(1, healthChecks.size());

            HealthCheck myCustomCheck = healthChecks.get(0);
            HealthCheckConfiguration configuration = myCustomCheck.getConfiguration();
            assertNotNull(configuration);
            assertFalse(configuration.isEnabled());
            assertEquals(20000, configuration.getInterval());
            assertEquals(10, configuration.getFailureThreshold());
        } finally {
            main.stop();
        }
    }

    static class Routes extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start").to("log:direct");
            from("seda:start").to("log:seda");
        }
    }
}
