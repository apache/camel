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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Repository for producer {@link HealthCheck}s.
 */
@org.apache.camel.spi.annotations.HealthCheck(ProducersHealthCheckRepository.REPOSITORY_NAME)
@DeferredContextBinding
public class ProducersHealthCheckRepository extends ServiceSupport
        implements CamelContextAware, WritableHealthCheckRepository, StaticService, NonManagedService {

    public static final String REPOSITORY_ID = "producers";
    public static final String REPOSITORY_NAME = "producers-repository";

    private final List<HealthCheck> checks;
    private volatile CamelContext context;
    private boolean enabled; // default disabled

    public ProducersHealthCheckRepository() {
        this.checks = new CopyOnWriteArrayList<>();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.context = camelContext;
    }

    @Override
    public String getId() {
        return REPOSITORY_ID;
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
        return this.context != null && enabled
                ? checks.stream()
                : Stream.empty();
    }

    @Override
    public void addHealthCheck(HealthCheck healthCheck) {
        CamelContextAware.trySetCamelContext(healthCheck, getCamelContext());
        this.checks.add(healthCheck);
    }

    @Override
    public void removeHealthCheck(HealthCheck healthCheck) {
        this.checks.remove(healthCheck);
    }
}
