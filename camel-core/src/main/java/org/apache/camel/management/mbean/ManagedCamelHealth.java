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
package org.apache.camel.management.mbean;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedCamelHealthMBean;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;

public class ManagedCamelHealth implements ManagedCamelHealthMBean {
    private final CamelContext context;

    public ManagedCamelHealth(CamelContext context) {
        this.context = context;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    @Override
    public boolean getIsHealthy() {
        for (HealthCheck.Result result: HealthCheckHelper.invoke(context)) {
            if (result.getState() == HealthCheck.State.DOWN) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Collection<String> getHealthChecksIDs() {
        HealthCheckRegistry registry = context.getHealthCheckRegistry();
        if (registry != null) {
            return registry.getCheckIDs();
        }

        return Collections.emptyList();
    }

    @Override
    public TabularData details() {
        try {
            final TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.camelHealthDetailsTabularType());
            final CompositeType type = CamelOpenMBeanTypes.camelHealthDetailsCompositeType();

            for (HealthCheck.Result result: HealthCheckHelper.invoke(context)) {
                CompositeData data = new CompositeDataSupport(
                    type,
                    new String[] {
                        "id",
                        "group",
                        "state",
                        "enabled",
                        "interval",
                        "failureThreshold"
                    },
                    new Object[] {
                        result.getCheck().getId(),
                        result.getCheck().getGroup(),
                        result.getState().name(),
                        result.getCheck().getConfiguration().isEnabled(),
                        result.getCheck().getConfiguration().getInterval() != null
                            ? result.getCheck().getConfiguration().getInterval().toMillis()
                            : null,
                        result.getCheck().getConfiguration().getFailureThreshold()
                    }
                );

                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public String invoke(String id) {
        Optional<HealthCheck.Result> result = HealthCheckHelper.invoke(context, id, Collections.emptyMap());

        return result.map(r -> r.getState().name()).orElse(HealthCheck.State.UNKNOWN.name());
    }
}
