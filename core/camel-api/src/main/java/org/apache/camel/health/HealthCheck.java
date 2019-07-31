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

public interface HealthCheck extends HasGroup, HasId, Ordered {
    enum State {
        UP,
        DOWN,
        UNKNOWN
    }

    @Override
    default int getOrder() {
        return Ordered.LOWEST;
    }

    /**
     * Return meta data associated with this {@link HealthCheck}.
     */
    default Map<String, Object> getMetaData() {
        return Collections.emptyMap();
    }

    /**
     * Return the configuration associated with this {@link HealthCheck}.
     */
    HealthCheckConfiguration getConfiguration();

    /**
     * Invoke the check.
     *
     * @see {@link #call(Map)}
     */
    default Result call() {
        return call(Collections.emptyMap());
    }

    /**
     * Invoke the check. The implementation is responsible to eventually perform
     * the check according to the limitation of the third party system i.e.
     * it should not be performed too often to avoid rate limiting. The options
     * argument can be used to pass information specific to the check like
     * forcing the check to be performed against the policies. The implementation
     * is responsible to catch an handle any exception thrown by the underlying
     * technology, including unchecked ones.
     */
    Result call(Map<String, Object> options);

    /**
     * Response to an health check invocation.
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
         * A message associated to the result, used to provide more information
         * for unhealthy services.
         */
        Optional<String> getMessage();

        /**
         * An error associated to the result, used to provide the error associated
         * to unhealthy services.
         */
        Optional<Throwable> getError();

        /**
         * An key/value combination of details.
         *
         * @return a non null details map
         */
        Map<String, Object> getDetails();
    }
}
