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

import org.apache.camel.Consumer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link HealthCheck} for a given consumer.
 */
public class ConsumerHealthCheck extends AbstractHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerHealthCheck.class);

    private final Consumer consumer;
    private final String routeId;

    public ConsumerHealthCheck(Consumer consumer, String id) {
        super("camel", id);
        this.consumer = consumer;
        this.routeId = id.replace("consumer:", "");
    }

    @Override
    public boolean isLiveness() {
        // this check is only for readiness
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        final ServiceStatus status = getCamelContext().getRouteController().getRouteStatus(routeId);
        builder.detail("route.id", routeId);
        builder.detail("route.status", status.name());
        builder.detail("route.context.name", getCamelContext().getName());

        if (consumer instanceof HealthCheckAware) {
            // health check is optional
            HealthCheck hc = ((HealthCheckAware) consumer).getHealthCheck();
            if (hc != null) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Calling HealthCheck on consumer route: {}", routeId);
                }
                Result result = hc.call();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("HealthCheck consumer route: {} -> {}", routeId, result.getState());
                }

                builder.state(result.getState());
                if (result.getMessage().isPresent()) {
                    builder.message(result.getMessage().get());
                }
                if (result.getError().isPresent()) {
                    builder.error(result.getError().get());
                }
                builder.details(result.getDetails());
                return;
            }
        }

        // consumer has no fine-grained health-check so check whether is started
        boolean started = true;
        if (consumer instanceof ServiceSupport) {
            started = ((ServiceSupport) consumer).isStarted();
        }
        builder.state(started ? State.UP : State.DOWN);
    }
}
