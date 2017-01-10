package org.apache.camel.cdi;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.transaction.JavaEETransactionErrorHandlerBuilder;
import org.apache.camel.model.ModelCamelContext;

/**
 * An extension of the {@link RouteBuilder} to provide some additional helper
 * methods
 *
 * @version
 */
public abstract class JavaEERouteBuilder extends RouteBuilder {

    /**
     * Creates a transaction error handler that will lookup in application
     * context for an exiting transaction manager.
     *
     * @return the created error handler
     */
    public JavaEETransactionErrorHandlerBuilder transactionErrorHandler() {
        return new JavaEETransactionErrorHandlerBuilder(false);
    }

    public JavaEERouteBuilder rollbackLoggingLevel(final LoggingLevel loggingLevel) {

        final ModelCamelContext context = this.getContext();

        Map<String, String> properties = context.getProperties();
        if (properties == null) {
            properties = new HashMap<>();
            properties.put(JavaEETransactionErrorHandlerBuilder.ROLLBACK_LOGGING_LEVEL_PROPERTY, loggingLevel.name());
            context.setProperties(properties);
        } else {
            properties.put(JavaEETransactionErrorHandlerBuilder.ROLLBACK_LOGGING_LEVEL_PROPERTY, loggingLevel.name());
        }

        return this;

    }

}
