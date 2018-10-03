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
package org.apache.camel.health;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.CamelContextAware;
import org.apache.camel.util.ObjectHelper;

/**
 * A registry for health checks.
 * <p>
 * Note that this registry can be superseded by the future camel context internal
 * registry, @see <a href="https://issues.apache.org/jira/browse/CAMEL-10792"/>.
 */
public interface HealthCheckRegistry extends HealthCheckRepository, CamelContextAware {
    /**
     * Registers a service {@link HealthCheck}.
     */
    boolean register(HealthCheck check);

    /**
     * Unregisters a service {@link HealthCheck}.
     */
    boolean unregister(HealthCheck check);

    /**
     * Set the health check repositories to use..
     */
    void setRepositories(Collection<HealthCheckRepository> repositories);

    /**
     * Get a collection of health check repositories.
     */
    Collection<HealthCheckRepository> getRepositories();

    /**
     * Add an Health Check repository.
     */
    boolean addRepository(HealthCheckRepository repository);

    /**
     * Remove an Health Check repository.
     */
    boolean removeRepository(HealthCheckRepository repository);

    /**
     * A collection of health check IDs.
     */
    default Collection<String> getCheckIDs() {
        return stream()
            .map(HealthCheck::getId)
            .collect(Collectors.toList());
    }

    /**
     * Returns the check identified by the given <code>id</code> if available.
     */
    default Optional<HealthCheck> getCheck(String id) {
        return stream()
            .filter(check -> ObjectHelper.equal(check.getId(), id))
            .findFirst();
    }
}
