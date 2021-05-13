package org.apache.camel.component.jackson;

import com.fasterxml.jackson.core.FormatSchema;
import org.apache.camel.Exchange;

/**
 * Interface for resolving schemas using pluggable strategies.
 */
@FunctionalInterface
public interface SchemaResolver {

    /**
     * Resolves a schema for the given exchange.
     *
     * @param  exchange the exchange for which the schema should be resolved
     * @return          the resolved format or null if no format is found
     */
    FormatSchema resolve(Exchange exchange);

}
