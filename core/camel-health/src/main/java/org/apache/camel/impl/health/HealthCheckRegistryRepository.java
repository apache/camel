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

import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * {@link HealthCheckRepository} that uses the Camel {@link org.apache.camel.spi.Registry}.
 *
 * Camel will use this by default, so there is no need to register this manually.
 */
public class HealthCheckRegistryRepository extends ServiceSupport
        implements CamelContextAware, HealthCheckRepository, StaticService, NonManagedService {
    private CamelContext context;
    private boolean enabled = true;

    @Override
    public String getId() {
        return "registry-health-check-repository";
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.context = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Stream<HealthCheck> stream() {
        if (context != null && enabled) {
            Set<HealthCheck> set = this.context.getRegistry().findByType(HealthCheck.class);
            return set.stream()
                    .map(this::toHealthCheck);
        } else {
            return Stream.empty();
        }
    }

    private HealthCheck toHealthCheck(HealthCheck hc) {
        return hc;
    }

}
