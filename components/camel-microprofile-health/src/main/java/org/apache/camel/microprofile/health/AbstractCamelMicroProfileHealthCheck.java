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

import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck.Result;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.health.HealthCheckFilter;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Invokes Camel health checks and adds their results into the HealthCheckResponseBuilder
 */
public abstract class AbstractCamelMicroProfileHealthCheck implements HealthCheck, CamelContextAware {

    @Inject
    protected CamelContext camelContext;

    @Override
    public HealthCheckResponse call() {
        final HealthCheckResponseBuilder builder = HealthCheckResponse.builder();
        builder.name(getHealthCheckName());

        if (camelContext != null) {
            Collection<Result> results = HealthCheckHelper.invoke(camelContext, (HealthCheckFilter) check -> check.getGroup() != null && check.getGroup().equals(getHealthGroupFilterExclude()));
            if (!results.isEmpty()) {
                builder.up();
            }

            for (Result result: results) {
                Map<String, Object> details = result.getDetails();
                boolean enabled = true;

                if (details.containsKey(AbstractHealthCheck.CHECK_ENABLED)) {
                    enabled = (boolean) details.get(AbstractHealthCheck.CHECK_ENABLED);
                }

                if (enabled) {
                    builder.withData(result.getCheck().getId(), result.getState().name());
                    if (result.getState() == State.DOWN) {
                        builder.down();
                    }
                }
            }
        }

        return builder.build();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    /**
     * Gets the name of a specific health group to exclude when invoking a Camel HealthCheck
     * @return the health group to exclude
     */
    abstract String getHealthGroupFilterExclude();

    /**
     * Gets the name of the health check which will be used as a heading for the associated checks.
     * @return the health check name
     */
    abstract String getHealthCheckName();
}
