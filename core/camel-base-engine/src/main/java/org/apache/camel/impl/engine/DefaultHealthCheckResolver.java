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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.FactoryFinder;

/**
 * Default health check resolver that looks for health checks factories in
 * <b>META-INF/services/org/apache/camel/health-check/</b>.
 */
public class DefaultHealthCheckResolver implements HealthCheckResolver, CamelContextAware {

    public static final String HEALTH_CHECK_RESOURCE_PATH = "META-INF/services/org/apache/camel/health-check/";

    protected FactoryFinder healthCheckFactory;
    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public HealthCheck resolveHealthCheck(String id) {
        // lookup in registry first
        HealthCheck answer = camelContext.getRegistry().lookupByNameAndType(id + "-health-check", HealthCheck.class);
        if (answer == null) {
            answer = camelContext.getRegistry().lookupByNameAndType(id, HealthCheck.class);
        }
        if (answer != null) {
            return answer;
        }

        Class<?> type = null;
        try {
            type = findHealthCheck(id, camelContext);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no HealthCheck registered for id: " + id, e);
        }

        if (type != null) {
            if (HealthCheck.class.isAssignableFrom(type)) {
                return (HealthCheck) camelContext.getInjector().newInstance(type, false);
            } else {
                throw new IllegalArgumentException(
                        "Resolving health-check: " + id + " detected type conflict: Not a HealthCheck implementation. Found: "
                                                   + type.getName());
            }
        }

        return null;
    }

    @Override
    public HealthCheckRepository resolveHealthCheckRepository(String id) {
        // lookup in registry first
        HealthCheckRepository answer
                = camelContext.getRegistry().lookupByNameAndType(id + "-health-check-repository", HealthCheckRepository.class);
        if (answer == null) {
            answer = camelContext.getRegistry().lookupByNameAndType(id, HealthCheckRepository.class);
        }
        if (answer != null) {
            return answer;
        }

        Class<?> type = null;
        try {
            type = findHealthCheckRepository(id, camelContext);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no HealthCheckRepository registered for id: " + id, e);
        }

        if (type != null) {
            if (HealthCheckRepository.class.isAssignableFrom(type)) {
                return (HealthCheckRepository) camelContext.getInjector().newInstance(type, false);
            } else {
                throw new IllegalArgumentException(
                        "Resolving health-check-repository: " + id
                                                   + " detected type conflict: Not a HealthCheckRepository implementation. Found: "
                                                   + type.getName());
            }
        }

        return null;
    }

    protected Class<?> findHealthCheck(String name, CamelContext context) throws Exception {
        if (healthCheckFactory == null) {
            healthCheckFactory = context.getCamelContextExtension().getFactoryFinder(HEALTH_CHECK_RESOURCE_PATH);
        }
        return healthCheckFactory.findOptionalClass(name + "-check").orElse(null);
    }

    protected Class<?> findHealthCheckRepository(String name, CamelContext context) throws Exception {
        if (healthCheckFactory == null) {
            healthCheckFactory = context.getCamelContextExtension().getFactoryFinder(HEALTH_CHECK_RESOURCE_PATH);
        }
        return healthCheckFactory.findOptionalClass(name + "-repository").orElse(null);
    }

}
