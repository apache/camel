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
package org.apache.camel.spring.health;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthCheckRegistryTest {

    @Test
    public void testHealthCheckRoutes() throws Exception {
        CamelContext context = createContext("org/apache/camel/spring/health/HealthCheckRegistryTest.xml");

        HealthCheckRegistry hc = context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        assertNotNull(hc);

        List<HealthCheck> checks = hc.stream().toList();
        assertEquals(2, checks.size());

        for (HealthCheck check : checks) {
            HealthCheck.Result response = check.call();

            assertEquals(HealthCheck.State.UP, response.getState());
            assertFalse(response.getMessage().isPresent());
            assertFalse(response.getError().isPresent());
        }

        context.getRouteController().stopRoute("foo");

        for (HealthCheck check : checks) {
            HealthCheck.Result response = check.call();
            boolean foo = "foo".equals(response.getDetails().get("route.id"));
            if (foo) {
                assertEquals(HealthCheck.State.DOWN, response.getState());
                assertTrue(response.getMessage().isPresent());
                assertFalse(response.getError().isPresent());
            } else {
                assertEquals(HealthCheck.State.UP, response.getState());
                assertFalse(response.getMessage().isPresent());
                assertFalse(response.getError().isPresent());
            }
        }
    }

    protected CamelContext createContext(String classpathConfigFile) {
        ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext(classpathConfigFile);

        CamelContext camelContext = appContext.getBean(CamelContext.class);
        assertNotNull(camelContext, "No Camel Context in file: " + classpathConfigFile);

        return camelContext;
    }
}
