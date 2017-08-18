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
package org.apache.camel.spring.boot.actuate.health;

import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckFilter;
import org.apache.camel.health.HealthCheckHelper;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Camel {@link org.apache.camel.health.HealthCheck} {@link HealthIndicator}.
 */
public class CamelHealthCheckIndicator extends AbstractHealthIndicator {
    private final CamelContext camelContext;
    private final List<HealthCheckFilter> filters;

    public CamelHealthCheckIndicator(CamelContext camelContext, List<HealthCheckFilter> filters) {
        this.camelContext = camelContext;
        this.filters = filters;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // By default the status is unknown.
        builder.unknown();

        if (camelContext != null) {
            Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(
                camelContext,
                (HealthCheck check) -> filters.stream().anyMatch(p -> p.test(check))
            );

            if (!results.isEmpty()) {
                // assuming the status is up unless a specific check is failing
                // which is determined later.
                builder.up();
            }

            for (HealthCheck.Result result: results) {
                builder.withDetail(result.getCheck().getId(), result.getState().name());

                if (result.getState() == HealthCheck.State.DOWN) {
                    builder.down();
                }
            }
        }
    }
}
