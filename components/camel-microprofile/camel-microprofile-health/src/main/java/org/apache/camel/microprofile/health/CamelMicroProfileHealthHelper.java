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
package org.apache.camel.microprofile.health;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import io.smallrye.health.api.HealthRegistry;
import io.smallrye.health.registry.LivenessHealthRegistry;
import io.smallrye.health.registry.ReadinessHealthRegistry;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheck.Result;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

/**
 * Helper utility class for MicroProfile health checks.
 */
final class CamelMicroProfileHealthHelper {

    private CamelMicroProfileHealthHelper() {
        // Utility class
    }

    /**
     * Propagates details from the Camel Health {@link Result} to the MicroProfile {@link HealthCheckResponseBuilder}.
     *
     * @param builder The health check response builder
     * @param result  The Camel health check result
     */
    public static void applyHealthDetail(HealthCheckResponseBuilder builder, Result result) {
        HealthCheck check = result.getCheck();
        Set<String> metaKeys = check.getMetaData().keySet();

        result.getDetails().forEach((key, value) -> {
            // Filter health check metadata to have a less verbose output
            if (!metaKeys.contains(key)) {
                builder.withData(key, value.toString());
            }
        });

        result.getError().ifPresent(error -> {
            builder.withData("error.message", error.getMessage());

            final StringWriter stackTraceWriter = new StringWriter();
            try (final PrintWriter pw = new PrintWriter(stackTraceWriter, true)) {
                error.printStackTrace(pw);
                builder.withData("error.stacktrace", stackTraceWriter.toString());
            }
        });
    }

    /**
     * Retrieves the {@link LivenessHealthRegistry} bean instance.
     * 
     * @return The {@link LivenessHealthRegistry} bean.
     */
    public static HealthRegistry getLivenessRegistry() {
        return getHealthRegistryBean(LivenessHealthRegistry.class, Liveness.Literal.INSTANCE);
    }

    /**
     * Retrieves the {@link ReadinessHealthRegistry} bean instance.
     * 
     * @return The {@link ReadinessHealthRegistry} bean.
     */
    public static HealthRegistry getReadinessRegistry() {
        return getHealthRegistryBean(ReadinessHealthRegistry.class, Readiness.Literal.INSTANCE);
    }

    /**
     * Retrieves a {@link HealthRegistry} bean from the CDI bean manager for the given type and qualifier.
     *
     * Registry beans are looked up from the CDI {@link BeanManager} to avoid CDI injection in
     * {@link CamelMicroProfileHealthCheckRegistry} and also avoid having to add CDI bean defining annotations to
     * {@link CamelMicroProfileHealthCheckRegistry}.
     *
     * Eventually this can be removed when upgrading to a future SmallRye Health release where static health registry
     * lookups will be supported.
     *
     * https://github.com/smallrye/smallrye-health/issues/172
     *
     * @param  type                  The implementation class of the {@link HealthRegistry} bean
     * @param  qualifier             The annotation qualifier applied to the {@link HealthRegistry} bean
     * @return                       The {@link HealthRegistry} bean
     * @throws IllegalStateException if no beans matching the {@link HealthRegistry} bean type and annotation qualifier
     *                               were found
     */
    private static HealthRegistry getHealthRegistryBean(Class<? extends HealthRegistry> type, Annotation qualifier) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(type, qualifier);
        if (beans.isEmpty()) {
            throw new IllegalStateException(
                    "Beans for type " + type.getName() + " with qualifier " + qualifier + " could not be found.");
        }

        Bean<?> bean = beanManager.resolve(beans);
        Object reference = beanManager.getReference(bean, type, beanManager.createCreationalContext(bean));
        return type.cast(reference);
    }
}
