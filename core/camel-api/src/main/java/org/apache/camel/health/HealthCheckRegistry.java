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
package org.apache.camel.health;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.ObjectHelper;

/**
 * A registry for health checks.
 */
public interface HealthCheckRegistry extends CamelContextAware, StaticService, IdAware {

    /**
     * Service factory name.
     */
    String NAME = "default-registry";

    /**
     * Service factory key.
     */
    String FACTORY = "health-check/" + NAME;

    /**
     * Whether Health Check is enabled globally
     */
    boolean isEnabled();

    /**
     * Whether Health Check is enabled globally
     */
    void setEnabled(boolean enabled);

    /**
     * Resolves {@link HealthCheck} or {@link HealthCheckRepository} by id.
     *
     * Will first lookup in this {@link HealthCheckRegistry} and then {@link org.apache.camel.spi.Registry}, and lastly
     * do classpath scanning via {@link org.apache.camel.spi.annotations.ServiceFactory}. The classpath scanning is
     * attempted first with id-health-check or id-health-check-repository as the key, and then with id as fallback if
     * not found the first time.
     *
     * @return either {@link HealthCheck} or {@link HealthCheckRepository}, or <tt>null</tt> if none found.
     */
    Object resolveById(String id);

    /**
     * Registers a {@link HealthCheck} or {@link HealthCheckRepository}.
     */
    boolean register(Object obj);

    /**
     * Unregisters a {@link HealthCheck} or {@link HealthCheckRepository}.
     */
    boolean unregister(Object obj);

    /**
     * A collection of health check IDs.
     */
    default Collection<String> getCheckIDs() {
        return stream()
                .map(HealthCheck::getId)
                .toList();
    }

    /**
     * Returns the check identified by the given <code>id</code> if available.
     */
    default Optional<HealthCheck> getCheck(String id) {
        return stream()
                .filter(r -> ObjectHelper.equal(r.getId(), id)
                        || ObjectHelper.equal(r.getId().replace("-health-check", ""), id)
                        || ObjectHelper.equal(r.getId().replace("route:", ""), id))
                .findFirst();
    }

    /**
     * Returns the repository identified by the given <code>id</code> if available.
     */
    Optional<HealthCheckRepository> getRepository(String id);

    /**
     * Returns an optional {@link HealthCheckRegistry}, by default no registry is present, and it must be explicit
     * activated. Components can register/unregister health checks in response to life-cycle events (i.e. start/stop).
     *
     * This registry is not used by the camel context, but it is up to the implementation to properly use it, such as: -
     * a RouteController could use the registry to decide to restart a route with failing health checks - spring boot
     * could integrate such checks within its health endpoint or make it available only as separate endpoint.
     */
    static HealthCheckRegistry get(CamelContext context) {
        return context != null ? context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class) : null;
    }

    /**
     * Returns a sequential {@code Stream} with the known {@link HealthCheck} as its source.
     */
    Stream<HealthCheck> stream();

    /**
     * Loads custom health checks by scanning classpath.
     */
    void loadHealthChecks();

    /**
     * Pattern to exclude health checks from being invoked by Camel when checking healths. Multiple patterns can be
     * separated by comma.
     */
    String getExcludePattern();

    /**
     * Pattern to exclude health checks from being invoked by Camel when checking healths. Multiple patterns can be
     * separated by comma.
     */
    void setExcludePattern(String excludePattern);

    /**
     * Whether the given health check has been excluded
     */
    boolean isExcluded(HealthCheck healthCheck);

    /**
     * Sets the level of details to exposure as result of invoking health checks. There are the following levels: full,
     * default, oneline
     *
     * The full level will include all details and status from all the invoked health checks.
     *
     * The default level will report UP if everything is okay, and only include detailed information for health checks
     * that was DOWN.
     *
     * The oneline level will only report either UP or DOWN.
     */
    void setExposureLevel(String exposureLevel);

    /**
     * The exposure level
     */
    String getExposureLevel();

    /**
     * The initial state of health-checks (readiness). There are the following states: UP, DOWN, UNKNOWN.
     *
     * By default, the state is DOWN, is regarded as being pessimistic/careful. This means that the overall health
     * checks may report as DOWN during startup and then only if everything is up and running flip to being UP.
     *
     * Setting the initial state to UP, is regarded as being optimistic. This means that the overall health checks may
     * report as UP during startup and then if a consumer or other service is in fact un-healthy, then the health-checks
     * can flip being DOWN.
     *
     * Setting the state to UNKNOWN means that some health-check would be reported in unknown state, especially during
     * early bootstrap where a consumer may not be fully initialized or validated a connection to a remote system.
     *
     * This option allows to pre-configure the state for different modes.
     */
    void setInitialState(HealthCheck.State initialState);

    /**
     * The initial state of health-checks.
     */
    HealthCheck.State getInitialState();

}
