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
package org.apache.camel.impl.health;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHealthCheckRegistry implements HealthCheckRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHealthCheckRegistry.class);

    private final Set<HealthCheck> checks;
    private final Set<HealthCheckRepository> repositories;
    private CamelContext camelContext;

    public DefaultHealthCheckRegistry() {
        this(null);
    }

    public DefaultHealthCheckRegistry(CamelContext camelContext) {
        this.checks = new CopyOnWriteArraySet<>();

        this.repositories = new CopyOnWriteArraySet<>();
        this.repositories.add(new RegistryRepository());
        this.repositories.addAll(repositories);

        setCamelContext(camelContext);
    }

    // ************************************
    // Properties
    // ************************************

    @Override
    public final void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;

        for (HealthCheck check: checks) {
            if (check instanceof CamelContextAware) {
                ((CamelContextAware) check).setCamelContext(camelContext);
            }
        }

        for (HealthCheckRepository repository: repositories) {
            if (repository instanceof CamelContextAware) {
                ((CamelContextAware) repository).setCamelContext(camelContext);
            }
        }
    }

    @Override
    public final CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public boolean register(HealthCheck check) {
        boolean result = checks.add(check);
        if (result) {
            if (check instanceof CamelContextAware) {
                ((CamelContextAware) check).setCamelContext(camelContext);
            }

            LOGGER.debug("HealthCheck with id {} successfully registered", check.getId());
        }

        return result;
    }

    @Override
    public boolean unregister(HealthCheck check) {
        boolean result = checks.remove(check);
        if (result) {
            LOGGER.debug("HealthCheck with id {} successfully un-registered", check.getId());
        }

        return result;
    }

    @Override
    public void setRepositories(Collection<HealthCheckRepository> repositories) {
        repositories.clear();
        repositories.addAll(repositories);
    }
    
    @Override
    public Collection<HealthCheckRepository> getRepositories() {
        return Collections.unmodifiableCollection(repositories);
    }

    @Override
    public boolean addRepository(HealthCheckRepository repository) {
        boolean result = repositories.add(repository);
        if (result) {
            if (repository instanceof CamelContextAware) {
                ((CamelContextAware) repository).setCamelContext(getCamelContext());

                LOGGER.debug("HealthCheckRepository {} successfully registered", repository);
            }
        }

        return result;
    }

    @Override
    public boolean removeRepository(HealthCheckRepository repository) {
        boolean result = repositories.remove(repository);
        if (result) {
            LOGGER.debug("HealthCheckRepository with {} successfully un-registered", repository);
        }

        return result;
    }

    // ************************************
    //
    // ************************************

    @Override
    public Stream<HealthCheck> stream() {
        return Stream.concat(
            checks.stream(),
            repositories.stream().flatMap(HealthCheckRepository::stream)
        ).distinct();
    }
}
