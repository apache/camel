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

import java.util.Collection;
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
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link HealthCheckRegistry}.
 */
@org.apache.camel.spi.annotations.HealthCheck(HealthCheckRegistry.NAME)
@DeferredContextBinding
public class DefaultHealthCheckRegistry extends ServiceSupport implements HealthCheckRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHealthCheckRegistry.class);

    private String id = "camel-health";
    private final Set<HealthCheck> checks;
    private final Set<HealthCheckRepository> repositories;
    private CamelContext camelContext;
    private boolean enabled = true;
    private volatile boolean loadHealthChecksDone;

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
            CamelContextAware.trySetCamelContext(check, camelContext);
        }

        for (HealthCheckRepository repository : repositories) {
            CamelContextAware.trySetCamelContext(repository, camelContext);
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
        CamelContextAware.trySetCamelContext(answer, camelContext);
        return answer;
    }

    @SuppressWarnings("unchecked")
    private HealthCheck resolveHealthCheckById(String id) {
        HealthCheck answer = checks.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheck.class).get(id));
        if (answer == null) {
            HealthCheckResolver resolver = camelContext.adapt(ExtendedCamelContext.class).getHealthCheckResolver();
            answer = resolver.resolveHealthCheck(id);
        }

        return answer;
    }

    @SuppressWarnings("unchecked")
    private HealthCheckRepository resolveHealthCheckRepositoryById(String id) {
        HealthCheckRepository answer = repositories.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheckRepository.class).get(id));
        if (answer == null) {
            // discover via classpath (try first via -health-check-repository and then id as-is)
            HealthCheckResolver resolver = camelContext.adapt(ExtendedCamelContext.class).getHealthCheckResolver();
            answer = resolver.resolveHealthCheckRepository(id);
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
                CamelContextAware.trySetCamelContext(obj, camelContext);
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
                CamelContextAware.trySetCamelContext(repository, camelContext);
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

    @Override
    public void loadHealthChecks() {
        StopWatch watch = new StopWatch();

        if (!loadHealthChecksDone) {
            loadHealthChecksDone = true;
            DefaultHealthChecksLoader loader = new DefaultHealthChecksLoader(camelContext);
            Collection<HealthCheck> col = loader.loadHealthChecks();
            // register loaded health-checks
            col.forEach(this::register);
            if (col.size() > 0) {
                String time = TimeUtils.printDuration(watch.taken());
                LOG.info("Health checks (scanned: {}) loaded in {}", col.size(), time);
            }
        }
    }

    private void checkIfAccepted(Object obj) {
        boolean accept = obj instanceof HealthCheck || obj instanceof HealthCheckRepository;
        if (!accept) {
            throw new IllegalArgumentException();
        }
    }
}
