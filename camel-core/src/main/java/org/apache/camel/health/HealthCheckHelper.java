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
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HealthCheckHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckHelper.class);

    private HealthCheckHelper() {
    }

    /**
     * Get the group of the given check or an empty string if the group is not set.
     *
     * @param check the health check
     * @return the {@link HealthCheck#getGroup()} or an empty string if it is <code>null</code>
     */
    public static String getGroup(HealthCheck check) {
        return ObjectHelper.supplyIfEmpty(check.getGroup(), () -> "");
    }

    /**
     * Invokes the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(CamelContext camelContext) {
        return invoke(camelContext, check -> Collections.emptyMap(), check -> false);
    }

    /**
     * Invokes the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            Function<HealthCheck, Map<String, Object>> optionsSupplier) {

        return invoke(camelContext, optionsSupplier, check -> false);
    }

    /**
     * Invokes the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            HealthCheckFilter filter) {

        return invoke(camelContext, check -> Collections.emptyMap(), filter);
    }

    /**
     * Invokes the checks and returns a collection of results.
     *
     * @param camelContext the camel context.
     * @param optionsSupplier a supplier for options.
     * @param filter filter to exclude some checks.
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            Function<HealthCheck, Map<String, Object>> optionsSupplier,
            HealthCheckFilter filter) {

        final HealthCheckRegistry registry = camelContext.getHealthCheckRegistry();
        final HealthCheckService service = camelContext.hasService(HealthCheckService.class);

        if (service != null) {
            // If a health check service is defined retrieve the current status
            // of the checks hold by the service.
            return service.getResults().stream()
                .filter(result -> !filter.test(result.getCheck()))
                .collect(Collectors.toList());
        } else if (registry != null) {
            // If no health check service is defined, this endpoint invokes the
            // check one by one.
            return registry.stream()
                .collect(Collectors.groupingBy(HealthCheckHelper::getGroup))
                .entrySet().stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(check -> !filter.test(check))
                    .sorted(Comparator.comparingInt(HealthCheck::getOrder))
                    .map(check -> check.call(optionsSupplier.apply(check)))
                    .collect(Collectors.toList());
        } else {
            LOGGER.debug("No health check source found");
        }

        return Collections.emptyList();
    }

    /**
     * Query the status of a check by id. Note that this may result in an effective
     * invocation of the {@link HealthCheck}, i.e. when no {@link HealthCheckService}
     * is available.
     *
     * @param camelContext the camel context.
     * @param id the check id.
     * @param options the check options.
     * @return an optional {@link HealthCheck.Result}.
     */
    public static Optional<HealthCheck.Result> query(CamelContext camelContext, String id, Map<String, Object> options) {
        final HealthCheckRegistry registry = camelContext.getHealthCheckRegistry();
        final HealthCheckService service = camelContext.hasService(HealthCheckService.class);

        if (service != null) {
            return service.getResults().stream()
                .filter(result -> ObjectHelper.equal(result.getCheck().getId(), id))
                .findFirst();
        } else if (registry != null) {
            return registry.getCheck(id).map(check -> check.call(options));
        } else {
            LOGGER.debug("No health check source found");
        }

        return Optional.empty();
    }

    /**
     * Invoke a check by id.
     *
     * @param camelContext the camel context.
     * @param id the check id.
     * @param options the check options.
     * @return an optional {@link HealthCheck.Result}.
     */
    public static Optional<HealthCheck.Result> invoke(CamelContext camelContext, String id, Map<String, Object> options) {
        final HealthCheckRegistry registry = camelContext.getHealthCheckRegistry();
        final HealthCheckService service = camelContext.hasService(HealthCheckService.class);

        if (service != null) {
            return service.call(id, options);
        } else if (registry != null) {
            return registry.getCheck(id).map(check -> check.call(options));
        } else {
            LOGGER.debug("No health check source found");
        }

        return Optional.empty();
    }
}
