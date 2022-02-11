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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.smallrye.health.api.HealthRegistry;
import io.smallrye.health.api.HealthType;
import io.smallrye.health.registry.HealthRegistries;
import org.apache.camel.CamelContext;
import org.apache.camel.StartupListener;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.ConsumersHealthCheckRepository;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link HealthCheckRegistry} implementation to register Camel health checks as MicroProfile health checks on SmallRye
 * Health.
 */
public class CamelMicroProfileHealthCheckRegistry extends DefaultHealthCheckRegistry implements StartupListener {

    public static final String CONSUMERS_CHECK_NAME = "camel-consumers";
    public static final String ROUTES_CHECK_NAME = "camel-routes";
    private static final Logger LOG = LoggerFactory.getLogger(CamelMicroProfileHealthCheckRegistry.class);
    private final Set<HealthCheckRepository> repositories = new CopyOnWriteArraySet<>();

    public CamelMicroProfileHealthCheckRegistry() {
        this(null);
    }

    public CamelMicroProfileHealthCheckRegistry(CamelContext camelContext) {
        super(camelContext);
        super.setId("camel-microprofile-health");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        super.getCamelContext().addStartupListener(this);
    }

    @Override
    public boolean register(Object obj) {
        boolean registered = super.register(obj);
        if (obj instanceof HealthCheck) {
            HealthCheck check = (HealthCheck) obj;
            if (check.isEnabled()) {
                registerMicroProfileHealthCheck(check);
            }
        } else {
            HealthCheckRepository repository = (HealthCheckRepository) obj;
            if (repository.stream().findAny().isPresent()) {
                registerRepositoryChecks(repository);
            } else {
                // Try health check registration again on CamelContext started
                repositories.add(repository);
            }
        }
        return registered;
    }

    @Override
    public boolean unregister(Object obj) {
        boolean unregistered = super.unregister(obj);
        if (obj instanceof HealthCheck) {
            HealthCheck check = (HealthCheck) obj;
            removeMicroProfileHealthCheck(check);
        } else {
            HealthCheckRepository repository = (HealthCheckRepository) obj;
            if (repository instanceof ConsumersHealthCheckRepository || repository instanceof RoutesHealthCheckRepository) {
                try {
                    getReadinessRegistry().remove(repository.getId());
                } catch (IllegalStateException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed to remove repository readiness health {} check due to: {}", repository.getId(),
                                e.getMessage());
                    }
                }
            } else {
                repository.stream().forEach(this::removeMicroProfileHealthCheck);
            }
        }
        return unregistered;
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        //Noop
    }

    @Override
    public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        // Some repository checks may not be resolvable earlier in the lifecycle, so try one last time on CamelContext started
        if (alreadyStarted) {
            repositories.stream()
                    .filter(repository -> repository.stream().findAny().isPresent())
                    .forEach(this::registerRepositoryChecks);
            repositories.clear();
        }
    }

    protected void registerRepositoryChecks(HealthCheckRepository repository) {
        if (repository.isEnabled()) {
            // Since the number of potential checks for consumers / routes is non-deterministic
            // avoid registering each one with SmallRye health and instead aggregate the results so
            // that we avoid highly verbose health output
            if (repository instanceof ConsumersHealthCheckRepository) {
                CamelMicroProfileRepositoryHealthCheck repositoryHealthCheck
                        = new CamelMicroProfileRepositoryHealthCheck(repository, CONSUMERS_CHECK_NAME);
                getReadinessRegistry().register(repository.getId(), repositoryHealthCheck);
            } else if (repository instanceof RoutesHealthCheckRepository) {
                CamelMicroProfileRepositoryHealthCheck repositoryHealthCheck
                        = new CamelMicroProfileRepositoryHealthCheck(repository, ROUTES_CHECK_NAME);
                getReadinessRegistry().register(repository.getId(), repositoryHealthCheck);
            } else {
                repository.stream()
                        .filter(healthCheck -> healthCheck.isEnabled())
                        .forEach(this::registerMicroProfileHealthCheck);
            }
        }
    }

    protected void registerMicroProfileHealthCheck(HealthCheck camelHealthCheck) {
        org.eclipse.microprofile.health.HealthCheck microProfileHealthCheck
                = new CamelMicroProfileHealthCheck(camelHealthCheck);

        if (camelHealthCheck.isReadiness()) {
            getReadinessRegistry().register(camelHealthCheck.getId(), microProfileHealthCheck);
        }

        if (camelHealthCheck.isLiveness()) {
            getLivenessRegistry().register(camelHealthCheck.getId(), microProfileHealthCheck);
        }
    }

    protected void removeMicroProfileHealthCheck(HealthCheck camelHealthCheck) {
        if (camelHealthCheck.isReadiness()) {
            try {
                getReadinessRegistry().remove(camelHealthCheck.getId());
            } catch (IllegalStateException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to remove readiness health check due to: {}", e.getMessage());
                }
            }
        }

        if (camelHealthCheck.isLiveness()) {
            try {
                getLivenessRegistry().remove(camelHealthCheck.getId());
            } catch (IllegalStateException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to remove liveness health check due to: {}", e.getMessage());
                }
            }
        }
    }

    protected HealthRegistry getLivenessRegistry() {
        return HealthRegistries.getRegistry(HealthType.LIVENESS);
    }

    protected HealthRegistry getReadinessRegistry() {
        return HealthRegistries.getRegistry(HealthType.READINESS);
    }
}
