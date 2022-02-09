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
package org.apache.camel.management.mbean;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedCamelHealthMBean;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.spi.ManagementStrategy;

public class ManagedCamelHealth implements ManagedCamelHealthMBean {
    private final CamelContext context;
    private final HealthCheckRegistry healthCheckRegistry;

    public ManagedCamelHealth(CamelContext context, HealthCheckRegistry healthCheckRegistry) {
        this.context = context;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    @Override
    public boolean isEnabled() {
        return healthCheckRegistry.isEnabled();
    }

    @Override
    public boolean isHealthy() {
        for (HealthCheck.Result result : HealthCheckHelper.invoke(context)) {
            if (result.getState() == HealthCheck.State.DOWN) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isHealthyReadiness() {
        for (HealthCheck.Result result : HealthCheckHelper.invokeReadiness(context)) {
            if (result.getState() == HealthCheck.State.DOWN) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isHealthyLiveness() {
        for (HealthCheck.Result result : HealthCheckHelper.invokeLiveness(context)) {
            if (result.getState() == HealthCheck.State.DOWN) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Collection<String> getHealthChecksIDs() {
        return healthCheckRegistry.getCheckIDs();
    }

    @Override
    public TabularData details() {
        try {
            final TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.camelHealthDetailsTabularType());
            final CompositeType type = CamelOpenMBeanTypes.camelHealthDetailsCompositeType();

            for (HealthCheck.Result result : HealthCheckHelper.invoke(context)) {
                String failureUri = (String) result.getDetails().getOrDefault(HealthCheck.ENDPOINT_URI, "");
                Integer failureCount = (Integer) result.getDetails().getOrDefault(HealthCheck.FAILURE_COUNT, 0);

                String stacktrace = "";
                if (result.getError().isPresent()) {
                    try (StringWriter stackTraceWriter = new StringWriter();
                         PrintWriter pw = new PrintWriter(stackTraceWriter, true)) {
                        result.getError().get().printStackTrace(pw);
                        stacktrace = stackTraceWriter.getBuffer().toString();
                    } catch (IOException exception) {
                        // ignore
                    }
                }

                CompositeData data = new CompositeDataSupport(
                        type,
                        new String[] {
                                "id",
                                "group",
                                "state",
                                "enabled",
                                "message",
                                "failureUri",
                                "failureCount",
                                "failureStackTrace",
                                "readiness",
                                "liveness"
                        },
                        new Object[] {
                                result.getCheck().getId(),
                                result.getCheck().getGroup(),
                                result.getState().name(),
                                result.getCheck().isEnabled(),
                                result.getMessage().orElse(""),
                                failureUri,
                                failureCount,
                                stacktrace,
                                result.getCheck().isReadiness(),
                                result.getCheck().isLiveness()
                        });

                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public String invoke(String id) {
        Optional<HealthCheck.Result> result = HealthCheckHelper.invoke(context, id, Collections.emptyMap());

        return result.map(r -> r.getState().name()).orElse(HealthCheck.State.UNKNOWN.name());
    }

    @Override
    public void enableById(String id) {
        Optional<HealthCheck> hc = healthCheckRegistry.getCheck(id);
        if (hc.isPresent()) {
            hc.get().setEnabled(true);
        } else {
            Optional<HealthCheckRepository> hcr = healthCheckRegistry.getRepository(id);
            hcr.ifPresent(repository -> repository.setEnabled(true));
        }
    }

    @Override
    public void disableById(String id) {
        Optional<HealthCheck> hc = healthCheckRegistry.getCheck(id);
        if (hc.isPresent()) {
            hc.get().setEnabled(false);
        } else {
            Optional<HealthCheckRepository> hcr = healthCheckRegistry.getRepository(id);
            hcr.ifPresent(repository -> repository.setEnabled(false));
        }
    }
}
