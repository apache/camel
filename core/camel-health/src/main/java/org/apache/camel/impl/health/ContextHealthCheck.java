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
package org.apache.camel.impl.health;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheckResultBuilder;

/**
 * {@link org.apache.camel.health.HealthCheck} that checks the status of the {@link CamelContext} whether its started or
 * not.
 */
@org.apache.camel.spi.annotations.HealthCheck("context-check")
public final class ContextHealthCheck extends AbstractHealthCheck {

    public ContextHealthCheck() {
        super("camel", "context");
    }

    @Override
    public int getOrder() {
        // context should always be first
        return Ordered.HIGHEST;
    }

    @Override
    public boolean isLiveness() {
        // context is also liveness to ensure we have at least one liveness check
        return true;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        builder.unknown();

        if (getCamelContext() != null) {
            ServiceStatus status = getCamelContext().getStatus();
            byte phase = getCamelContext().getCamelContextExtension().getStatusPhase();
            String name = getCamelContext().getName();

            builder.detail("context.name", name);
            builder.detail("context.version", getCamelContext().getVersion());
            builder.detail("context.status", status);
            builder.detail("context.phase", phase);

            switch (status) {
                case Initializing:
                case Initialized:
                case Starting:
                    builder.message(
                            "Camel Context '" + name + "' is starting. Status: '" + status + "', Phase: '" + phase
                                    + "'. Please wait...");
                    builder.down();
                    break;
                case Started:
                    builder.up();
                case Stopping:
                case Stopped:
                case Suspending:
                case Suspended:
                    builder.message("Camel Context '" + name + "' is shutting down. Status: '" + status + "', Phase: '" + phase
                                    + "'. Please check the debug log");
                    builder.down();
                default:
                    builder.message("Camel Context '" + name + "' has unknown Status: " + status);
                    builder.down();
            }
        }
    }
}
