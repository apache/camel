package org.apache.camel.health;

import org.apache.camel.CamelContextAware;

/**
 * A pluggable strategy for resolving health checks in a loosely coupled manner
 */
public interface HealthCheckResolver extends CamelContextAware {

    /**
     * Resolves the given {@link HealthCheck}.
     *
     * @param  id the id of the {@link HealthCheck}
     * @return    the resolved {@link HealthCheck}, or <tt>null</tt> if not found
     */
    HealthCheck resolveHealthCheck(String id);

    /**
     * Resolves the given {@link HealthCheckRepository}.
     *
     * @param  id the id of the {@link HealthCheckRepository}
     * @return    the resolved {@link HealthCheckRepository}, or <tt>null</tt> if not found
     */
    HealthCheckRepository resolveHealthCheckRepository(String id);

}
