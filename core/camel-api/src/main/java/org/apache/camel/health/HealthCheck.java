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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Ordered;
import org.apache.camel.spi.HasGroup;
import org.apache.camel.spi.HasId;

/**
 * Health check
 */
public interface HealthCheck extends HasGroup, HasId, Ordered {

    String CHECK_ID = "check.id";
    String CHECK_GROUP = "check.group";
    String CHECK_KIND = "check.kind";
    String CHECK_ENABLED = "check.enabled";
    String INVOCATION_COUNT = "invocation.count";
    String INVOCATION_TIME = "invocation.time";
    String FAILURE_COUNT = "failure.count";
    String FAILURE_START_TIME = "failure.start.time";
    String FAILURE_TIME = "failure.time";
    String FAILURE_ERROR_COUNT = "failure.error.count";
    String ENDPOINT_URI = "endpoint.uri";
    String SUCCESS_COUNT = "success.count";
    String SUCCESS_START_TIME = "success.start.time";
    String SUCCESS_TIME = "success.time";
    String HTTP_RESPONSE_CODE = "http.response.code";

    enum State {
        UP,
        DOWN,
        UNKNOWN
    }

    enum Kind {
        READINESS,
        LIVENESS,
        ALL,
    }

    @Override
    default int getOrder() {
        return Ordered.LOWEST;
    }

    /**
     * Whether this health check is enabled
     */
    boolean isEnabled();

    /**
     * Used for enabling or disabling this health check
     */
    void setEnabled(boolean enabled);

    /**
     * Return metadata associated with this {@link HealthCheck}.
     */
    default Map<String, Object> getMetaData() {
        return Collections.emptyMap();
    }

    /**
     * Whether this health check can be used for readiness checks.
     *
     * Readiness check is default.
     */
    default boolean isReadiness() {
        return true;
    }

    /**
     * Whether this health check can be used for liveness checks
     *
     * Liveness check is not default, and must be explicit enabled.
     */
    default boolean isLiveness() {
        return false;
    }

    /**
     * Invoke the check.
     *
     * @see #call(Map)
     */
    default Result call() {
        return call(Collections.emptyMap());
    }

    /**
     * Invoke the check as readiness check.
     *
     * @see #call(Map)
     */
    default Result callReadiness() {
        return call(Map.of(HealthCheck.CHECK_KIND, Kind.READINESS));
    }

    /**
     * Invoke the check as liveness check.
     *
     * @see #call(Map)
     */
    default Result callLiveness() {
        return call(Map.of(HealthCheck.CHECK_KIND, Kind.LIVENESS));
    }

    /**
     * Invoke the check.
     *
     * The implementation is responsible to eventually perform the check according to the limitation of the third party
     * system i.e. it should not be performed too often to avoid rate limiting. The options argument can be used to pass
     * information specific to the check like forcing the check to be performed against the policies. The implementation
     * is responsible to catch and handle any exception thrown by the underlying technology, including unchecked ones.
     */
    Result call(Map<String, Object> options);

    /**
     * Response to a health check invocation.
     */
    interface Result {

        /**
         * The {@link HealthCheck} associated to this response.
         */
        HealthCheck getCheck();

        /**
         * The state of the service.
         */
        State getState();

        /**
         * A message associated to the result, used to provide more information for unhealthy services.
         */
        Optional<String> getMessage();

        /**
         * An error associated to the result, used to provide the error associated to unhealthy services.
         */
        Optional<Throwable> getError();

        /**
         * A key/value combination of details.
         *
         * @return a non null details map (empty if no details)
         */
        Map<String, Object> getDetails();
    }
}
