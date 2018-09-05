/**
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
package org.apache.camel.spring.boot.health;

import java.util.Collection;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.RegistryRepository;
import org.apache.camel.impl.health.RoutePerformanceCounterEvaluators;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.actuate.health.CamelHealthAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelHealthAutoConfiguration.class,
        HealthCheckRoutesAutoConfiguration.class
    },
    properties = {
        "camel.health.check.routes.enabled = true",
        "camel.health.check.routes.thresholds.exchanges-failed = 1",
        "camel.health.check.routes.thresholds.last-processing-time.threshold = 1",
        "camel.health.check.routes.thresholds.last-processing-time.failures = 2",
        "camel.health.check.routes.threshold[route-1].inherit = false",
        "camel.health.check.routes.threshold[route-1].exchanges-inflight = 1",
        "camel.health.check.routes.threshold[route-2].inherit = true",
        "camel.health.check.routes.threshold[route-2].exchanges-inflight = 1",
    }
)
public class HealthCheckRegistryTest extends Assert {
    @Autowired
    private CamelContext context;

    @Test
    public void testRepositories() {
        Collection<HealthCheckRepository> repos = context.getHealthCheckRegistry().getRepositories();

        Assert.assertNotNull(repos);
        Assert.assertEquals(2, repos.size());
        Assert.assertTrue(repos.stream().anyMatch(RegistryRepository.class::isInstance));
        Assert.assertTrue(repos.stream().anyMatch(RoutesHealthCheckRepository.class::isInstance));

        Optional<RoutesHealthCheckRepository> repo = repos.stream()
            .filter(RoutesHealthCheckRepository.class::isInstance)
            .map(RoutesHealthCheckRepository.class::cast)
            .findFirst();

        Assert.assertTrue(repo.isPresent());

        // default thresholds configuration
        Assert.assertEquals(2, repo.get().evaluators().count());
        Assert.assertEquals(1, repo.get().evaluators().filter(RoutePerformanceCounterEvaluators.ExchangesFailed.class::isInstance).count());
        Assert.assertEquals(1, repo.get().evaluators().filter(RoutePerformanceCounterEvaluators.LastProcessingTime.class::isInstance).count());

        // route-1 does not inherit from default thresholds configuration
        Assert.assertEquals(1, repo.get().evaluators("route-1").count());
        Assert.assertEquals(1, repo.get().evaluators("route-1").filter(RoutePerformanceCounterEvaluators.ExchangesInflight.class::isInstance).count());

        // route-2 inherits from default thresholds configuration
        Assert.assertEquals(3, repo.get().evaluators("route-2").count());
        Assert.assertEquals(1, repo.get().evaluators("route-2").filter(RoutePerformanceCounterEvaluators.ExchangesFailed.class::isInstance).count());
        Assert.assertEquals(1, repo.get().evaluators("route-2").filter(RoutePerformanceCounterEvaluators.LastProcessingTime.class::isInstance).count());
        Assert.assertEquals(1, repo.get().evaluators("route-2").filter(RoutePerformanceCounterEvaluators.ExchangesInflight.class::isInstance).count());
    }
}
