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
package org.apache.camel.microprofile.health;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck.Result;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Invokes health checks registered with a {@link HealthCheckRepository} and resolves / aggregates the results into a
 * single UP / DOWN status.
 */
final class CamelMicroProfileRepositoryHealthCheck implements HealthCheck {

    private final CamelContext camelContext;
    private final HealthCheckRepository repository;
    private final String name;

    CamelMicroProfileRepositoryHealthCheck(CamelContext camelContext, HealthCheckRepository repository, String name) {
        this.camelContext = camelContext;
        this.repository = repository;
        this.name = name;
    }

    @Override
    public HealthCheckResponse call() {
        final HealthCheckResponseBuilder builder = HealthCheckResponse.builder();
        builder.name(name);
        builder.up();

        if (repository.isEnabled()) {
            List<Result> results = repository.stream()
                    .filter(org.apache.camel.health.HealthCheck::isEnabled)
                    .map(org.apache.camel.health.HealthCheck::call)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // If any of the result statuses is DOWN, find the first one and report any error details
            HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
            results.stream()
                    .filter(result -> result.getState().equals(State.DOWN))
                    .findFirst()
                    .ifPresent(result -> {
                        CamelMicroProfileHealthHelper.applyHealthDetail(builder, result, registry.getExposureLevel());
                        builder.down();
                    });
        } else {
            builder.withData(AbstractHealthCheck.CHECK_ENABLED, false);
        }

        return builder.build();
    }
}
