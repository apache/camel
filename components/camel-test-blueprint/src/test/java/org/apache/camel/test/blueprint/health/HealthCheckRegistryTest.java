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
package org.apache.camel.test.blueprint.health;

import java.util.Collection;
import java.util.Optional;

import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.RegistryRepository;
import org.apache.camel.impl.health.RoutePerformanceCounterEvaluators;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class HealthCheckRegistryTest extends CamelBlueprintTestSupport {
    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/health/HealthCheckRegistryTest.xml";
    }

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
        Assert.assertEquals(2, repo.get().evaluators().count());
        Assert.assertEquals(1, repo.get().evaluators().filter(RoutePerformanceCounterEvaluators.ExchangesFailed.class::isInstance).count());
        Assert.assertEquals(1, repo.get().evaluators().filter(RoutePerformanceCounterEvaluators.LastProcessingTime.class::isInstance).count());
        Assert.assertEquals(1, repo.get().evaluators("route-1").count());
        Assert.assertEquals(1, repo.get().evaluators("route-1").filter(RoutePerformanceCounterEvaluators.ExchangesInflight.class::isInstance).count());
    }
}
