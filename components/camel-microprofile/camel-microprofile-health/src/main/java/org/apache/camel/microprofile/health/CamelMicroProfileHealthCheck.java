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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import static org.apache.camel.health.HealthCheck.*;

/**
 * A MicroProfile {@link HealthCheck} that invokes the supplied Camel health check, reports its health status and
 * associated details.
 */
final class CamelMicroProfileHealthCheck implements HealthCheck {

    private final CamelContext camelContext;
    private final org.apache.camel.health.HealthCheck camelHealthCheck;

    CamelMicroProfileHealthCheck(CamelContext camelContext, org.apache.camel.health.HealthCheck camelHealthCheck) {
        this.camelContext = camelContext;
        this.camelHealthCheck = camelHealthCheck;
    }

    @Override
    public HealthCheckResponse call() {
        final HealthCheckResponseBuilder builder = HealthCheckResponse.builder();
        builder.name(camelHealthCheck.getId());
        builder.up();

        Result result = camelHealthCheck.call();
        Map<String, Object> details = result.getDetails();
        boolean enabled = true;

        if (details.containsKey(AbstractHealthCheck.CHECK_ENABLED)) {
            enabled = (boolean) details.get(AbstractHealthCheck.CHECK_ENABLED);
        }

        if (enabled) {
            HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);

            CamelMicroProfileHealthHelper.applyHealthDetail(builder, result, registry.getExposureLevel());

            if (result.getState() == State.DOWN) {
                builder.down();
            }
        } else {
            builder.withData(AbstractHealthCheck.CHECK_ENABLED, false);
        }

        return builder.build();
    }
}
