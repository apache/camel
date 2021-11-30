package org.apache.camel.health;

import org.apache.camel.CamelContext;

/**
 * A pluggable strategy for resolving health checks in a loosely coupled manner
 */
public interface HealthCheckResolver {

    /**
     * Resolves the given {@link HealthCheck}.
     *
     * @param  id      the id of the {@link HealthCheck}
     * @param  context the camel context
     * @return         the resolved {@link HealthCheck}, or <tt>null</tt> if not found
     */
    HealthCheck resolveHealthCheck(String id, CamelContext context);

    /**
     * Resolves the given {@link HealthCheckRepository}.
     *
     * @param  id      the id of the {@link HealthCheckRepository}
     * @param  context the camel context
     * @return         the resolved {@link HealthCheckRepository}, or <tt>null</tt> if not found
     */
    HealthCheckRepository resolveHealthCheckRepository(String id, CamelContext context);

}
