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

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck.Result;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.impl.health.ContextHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

/**
 * A simple health check implementation for checking the status of a CamelContext
 */
@Readiness
@Liveness
public class CamelMicroProfileContextCheck implements HealthCheck, CamelContextAware {

    @Inject
    private CamelContext camelContext;

    private ContextHealthCheck contextHealthCheck = new ContextHealthCheck();

    public CamelMicroProfileContextCheck() {
        contextHealthCheck.getConfiguration().setEnabled(true);
    }

    @Override
    public HealthCheckResponse call() {
        final HealthCheckResponseBuilder builder = HealthCheckResponse.builder();
        builder.name("camel");
        builder.down();

        if (camelContext != null) {
            contextHealthCheck.setCamelContext(camelContext);

            Result result = contextHealthCheck.call();
            Map<String, Object> details = result.getDetails();
            builder.withData("name", details.get("context.name").toString());
            builder.withData("contextStatus", details.get("context.status").toString());

            if (result.getState().equals(State.UP)) {
                builder.up();
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
}
