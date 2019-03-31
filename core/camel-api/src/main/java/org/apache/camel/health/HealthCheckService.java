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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;

/**
 * An health check service that invokes the checks registered on the {@link HealthCheckRegistry}
 * according to a schedule.
 */
public interface HealthCheckService extends Service, CamelContextAware {
    /**
     * Add a listener to invoke when the state of a check change.
     *
     * @param consumer the event listener.
     */
    void addStateChangeListener(BiConsumer<HealthCheck.State, HealthCheck> consumer);

    /**
     * Remove the state change listener.
     *
     * @param consumer the event listener to remove.
     */
    void removeStateChangeListener(BiConsumer<HealthCheck.State, HealthCheck> consumer);

    /**
     * Sets the options to be used when invoking the check identified by the
     * given id.
     *
     * @param id the health check id.
     * @param options the health check options.
     */
    void setHealthCheckOptions(String id, Map<String, Object> options);

    /**
     * @see {@link #call(String, Map)}
     *
     * @param id the health check id.
     * @return the result of the check or {@link Optional#empty()} if the id is unknown.
     */
    default Optional<HealthCheck.Result> call(String id) {
        return call(id, Collections.emptyMap());
    }

    /**
     * Invokes the check identified by the given <code>id</code> with the given
     * <code>options</code>.
     *
     * @param id the health check id.
     * @param options the health check options.
     * @return the result of the check or {@link Optional#empty()} if the id is unknown.
     */
    Optional<HealthCheck.Result> call(String id, Map<String, Object> options);

    /**
     * Notify the service that a check has changed status. This may be useful for
     * stateful checks like checks rely on tcp/ip connections.
     *
     * @param check the health check.
     * @param result the health check result.
     */
    void notify(HealthCheck check, HealthCheck.Result result);

    /**
     * Return a list of the known checks status.
     *
     * @return the list of results.
     */
    Collection<HealthCheck.Result> getResults();

    /**
     * Access the underlying concrete HealthCheckService implementation to
     * provide access to further features.
     *
     * @param clazz the proprietary class or interface of the underlying concrete HealthCheckService.
     * @return an instance of the underlying concrete HealthCheckService as the required type.
     */
    default <T extends HealthCheckService> T unwrap(Class<T> clazz) {
        if (HealthCheckService.class.isAssignableFrom(clazz)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException(
            "Unable to unwrap this HealthCheckService type (" + getClass() + ") to the required type (" + clazz + ")"
        );
    }
}
