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
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
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
    private String excludePattern;
    private String exposureLevel = "default";
    private HealthCheck.State initialState = HealthCheck.State.DOWN;
    private volatile boolean loadHealthChecksDone;

    public DefaultHealthCheckRegistry() {
        this(null);
    }

    public DefaultHealthCheckRegistry(CamelContext camelContext) {
        this.checks = new CopyOnWriteArraySet<>();
        this.repositories = new CopyOnWriteArraySet<>();

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
    public String getExcludePattern() {
        return excludePattern;
    }

    @Override
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    @Override
    public String getExposureLevel() {
        return exposureLevel;
    }

    @Override
    public void setExposureLevel(String exposureLevel) {
        this.exposureLevel = exposureLevel;
    }

    public HealthCheck.State getInitialState() {
        return initialState;
    }

    public void setInitialState(HealthCheck.State initialState) {
        this.initialState = initialState;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Optional<HealthCheckRepository> hcr = repositories.stream()
                .filter(repository -> repository instanceof HealthCheckRegistryRepository)
                .findFirst();

        if (hcr.isEmpty()) {
            register(new HealthCheckRegistryRepository());
        }

        for (HealthCheck check : checks) {
            CamelContextAware.trySetCamelContext(check, camelContext);
        }

        for (HealthCheckRepository repository : repositories) {
            CamelContextAware.trySetCamelContext(repository, camelContext);
        }

        ServiceHelper.initService(repositories, checks);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(repositories, checks);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(repositories, checks);
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

    private HealthCheck resolveHealthCheckById(String id) {
        HealthCheck answer = checks.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheck.class).get(id));
        if (answer == null) {
            HealthCheckResolver resolver = PluginHelper.getHealthCheckResolver(camelContext);
            answer = resolver.resolveHealthCheck(id);
        }

        return answer;
    }

    private HealthCheckRepository resolveHealthCheckRepositoryById(String id) {
        HealthCheckRepository answer = repositories.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(HealthCheckRepository.class).get(id));
        if (answer == null) {
            // discover via classpath (try first via -health-check-repository and then id as-is)
            HealthCheckResolver resolver = PluginHelper.getHealthCheckResolver(camelContext);
            answer = resolver.resolveHealthCheckRepository(id);
        }

        return answer;
    }

    @Override
    public boolean register(Object obj) {
        boolean result;

        checkIfAccepted(obj);

        // inject context
        CamelContextAware.trySetCamelContext(obj, camelContext);

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

        // ensure the check is started if we are already started (such as added later)
        if (isStarted()) {
            ServiceHelper.startService(obj);
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

        if (result) {
            ServiceHelper.stopService(obj);
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
            if (!col.isEmpty()) {
                String time = TimeUtils.printDuration(watch.taken(), true);
                LOG.debug("Health checks (scanned: {}) loaded in {}", col.size(), time);
            }
        }
    }

    @Override
    public boolean isExcluded(HealthCheck healthCheck) {
        if (excludePattern != null) {
            String[] s = excludePattern.split(",");

            String id = healthCheck.getId();
            if (PatternHelper.matchPatterns(id, s)) {
                return true;
            }
            // special for route, consumer and producer health checks
            if (id.startsWith("route:")) {
                id = id.substring(6);
                return PatternHelper.matchPatterns(id, s);
            } else if (id.startsWith("consumer:")) {
                id = id.substring(9);
                return PatternHelper.matchPatterns(id, s);
            } else if (id.startsWith("producer:")) {
                id = id.substring(9);
                return PatternHelper.matchPatterns(id, s);
            }
        }

        return false;
    }

    private void checkIfAccepted(Object obj) {
        boolean accept = obj instanceof HealthCheck || obj instanceof HealthCheckRepository;
        if (!accept) {
            throw new IllegalArgumentException();
        }
    }
}
