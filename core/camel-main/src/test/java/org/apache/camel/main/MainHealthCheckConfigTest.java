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

import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
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

    static class Routes extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start").to("log:direct");
            from("seda:start").to("log:seda");
        }
    }
}
