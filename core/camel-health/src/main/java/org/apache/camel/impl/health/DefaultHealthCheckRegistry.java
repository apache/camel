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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link HealthCheckRegistry}.
 */
@JdkService(HealthCheckRegistry.FACTORY)
@DeferredContextBinding
public class DefaultHealthCheckRegistry extends ServiceSupport implements HealthCheckRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHealthCheckRegistry.class);

    private String id = "camel-health";
    private final Set<HealthCheck> checks;
    private final Set<HealthCheckRepository> repositories;
    private CamelContext camelContext;
    private boolean enabled = true;

    public DefaultHealthCheckRegistry() {
        this(null);
    }

    public DefaultHealthCheckRegistry(CamelContext camelContext) {
        this.checks = new CopyOnWriteArraySet<>();
        this.repositories = new CopyOnWriteArraySet<>();
        this.repositories.add(new HealthCheckRegistryRepository());

        setCamelContext(camelContext);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
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
    protected void doInit() throws Exception {
        super.doInit();

        for (HealthCheck check : checks) {
            if (check instanceof CamelContextAware) {
                ((CamelContextAware) check).setCamelContext(camelContext);
            }
        }

        for (HealthCheckRepository repository : repositories) {
            if (repository instanceof CamelContextAware) {
                ((CamelContextAware) repository).setCamelContext(camelContext);
            }
        }
    }

    // ************************************
    // Properties
    // ************************************

    @Override
    public final void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public final CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Object resolveById(String id) {
        Object answer = resolveHealthCheckById(id);
        if (answer == null) {
            answer = resolveHealthCheckRepositoryById(id);
        }
        if (answer instanceof CamelContextAware) {
            ((CamelContextAware) answer).setCamelContext(camelContext);
        }
        return answer;
    }

    @SuppressWarnings("unchecked")
    private HealthCheck resolveHealthCheckById(String id) {
        HealthCheck answer = checks.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheck.class).get(id));
        if (answer == null) {
            // discover via classpath (try first via -health-check and then id as-is)
            FactoryFinder ff = camelContext.adapt(ExtendedCamelContext.class).getDefaultFactoryFinder();
            Class<? extends HealthCheck> clazz
                    = (Class<? extends HealthCheck>) ff.findOptionalClass(id + "-health-check").orElse(null);
            if (clazz == null) {
                clazz = (Class<? extends HealthCheck>) ff.findOptionalClass(id).orElse(null);
            }
            if (clazz != null) {
                answer = camelContext.getInjector().newInstance(clazz);
            }
        }

        return answer;
    }

    @SuppressWarnings("unchecked")
    private HealthCheckRepository resolveHealthCheckRepositoryById(String id) {
        HealthCheckRepository answer = repositories.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheckRepository.class).get(id));
        if (answer == null) {
            // discover via classpath (try first via -health-check-repository and then id as-is)
            FactoryFinder ff = camelContext.adapt(ExtendedCamelContext.class).getDefaultFactoryFinder();
            Class<? extends HealthCheckRepository> clazz = (Class<? extends HealthCheckRepository>) ff
                    .findOptionalClass(id + "-health-check-repository").orElse(null);
            if (clazz == null) {
                clazz = (Class<? extends HealthCheckRepository>) ff.findOptionalClass(id).orElse(null);
            }
            if (clazz != null) {
                answer = camelContext.getInjector().newInstance(clazz);
            }
        }

        return answer;
    }

    @Override
    public boolean register(Object obj) {
        boolean result;

        checkIfAccepted(obj);

        if (obj instanceof HealthCheck) {
            HealthCheck healthCheck = (HealthCheck) obj;
            // do we have this already
            if (getCheck(healthCheck.getId()).isPresent()) {
                return false;
            }
            result = checks.add(healthCheck);
            if (result) {
                if (obj instanceof CamelContextAware) {
                    ((CamelContextAware) obj).setCamelContext(camelContext);
                }

                LOG.debug("HealthCheck with id {} successfully registered", healthCheck.getId());
            }
        } else {
            HealthCheckRepository repository = (HealthCheckRepository) obj;
            // do we have this already
            if (getRepository(repository.getId()).isPresent()) {
                return false;
            }
            result = this.repositories.add(repository);
            if (result) {
                if (repository instanceof CamelContextAware) {
                    ((CamelContextAware) repository).setCamelContext(camelContext);
                }

                LOG.debug("HealthCheckRepository with id {} successfully registered", repository.getId());
            }
        }

        return result;
    }

    @Override
    public boolean unregister(Object obj) {
        boolean result;

        checkIfAccepted(obj);

        if (obj instanceof HealthCheck) {
            HealthCheck healthCheck = (HealthCheck) obj;
            result = checks.remove(healthCheck);
            if (result) {
                LOG.debug("HealthCheck with id {} successfully un-registered", healthCheck.getId());
            }
        } else {
            HealthCheckRepository repository = (HealthCheckRepository) obj;
            result = this.repositories.remove(repository);
            if (result) {
                LOG.debug("HealthCheckRepository with id {} successfully un-registered", repository.getId());
            }
        }

        return result;
    }

    // ************************************
    //
    // ************************************

    /**
     * Returns the repository identified by the given <code>id</code> if available.
     */
    public Optional<HealthCheckRepository> getRepository(String id) {
        return repositories.stream()
                // try also shorthand naming
                .filter(r -> ObjectHelper.equal(r.getId(), id)
                        || ObjectHelper.equal(r.getId().replace("-health-check-repository", ""), id))
                .findFirst();
    }

    @Override
    public Stream<HealthCheck> stream() {
        if (enabled) {
            return Stream.concat(
                    checks.stream(),
                    repositories.stream().flatMap(HealthCheckRepository::stream)).distinct();
        }
        return Stream.empty();
    }

    private void checkIfAccepted(Object obj) {
        boolean accept = obj instanceof HealthCheck || obj instanceof HealthCheckRepository;
        if (!accept) {
            throw new IllegalArgumentException();
        }
    }
}
