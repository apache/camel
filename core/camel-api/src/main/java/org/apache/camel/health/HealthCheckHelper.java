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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper for invoking {@link HealthCheck}'s.
 *
 * The helper will look up the {@link HealthCheckRegistry} from {@link CamelContext} and gather all the registered
 * {@link HealthCheck}s and invoke them and gather their results.
 *
 * The helper allows filtering out unwanted health checks using {@link Predicate<HealthCheck>} or to invoke only
 * readiness or liveness checks.
 */
public final class HealthCheckHelper {

    private HealthCheckHelper() {
    }

    /**
     * Invokes all the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(CamelContext camelContext) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.ALL), check -> false, null);
    }

    /**
     * Invokes all the checks and returns a collection of results.
     *
     * @param camelContext  the camel context
     * @param exposureLevel level of exposure (full, oneline or default)
     */
    public static Collection<HealthCheck.Result> invoke(CamelContext camelContext, String exposureLevel) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.ALL), check -> false,
                exposureLevel);
    }

    /**
     * Invokes the readiness checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invokeReadiness(CamelContext camelContext) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS),
                check -> !check.isReadiness(), null);
    }

    /**
     * Invokes the readiness checks and returns a collection of results.
     *
     * @param camelContext  the camel context
     * @param exposureLevel level of exposure (full, oneline or default)
     */
    public static Collection<HealthCheck.Result> invokeReadiness(CamelContext camelContext, String exposureLevel) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS),
                check -> !check.isReadiness(), exposureLevel);
    }

    /**
     * Invokes the liveness checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invokeLiveness(CamelContext camelContext) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.LIVENESS),
                check -> !check.isLiveness(), null);
    }

    /**
     * Invokes the liveness checks and returns a collection of results.
     *
     * @param camelContext  the camel context
     * @param exposureLevel level of exposure (full, oneline or default)
     */
    public static Collection<HealthCheck.Result> invokeLiveness(CamelContext camelContext, String exposureLevel) {
        return invoke(camelContext, check -> Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.LIVENESS),
                check -> !check.isLiveness(), exposureLevel);
    }

    /**
     * Invokes the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            Function<HealthCheck, Map<String, Object>> optionsSupplier) {

        return invoke(camelContext, optionsSupplier, check -> false, null);
    }

    /**
     * Invokes the checks and returns a collection of results.
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            Predicate<HealthCheck> filter) {

        return invoke(camelContext, check -> Collections.emptyMap(), filter, null);
    }

    /**
     * Invokes the checks and returns a collection of results.
     *
     * @param camelContext    the camel context.
     * @param optionsSupplier a supplier for options.
     * @param filter          filter to exclude some checks.
     * @param exposureLevel   full or oneline (null to use default)
     */
    public static Collection<HealthCheck.Result> invoke(
            CamelContext camelContext,
            Function<HealthCheck, Map<String, Object>> optionsSupplier,
            Predicate<HealthCheck> filter,
            String exposureLevel) {

        final HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);

        if (registry != null) {
            Collection<HealthCheck.Result> result = registry.stream()
                    .collect(Collectors.groupingBy(HealthCheckHelper::getGroup))
                    .values().stream()
                    .flatMap(Collection::stream)
                    .filter(check -> !registry.isExcluded(check) && !filter.test(check))
                    .sorted(Comparator.comparingInt(HealthCheck::getOrder))
                    .distinct()
                    .map(check -> check.call(optionsSupplier.apply(check)))
                    .toList();

            if (result.isEmpty()) {
                return Collections.emptyList();
            }

            if (exposureLevel == null) {
                exposureLevel = registry.getExposureLevel();
            }

            // the result includes all the details
            if ("full".equals(exposureLevel)) {
                return result;
            } else {
                // are there any downs?
                Collection<HealthCheck.Result> downs = result.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN))
                        .collect(Collectors.toCollection(ArrayList::new));

                // default mode is to either be just UP or include all DOWNs
                // oneline mode is either UP or DOWN
                if (!downs.isEmpty()) {
                    if ("oneline".equals(exposureLevel)) {
                        // grab first down
                        return Collections.singleton(downs.iterator().next());
                    } else {
                        return downs;
                    }
                } else {
                    // all up so grab first
                    HealthCheck.Result up = result.iterator().next();
                    return Collections.singleton(up);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Invoke a check by id.
     *
     * @param  camelContext the camel context.
     * @param  id           the check id.
     * @param  options      the check options.
     * @return              an optional {@link HealthCheck.Result}.
     */
    public static Optional<HealthCheck.Result> invoke(CamelContext camelContext, String id, Map<String, Object> options) {
        final HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);

        if (registry != null) {
            return registry.getCheck(id).map(check -> check.call(options));
        }

        return Optional.empty();
    }

    /**
     * Gets the {@link HealthCheckRegistry}.
     *
     * @param  context the camel context
     * @return         the health check registry, or <tt>null</tt> if health-check is not enabled.
     */
    public static HealthCheckRegistry getHealthCheckRegistry(CamelContext context) {
        return context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
    }

    /**
     * Gets the {@link HealthCheck} by the given id (will resolve from classpath if necessary)
     *
     * @param  context the camel context
     * @param  id      the id of the health check
     * @return         the health check, or <tt>null</tt> if no health check exists with this id
     */
    public static HealthCheck getHealthCheck(CamelContext context, String id) {
        HealthCheck answer = null;

        HealthCheckRegistry hcr = context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        if (hcr != null && hcr.isEnabled()) {
            Optional<HealthCheck> check = hcr.getCheck(id);
            if (check.isEmpty()) {
                // use resolver to load from classpath if needed
                HealthCheckResolver resolver
                        = context.getCamelContextExtension().getContextPlugin(HealthCheckResolver.class);
                HealthCheck hc = resolver.resolveHealthCheck(id);
                if (hc != null) {
                    check = Optional.of(hc);
                    hcr.register(hc);
                }
            }
            if (check.isPresent()) {
                answer = check.get();
            }
        }
        return answer;
    }

    /**
     * Gets the {@link HealthCheck} by the given id (will resolve from classpath if necessary)
     *
     * @param  context the camel context
     * @param  id      the id of the health check
     * @param  type    the expected type of the health check repository
     * @return         the health check, or <tt>null</tt> if no health check exists with this id
     */
    public static <T extends HealthCheck> T getHealthCheck(CamelContext context, String id, Class<T> type) {
        HealthCheck answer = getHealthCheck(context, id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    /**
     * Gets the {@link HealthCheckRepository} by the given id (will resolve from classpath if necessary)
     *
     * @param  context the camel context
     * @param  id      the id of the health check repository
     * @return         the health check repository, or <tt>null</tt> if no health check repository exists with this id
     */
    public static HealthCheckRepository getHealthCheckRepository(CamelContext context, String id) {
        HealthCheckRepository answer = null;

        HealthCheckRegistry hcr = context.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        if (hcr != null && hcr.isEnabled()) {
            Optional<HealthCheckRepository> repo = hcr.getRepository(id);
            if (repo.isEmpty()) {
                // use resolver to load from classpath if needed
                HealthCheckResolver resolver
                        = context.getCamelContextExtension().getContextPlugin(HealthCheckResolver.class);
                HealthCheckRepository hr = resolver.resolveHealthCheckRepository(id);
                if (hr != null) {
                    repo = Optional.of(hr);
                    hcr.register(hr);
                }
            }
            if (repo.isPresent()) {
                answer = repo.get();
            }
        }
        return answer;
    }

    /**
     * Gets the {@link HealthCheckRepository} by the given id (will resolve from classpath if necessary)
     *
     * @param  context the camel context
     * @param  id      the id of the health check repository
     * @param  type    the expected type of the health check repository
     * @return         the health check repository, or <tt>null</tt> if no health check repository exists with this id
     */
    public static <T extends HealthCheckRepository> T getHealthCheckRepository(CamelContext context, String id, Class<T> type) {
        HealthCheckRepository answer = getHealthCheckRepository(context, id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    /**
     * Checks the overall status of the results.
     *
     * @param  results   the results from the invoked health checks
     * @param  readiness readiness or liveness mode
     * @return           true if up, or false if down
     */
    public static boolean isResultsUp(Collection<HealthCheck.Result> results, boolean readiness) {
        boolean up;
        if (readiness) {
            // readiness requires that all are UP
            up = results.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
        } else {
            // liveness will fail if there is any down
            up = results.stream().noneMatch(r -> r.getState().equals(HealthCheck.State.DOWN));
        }
        return up;
    }

    /**
     * Get the group of the given check or an empty string if the group is not set.
     *
     * @param  check the health check
     * @return       the {@link HealthCheck#getGroup()} or an empty string if it is <code>null</code>
     */
    private static String getGroup(HealthCheck check) {
        return ObjectHelper.supplyIfEmpty(check.getGroup(), () -> "");
    }

    /**
     * Is the given key a reserved key used by Camel to store metadata in health check response details.
     *
     * @param  key the key
     * @return     true if reserved, false otherwise
     */
    public static boolean isReservedKey(String key) {
        if (key == null) {
            return false;
        }

        if (HealthCheck.CHECK_ID.equals(key)) {
            return true;
        } else if (HealthCheck.CHECK_GROUP.equals(key)) {
            return true;
        } else if (HealthCheck.CHECK_KIND.equals(key)) {
            return true;
        } else if (HealthCheck.CHECK_ENABLED.equals(key)) {
            return true;
        } else if (HealthCheck.INVOCATION_COUNT.equals(key)) {
            return true;
        } else if (HealthCheck.INVOCATION_TIME.equals(key)) {
            return true;
        } else if (HealthCheck.FAILURE_COUNT.equals(key)) {
            return true;
        } else if (HealthCheck.FAILURE_START_TIME.equals(key)) {
            return true;
        } else if (HealthCheck.FAILURE_TIME.equals(key)) {
            return true;
        } else if (HealthCheck.FAILURE_ERROR_COUNT.equals(key)) {
            return true;
        } else if (HealthCheck.SUCCESS_COUNT.equals(key)) {
            return true;
        } else if (HealthCheck.SUCCESS_START_TIME.equals(key)) {
            return true;
        } else if (HealthCheck.SUCCESS_TIME.equals(key)) {
            return true;
        }

        return false;
    }
}
